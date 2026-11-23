package com.termux.app.features.system.update;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.update.AppUpdateChecker;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The state of the update manager: the releases found on GitHub, the download of the APK of the
 * release for this device with its verification, and the install. It lives as long as the app
 * process, so a download continues while {@link UpdateManagerActivity} is closed and rotated.
 *
 * All methods must be called on the main thread, listeners are called on it.
 */
public final class UpdateManager {

    public enum State {
        IDLE,
        CHECKING,
        CHECK_FAILED,
        /** The releases are known and nothing is going on. */
        READY,
        DOWNLOADING,
        /** The APK is downloaded and verified. */
        DOWNLOADED,
        /** The APK is being handed to the system installer. */
        INSTALLING,
        /** The system asks the user to confirm the install, see {@link #consumeConfirmIntent()}. */
        WAITING_FOR_USER,
        INSTALL_FAILED
    }

    public enum ErrorKind {
        /** The list of releases or the APK could not be downloaded, {@link #getErrorDetail()} has the reason. */
        NETWORK,
        /** The release has no APK for this device. */
        NO_APK,
        /** The APK is not downloaded from the releases of this app. */
        UNTRUSTED_URL,
        /** The downloaded file has not the size the release lists. */
        SIZE_MISMATCH,
        /** The SHA-256 of the downloaded file is not the one of the release. */
        CHECKSUM_MISMATCH,
        STORAGE,
        /** The update is signed with another key than the installed app. */
        SIGNATURE_MISMATCH,
        BLOCKED,
        INVALID,
        INSTALL_FAILED
    }

    public interface Listener {
        @MainThread
        void onUpdateStateChanged();
    }

    private static final int TIMEOUT_MILLIS = 20000;
    private static final long PROGRESS_INTERVAL_MILLIS = 150;
    private static final String NOTIFICATION_CHANNEL_ID = "termux_system_update";
    private static final int NOTIFICATION_ID = 1348;

    private static final String LOG_TAG = "UpdateManager";

    private static UpdateManager sInstance;

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    private State mState = State.IDLE;
    private List<AppUpdateChecker.Release> mReleases = Collections.emptyList();
    @Nullable private ErrorKind mErrorKind;
    @Nullable private String mErrorDetail;
    private long mDownloadedBytes, mTotalBytes;
    /** The verified APK, once downloaded. */
    @Nullable private File mApk;
    /** Whether the SHA-256 of {@link #mApk} was compared with the one of the release. */
    private boolean mChecksumVerified;
    @Nullable private Intent mConfirmIntent;
    private final AtomicBoolean mCancelled = new AtomicBoolean(false);
    @Nullable private volatile HttpURLConnection mConnection;

    private UpdateManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @MainThread
    @NonNull
    public static UpdateManager getInstance(@NonNull Context context) {
        if (sInstance == null) sInstance = new UpdateManager(context);
        return sInstance;
    }



