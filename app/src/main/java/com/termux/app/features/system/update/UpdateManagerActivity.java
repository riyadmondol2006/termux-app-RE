package com.termux.app.features.system.update;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.features.system.monitor.ProcParsers;
import com.termux.app.update.AppUpdateChecker;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.theme.NightMode;

import java.util.List;

/**
 * Shows the installed version, the latest release and the changes of the newer releases, and
 * downloads, verifies and installs the update. The work is done by {@link UpdateManager}.
 */
public class UpdateManagerActivity extends AppCompatActivity implements UpdateManager.Listener {

    private static final String LOG_TAG = "UpdateManagerActivity";

    private UpdateManager mManager;
    private TextView mStatus, mInstalled, mLatest, mProgressText, mNotice, mNotesTitle;
    private LinearProgressIndicator mProgress;
    private MaterialButton mPrimary, mCheck, mSkip, mPage;
    private LinearLayout mNotes;

    /** The releases the notes were rendered for, to not render markdown again for every progress update. */
    @Nullable private List<AppUpdateChecker.Release> mRenderedReleases;
    /** Install as soon as the download is done, because the user asked to download and install. */
    private boolean mInstallWhenDownloaded;
    /** Continue with the download after the user allowed installing apps in the system settings. */
    private boolean mWaitingForInstallPermission;
    @Nullable private Intent mLastConfirmIntent;