    public void addListener(@NonNull Listener listener) {
        if (!mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        mListeners.remove(listener);
    }

    @NonNull public State getState() { return mState; }
    /** The releases that can be installed, newest first. */
    @NonNull public List<AppUpdateChecker.Release> getReleases() { return mReleases; }
    @Nullable public ErrorKind getErrorKind() { return mErrorKind; }
    @Nullable public String getErrorDetail() { return mErrorDetail; }
    public long getDownloadedBytes() { return mDownloadedBytes; }
    /** The size of the download in bytes, or -1 if unknown. */
    public long getTotalBytes() { return mTotalBytes; }
    @Nullable public File getApk() { return mApk; }
    public boolean isChecksumVerified() { return mChecksumVerified; }

    /** The newest release, or {@code null} if the releases are not known or there is none. */
    @Nullable
    public AppUpdateChecker.Release getLatestRelease() {
        return mReleases.isEmpty() ? null : mReleases.get(0);
    }

    /** The releases with a higher version than the installed one, newest first. */
    @NonNull
    public List<AppUpdateChecker.Release> getNewerReleases() {
        List<AppUpdateChecker.Release> newer = new ArrayList<>();
        for (AppUpdateChecker.Release release : mReleases) {
            if (AppUpdateChecker.compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) newer.add(release);
        }
        return newer;
    }

    /** Whether the latest release has a higher version than the installed one. */
    public boolean isUpdateAvailable() {
        AppUpdateChecker.Release latest = getLatestRelease();
        return latest != null && AppUpdateChecker.compareVersions(latest.version, BuildConfig.VERSION_NAME) > 0;
    }

    /** Whether a check, download or install is in progress, so no other one can be started. */
    public boolean isBusy() {
        return mState == State.CHECKING || mState == State.DOWNLOADING || mState == State.INSTALLING;
    }

    /** Take the intent of the system install confirmation, or {@code null} if there is none to show. */
    @Nullable
    public Intent consumeConfirmIntent() {
        Intent intent = mConfirmIntent;
        mConfirmIntent = null;
        return intent;
    }



    /** Get the list of releases from GitHub. */
    public void check() {
        if (isBusy()) return;
        setState(State.CHECKING);
        mExecutor.execute(() -> {
            List<AppUpdateChecker.Release> releases = null;
            String error = null;
            try {
                releases = AppUpdateChecker.fetchReleases();
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to check for updates", e);
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            final List<AppUpdateChecker.Release> result = releases;
            final String finalError = error;
            mHandler.post(() -> {
                if (result != null) {
                    mReleases = result;
                    mErrorKind = null;
                    mErrorDetail = null;
                    deleteOldDownloads();
                    // A finished download of the same release stays usable.
                    AppUpdateChecker.Release latest = getLatestRelease();
                    boolean haveDownload = mApk != null && mApk.isFile() && latest != null && mApk.getName().equals(latest.apkName);
                    if (!haveDownload) mApk = null;
                    setState(haveDownload ? State.DOWNLOADED : State.READY);
                } else {
                    setError(ErrorKind.NETWORK, finalError, State.CHECK_FAILED);
                }
            });
        });
    }

    /** Download and verify the APK of the latest release, which can then be installed with {@link #install()}. */
    public void download() {
        final AppUpdateChecker.Release release = getLatestRelease();
        if (isBusy() || release == null) return;
        if (release.apkUrl == null || release.apkName == null) {
            setError(ErrorKind.NO_APK, null, State.READY);
            return;
        }
        if (!UpdateVerifier.isTrustedDownloadUrl(release.apkUrl, TermuxConstants.TERMUX_GITHUB_REPO_URL) ||
            !UpdateVerifier.isSafeApkName(release.apkName)) {
            setError(ErrorKind.UNTRUSTED_URL, release.apkUrl, State.READY);
            return;
        }

        mCancelled.set(false);
        mDownloadedBytes = 0;
        mTotalBytes = release.apkSize;
        mApk = null;
        mChecksumVerified = false;
        mErrorKind = null;
        mErrorDetail = null;
        setState(State.DOWNLOADING);
        mExecutor.execute(() -> runDownload(release));
    }

    /** Cancel a running download. */
    public void cancelDownload() {
        if (mState != State.DOWNLOADING) return;
        mCancelled.set(true);
        HttpURLConnection connection = mConnection;
        if (connection != null) connection.disconnect();
    }

    /** Hand the downloaded APK to the system installer. */
    public void install() {
        final File apk = mApk;
        if (apk == null || !apk.isFile() || mState == State.INSTALLING || mState == State.DOWNLOADING || mState == State.CHECKING) return;
        mErrorKind = null;
        mErrorDetail = null;
        setState(State.INSTALLING);
        mExecutor.execute(() -> {
            String error = null;
            try {
                ApkInstaller.install(mContext, apk);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start the install of " + apk, e);
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            final String finalError = error;
            if (finalError != null)
                mHandler.post(() -> setError(isNoSpace(finalError) ? ErrorKind.STORAGE : ErrorKind.INSTALL_FAILED, finalError, State.INSTALL_FAILED));
            // Otherwise the result is sent to onInstallStatus().
        });
    }

    private static boolean isNoSpace(@NonNull String message) {
        return message.contains("ENOSPC") || message.toLowerCase(java.util.Locale.ROOT).contains("no space");
    }

    /** Called by {@link InstallResultReceiver} with the result of the install session. */
    @MainThread
    void onInstallStatus(@NonNull InstallStatus status, @Nullable String message, @Nullable Intent confirmIntent) {
        Logger.logInfo(LOG_TAG, "Install status " + status + ": " + message);
        switch (status) {
            case USER_ACTION_REQUIRED:
                if (confirmIntent == null) {
                    setError(ErrorKind.INSTALL_FAILED, message, State.INSTALL_FAILED);
                    return;
                }
                mConfirmIntent = confirmIntent;
                setState(State.WAITING_FOR_USER);
                // If the screen is not open, nobody takes the intent, so offer it in a notification.
                if (mListeners.isEmpty()) showConfirmNotification();
                break;
            case SUCCESS:
                // The app is normally restarted by the system before this is seen.
                setState(State.READY);
                break;
            case CANCELLED:
                // The user said no, the downloaded APK can still be installed.
                setState(State.DOWNLOADED);
                break;
            case BLOCKED:
                setError(ErrorKind.BLOCKED, message, State.INSTALL_FAILED);
                break;
            case SIGNATURE_MISMATCH:
                setError(ErrorKind.SIGNATURE_MISMATCH, message, State.INSTALL_FAILED);
                break;
            case INVALID:
                setError(ErrorKind.INVALID, message, State.INSTALL_FAILED);
                break;
            case STORAGE:
                setError(ErrorKind.STORAGE, message, State.INSTALL_FAILED);
                break;
            default:
                setError(ErrorKind.INSTALL_FAILED, message, State.INSTALL_FAILED);
                break;
        }
    }

    /** Go back from a failed install or check to the list of releases. */
    public void dismissError() {
        if (mState == State.INSTALL_FAILED) setState(mApk != null ? State.DOWNLOADED : State.READY);
        else if (mState == State.CHECK_FAILED) setState(State.IDLE);
        mErrorKind = null;
        mErrorDetail = null;
        notifyListeners();
    }



    @WorkerThread
    private void runDownload(@NonNull AppUpdateChecker.Release release) {
        File directory = new File(mContext.getCacheDir(), "updates");
        File target = new File(directory, release.apkName);
        File partial = new File(directory, release.apkName + ".part");
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create " + directory);

            // A file of an earlier try, like after a failed install, doesn't need to be downloaded again.
            if (target.isFile() && isVerified(target, release)) {
                finishDownload(target, release);
                return;
            }
            target.delete();
            download(release, partial);
            if (!verify(partial, release)) return;
            if (!partial.renameTo(target)) throw new IOException("Could not rename " + partial + " to " + target);
            finishDownload(target, release);
        } catch (CancelledException e) {
            partial.delete();
            mHandler.post(() -> setState(State.READY));
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to download " + release.apkUrl, e);
            partial.delete();
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            mHandler.post(() -> setError(isNoSpace(message) ? ErrorKind.STORAGE : ErrorKind.NETWORK, message, State.READY));
        }
    }

    private static final class CancelledException extends IOException {
        CancelledException() {
            super("Cancelled");
        }
    }

    /** Download the APK of {@code release} to {@code partial}. */
    @WorkerThread
    private void download(@NonNull AppUpdateChecker.Release release, @NonNull File partial) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        mConnection = connection;
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", TermuxConstants.TERMUX_APP_NAME.replace(' ', '-') + "/" + BuildConfig.VERSION_NAME);
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK)
                throw new IOException("HTTP " + responseCode + " " + connection.getResponseMessage());

            long length = connection.getContentLengthLong();
            if (length > 0) mTotalBytes = length;

            long lastProgress = 0;
            long downloaded = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(partial)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (mCancelled.get()) throw new CancelledException();
                    out.write(buffer, 0, read);
                    downloaded += read;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastProgress >= PROGRESS_INTERVAL_MILLIS) {
                        lastProgress = now;
                        final long progress = downloaded;
                        mHandler.post(() -> {
                            if (mState == State.DOWNLOADING) {
                                mDownloadedBytes = progress;
                                notifyListeners();
                            }
                        });
                    }
                }
            } catch (IOException e) {
                // Disconnecting to cancel makes the read fail.
                if (mCancelled.get()) throw new CancelledException();
                throw e;
            }
            final long finalDownloaded = downloaded;
            mHandler.post(() -> mDownloadedBytes = finalDownloaded);
        } finally {
            mConnection = null;
            connection.disconnect();
        }
        if (mCancelled.get()) throw new CancelledException();
    }

    /**
     * Compare the size and the SHA-256 of a downloaded file with the release. On a mismatch the file
     * is deleted, the error is set and {@code false} is returned.
     */
    @WorkerThread
    private boolean verify(@NonNull File file, @NonNull AppUpdateChecker.Release release) throws IOException {
        if (release.apkSize > 0 && file.length() != release.apkSize) {
            file.delete();
            final String detail = file.length() + " / " + release.apkSize;
            mHandler.post(() -> setError(ErrorKind.SIZE_MISMATCH, detail, State.READY));
            return false;
        }
        if (release.apkSha256 != null) {
            String actual;
            try (InputStream in = new FileInputStream(file)) {
                actual = UpdateVerifier.sha256Hex(in);
            }
            if (!UpdateVerifier.checksumMatches(release.apkSha256, actual)) {
                file.delete();
                Logger.logError(LOG_TAG, "SHA-256 mismatch for " + release.apkName + ": expected " + release.apkSha256 + ", got " + actual);
                final String detail = actual;
                mHandler.post(() -> setError(ErrorKind.CHECKSUM_MISMATCH, detail, State.READY));
                return false;
            }
        }
        return true;
    }

    @WorkerThread
    private boolean isVerified(@NonNull File file, @NonNull AppUpdateChecker.Release release) {
        try {
            if (release.apkSize > 0 && file.length() != release.apkSize) return false;
            if (release.apkSha256 == null) return false; // Can't tell that it is complete, download again.
            try (InputStream in = new FileInputStream(file)) {
                return UpdateVerifier.checksumMatches(release.apkSha256, UpdateVerifier.sha256Hex(in));
            }
        } catch (IOException e) {
            return false;
        }
    }

    private void finishDownload(@NonNull File apk, @NonNull AppUpdateChecker.Release release) {
        mHandler.post(() -> {
            mApk = apk;
            mChecksumVerified = release.apkSha256 != null;
            mDownloadedBytes = apk.length();
            mTotalBytes = apk.length();
            setState(State.DOWNLOADED);
        });
    }

    /** Delete downloads of other releases than the latest, which stays to be reused by the next download. */
    private void deleteOldDownloads() {
        File[] files = new File(mContext.getCacheDir(), "updates").listFiles();
        if (files == null) return;
        AppUpdateChecker.Release latest = getLatestRelease();
        for (File file : files) {
            if (latest != null && file.getName().equals(latest.apkName)) continue;
            file.delete();
        }
    }



    private void setState(@NonNull State state) {
        mState = state;
        notifyListeners();
    }

    private void setError(@NonNull ErrorKind kind, @Nullable String detail, @NonNull State state) {
        mErrorKind = kind;
        mErrorDetail = detail;
        mState = state;
        notifyListeners();
    }

    private void notifyListeners() {
        for (Listener listener : mListeners) listener.onUpdateStateChanged();
    }

    private void showConfirmNotification() {
        Intent confirmIntent = mConfirmIntent;
        if (confirmIntent == null) return;
        NotificationUtils.setupNotificationChannel(mContext, NOTIFICATION_CHANNEL_ID,
            mContext.getString(R.string.feature_system_update_channel), NotificationManager.IMPORTANCE_HIGH);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
            new Intent(confirmIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        manager.notify(NOTIFICATION_ID, new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(mContext.getString(R.string.feature_system_update_notification_title))
            .setContentText(mContext.getString(R.string.feature_system_update_notification_text))
            .setSmallIcon(R.drawable.ic_service_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build());
    }

}