    /** Open the update manager. */
    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, UpdateManagerActivity.class);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_system_update);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.feature_system_update_title);

        mStatus = findViewById(R.id.feature_system_update_status);
        mInstalled = findViewById(R.id.feature_system_update_installed);
        mLatest = findViewById(R.id.feature_system_update_latest);
        mProgress = findViewById(R.id.feature_system_update_progress);
        mProgressText = findViewById(R.id.feature_system_update_progress_text);
        mNotice = findViewById(R.id.feature_system_update_notice);
        mPrimary = findViewById(R.id.feature_system_update_primary);
        mCheck = findViewById(R.id.feature_system_update_check);
        mSkip = findViewById(R.id.feature_system_update_skip);
        mPage = findViewById(R.id.feature_system_update_page);
        mNotesTitle = findViewById(R.id.feature_system_update_notes_title);
        mNotes = findViewById(R.id.feature_system_update_notes);

        mManager = UpdateManager.getInstance(this);
        mCheck.setOnClickListener(v -> mManager.check());
        mPage.setOnClickListener(v -> {
            AppUpdateChecker.Release latest = mManager.getLatestRelease();
            ShareUtils.openUrl(this, latest != null && !latest.htmlUrl.isEmpty() ? latest.htmlUrl : TermuxConstants.TERMUX_GITHUB_REPO_URL + "/releases");
        });
        mSkip.setOnClickListener(v -> toggleSkip());
        mPrimary.setOnClickListener(v -> onPrimaryClicked());
    }

    @Override
    protected void onStart() {
        super.onStart();
        mManager.addListener(this);
        // A fresh check on open, unless a download or install is going on.
        if (!mManager.isBusy() && (mManager.getReleases().isEmpty() || mManager.getState() == UpdateManager.State.IDLE))
            mManager.check();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back from the system settings of "Install unknown apps".
        if (mWaitingForInstallPermission) {
            mWaitingForInstallPermission = false;
            if (canInstallPackages()) startDownload();
        }
    }

    @Override
    protected void onStop() {
        mManager.removeListener(this);
        super.onStop();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onUpdateStateChanged() {
        render();
    }



    private void render() {
        UpdateManager.State state = mManager.getState();
        AppUpdateChecker.Release latest = mManager.getLatestRelease();
        boolean updateAvailable = mManager.isUpdateAvailable();

        mInstalled.setText(getString(R.string.feature_system_update_installed, BuildConfig.VERSION_NAME));
        if (latest != null) {
            String date = latest.publishedAt.length() >= 10 ? " · " + latest.publishedAt.substring(0, 10) : "";
            mLatest.setText(getString(R.string.feature_system_update_latest, latest.version + date));
        } else {
            mLatest.setText(R.string.feature_system_update_latest_unknown);
        }

        // Status line.
        switch (state) {
            case CHECKING:
                mStatus.setText(R.string.feature_system_update_checking);
                break;
            case CHECK_FAILED:
                mStatus.setText(R.string.feature_system_update_check_failed);
                break;
            case DOWNLOADING:
                mStatus.setText(getString(R.string.feature_system_update_downloading, latest != null ? "v" + latest.version : ""));
                break;
            case DOWNLOADED:
                mStatus.setText(R.string.feature_system_update_downloaded);
                break;
            case INSTALLING:
                mStatus.setText(R.string.feature_system_update_installing);
                break;
            case WAITING_FOR_USER:
                mStatus.setText(R.string.feature_system_update_waiting);
                break;
            case INSTALL_FAILED:
                mStatus.setText(R.string.feature_system_update_install_failed);
                break;
            default:
                mStatus.setText(latest == null ? R.string.feature_system_update_no_release :
                    updateAvailable ? R.string.feature_system_update_available : R.string.feature_system_update_up_to_date);
                break;
        }

        renderProgress(state);
        renderNotice(state, latest);
        renderButtons(state, latest, updateAvailable);
        renderNotes(latest);

        if (state == UpdateManager.State.WAITING_FOR_USER) launchConfirmation();
        if (state == UpdateManager.State.DOWNLOADED && mInstallWhenDownloaded) {
            mInstallWhenDownloaded = false;
            mManager.install();
        } else if (state != UpdateManager.State.DOWNLOADING && state != UpdateManager.State.DOWNLOADED) {
            mInstallWhenDownloaded = false;
        }
    }

    private void renderProgress(@NonNull UpdateManager.State state) {
        boolean downloading = state == UpdateManager.State.DOWNLOADING;
        boolean installing = state == UpdateManager.State.INSTALLING || state == UpdateManager.State.WAITING_FOR_USER;
        mProgress.setVisibility(downloading || installing ? View.VISIBLE : View.GONE);
        mProgressText.setVisibility(downloading ? View.VISIBLE : View.GONE);
        if (downloading) {
            long total = mManager.getTotalBytes();
            long done = mManager.getDownloadedBytes();
            if (total > 0) {
                mProgress.setIndeterminate(false);
                mProgress.setProgressCompat((int) Math.min(1000, done * 1000 / total), false);
                mProgressText.setText(getString(R.string.feature_system_update_progress, ProcParsers.formatBytes(done),
                    ProcParsers.formatBytes(total), (int) Math.min(100, done * 100 / total)));
            } else {
                mProgress.setIndeterminate(true);
                mProgressText.setText(getString(R.string.feature_system_update_progress_unknown_total, ProcParsers.formatBytes(done)));
            }
        } else if (installing) {
            mProgress.setIndeterminate(true);
        }
    }

    private void renderNotice(@NonNull UpdateManager.State state, @Nullable AppUpdateChecker.Release latest) {
        StringBuilder notice = new StringBuilder();
        UpdateManager.ErrorKind error = mManager.getErrorKind();
        String detail = mManager.getErrorDetail() != null ? mManager.getErrorDetail() : "";
        if (error != null) {
            switch (error) {
                case NETWORK:
                    notice.append(getString(R.string.feature_system_update_error_network, detail));
                    break;
                case NO_APK:
                    notice.append(getString(R.string.feature_system_update_error_no_apk, android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS)));
                    break;
                case UNTRUSTED_URL:
                    notice.append(getString(R.string.feature_system_update_error_untrusted, detail));
                    break;
                case SIZE_MISMATCH:
                    notice.append(getString(R.string.feature_system_update_error_size, detail));
                    break;
                case CHECKSUM_MISMATCH:
                    notice.append(getString(R.string.feature_system_update_error_checksum,
                        latest != null && latest.apkSha256 != null ? latest.apkSha256 : "?", detail));
                    break;
                case STORAGE:
                    notice.append(getString(R.string.feature_system_update_error_storage));
                    break;
                case SIGNATURE_MISMATCH:
                    notice.append(getString(R.string.feature_system_update_error_signature, detail));
                    break;
                case BLOCKED:
                    notice.append(getString(R.string.feature_system_update_error_blocked, detail));
                    break;
                case INVALID:
                    notice.append(getString(R.string.feature_system_update_error_invalid, detail));
                    break;
                default:
                    notice.append(getString(R.string.feature_system_update_error_failed, detail));
                    break;
            }
        } else if (state == UpdateManager.State.DOWNLOADED && mManager.getApk() != null) {
            notice.append(mManager.isChecksumVerified() && latest != null && latest.apkSha256 != null ?
                getString(R.string.feature_system_update_verified, latest.apkSha256) :
                getString(R.string.feature_system_update_not_verified));
        } else if (latest != null && isSkipped(latest)) {
            notice.append(getString(R.string.feature_system_update_skipped));
        }
        if (BuildConfig.DEBUG) {
            if (notice.length() > 0) notice.append("\n\n");
            notice.append(getString(R.string.feature_system_update_debug_note));
        }
        mNotice.setText(notice);
        mNotice.setVisibility(notice.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void renderButtons(@NonNull UpdateManager.State state, @Nullable AppUpdateChecker.Release latest, boolean updateAvailable) {
        boolean sameVersion = latest != null && AppUpdateChecker.compareVersions(latest.version, BuildConfig.VERSION_NAME) == 0;
        boolean installable = latest != null && latest.apkUrl != null && (updateAvailable || sameVersion);

        mPrimary.setVisibility(View.VISIBLE);
        mPrimary.setEnabled(true);
        switch (state) {
            case CHECKING:
                mPrimary.setText(R.string.feature_system_update_checking);
                mPrimary.setEnabled(false);
                break;
            case DOWNLOADING:
                mPrimary.setText(R.string.feature_system_update_cancel);
                break;
            case INSTALLING:
                mPrimary.setText(R.string.feature_system_update_installing);
                mPrimary.setEnabled(false);
                break;
            case WAITING_FOR_USER:
                mPrimary.setText(R.string.feature_system_update_continue);
                break;
            case DOWNLOADED:
                mPrimary.setText(R.string.feature_system_update_install);
                break;
            default:
                if (!installable) {
                    mPrimary.setVisibility(View.GONE);
                } else {
                    mPrimary.setText(updateAvailable ? getString(R.string.feature_system_update_download_install) :
                        getString(R.string.feature_system_update_reinstall, "v" + latest.version));
                }
                break;
        }

        mCheck.setEnabled(!mManager.isBusy());
        boolean canSkip = latest != null && updateAvailable;
        mSkip.setVisibility(canSkip ? View.VISIBLE : View.GONE);
        if (canSkip) mSkip.setText(isSkipped(latest) ? R.string.feature_system_update_unskip : R.string.feature_system_update_skip);
        mPage.setVisibility(latest != null ? View.VISIBLE : View.GONE);
    }

    private void renderNotes(@Nullable AppUpdateChecker.Release latest) {
        List<AppUpdateChecker.Release> releases = mManager.getReleases();
        if (releases == mRenderedReleases) return;
        mRenderedReleases = releases;
        mNotes.removeAllViews();

        List<AppUpdateChecker.Release> shown = mManager.getNewerReleases();
        if (shown.isEmpty() && latest != null) {
            shown = releases.subList(0, 1);
            mNotesTitle.setText(R.string.feature_system_update_notes_latest);
        } else {
            mNotesTitle.setText(getString(R.string.feature_system_update_notes_newer, BuildConfig.VERSION_NAME));
        }
        mNotesTitle.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);

        for (AppUpdateChecker.Release release : shown) {
            View item = LayoutInflater.from(this).inflate(R.layout.feature_system_item_release_notes, mNotes, false);
            ((TextView) item.findViewById(R.id.feature_system_release_title)).setText(release.name.isEmpty() ? "v" + release.version : release.name);
            ((TextView) item.findViewById(R.id.feature_system_release_date)).setText(
                release.publishedAt.length() >= 10 ? release.publishedAt.substring(0, 10) : "");
            TextView body = item.findViewById(R.id.feature_system_release_body);
            String notes = release.notes.trim();
            if (notes.isEmpty()) {
                body.setText(R.string.feature_system_update_no_notes);
            } else {
                body.setText(MarkdownUtils.getSpannedMarkdownText(this, notes));
                body.setMovementMethod(LinkMovementMethod.getInstance());
            }
            mNotes.addView(item);
        }
    }



    private void onPrimaryClicked() {
        switch (mManager.getState()) {
            case DOWNLOADING:
                mInstallWhenDownloaded = false;
                mManager.cancelDownload();
                break;
            case DOWNLOADED:
                if (ensureInstallAllowed()) mManager.install();
                break;
            case WAITING_FOR_USER:
                if (mLastConfirmIntent != null) startConfirmation(mLastConfirmIntent);
                break;
            default:
                mManager.dismissError();
                if (ensureInstallAllowed()) startDownload();
                break;
        }
    }

    private void startDownload() {
        mInstallWhenDownloaded = true;
        mManager.download();
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    /** Whether the app may install apps. If not, explains it and opens the system settings for it. */
    private boolean ensureInstallAllowed() {
        if (canInstallPackages()) return true;
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.feature_system_update_permission_title)
            .setMessage(getString(R.string.feature_system_update_permission_message, TermuxConstants.TERMUX_APP_NAME))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.feature_system_update_permission_open, (dialog, which) -> {
                mWaitingForInstallPermission = true;
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                } catch (ActivityNotFoundException e) {
                    mWaitingForInstallPermission = false;
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open the settings for installing unknown apps", e);
                    startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                }
            })
            .show();
        return false;
    }

    private void launchConfirmation() {
        Intent intent = mManager.consumeConfirmIntent();
        if (intent == null) return;
        mLastConfirmIntent = intent;
        NotificationManager notifications = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifications != null) notifications.cancel(1348);
        startConfirmation(intent);
    }

    private void startConfirmation(@NonNull Intent intent) {
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to open the install confirmation", e);
            Logger.showToast(this, e.getMessage(), true);
        }
    }

    private boolean isSkipped(@NonNull AppUpdateChecker.Release release) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this);
        return preferences != null && release.version.equals(preferences.getUpdateCheckSkippedVersion());
    }

    private void toggleSkip() {
        AppUpdateChecker.Release latest = mManager.getLatestRelease();
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this);
        if (latest == null || preferences == null) return;
        preferences.setUpdateCheckSkippedVersion(isSkipped(latest) ? null : latest.version);
        render();
    }

}
