package com.termux.app.update;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.features.system.update.UpdateManagerActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the GitHub releases of {@link TermuxConstants#TERMUX_GITHUB_REPO_URL} for a newer version
 * of the app and shows a dialog to download it.
 *
 * Release builds check automatically when {@link com.termux.app.TermuxActivity} is shown, at most
 * once every {@link #AUTO_CHECK_INTERVAL_MILLIS}, unless disabled in the settings. Debug builds
 * only check when requested from the settings, since release APKs are signed with a different
 * key and can't be installed over them.
 *
 * Releases are the "v<major>.<minor>.<patch>" tags made by the Build workflow, other releases of
 * the repository, like bootstrap releases, and drafts and prereleases are ignored.
 */
public final class AppUpdateChecker {

    /** The minimum time between automatic checks. */
    public static final long AUTO_CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(6);

    /** Remembers the version that last ran, to show a popup after the app was updated. */
    private static final String VERSION_PREFERENCES = "termux_re_app_version";
    private static final String KEY_LAST_VERSION_CODE = "last_version_code";

    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RELEASE_NOTES_CHARS = 2000;

    private static final Pattern GITHUB_REPO_URL_PATTERN = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/*$");
    private static final Pattern RELEASE_TAG_PATTERN = Pattern.compile("^v(\\d+\\.\\d+\\.\\d+)$");
    private static final Pattern SHA256_DIGEST_PATTERN = Pattern.compile("^sha256:([0-9a-fA-F]{64})$");
    private static final Pattern VERSION_CORE_PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$");

    private static final AtomicBoolean sCheckRunning = new AtomicBoolean(false);

    private static final String LOG_TAG = "AppUpdateChecker";

    private AppUpdateChecker() {}



    /** A release that can be installed. */
    public static final class Release {
        /** The version, like "1.2.0". */
        @NonNull public final String version;
        /** The name of the release, or the tag if it has none. */
        @NonNull public final String name;
        /** The release page. */
        @NonNull public final String htmlUrl;
        /** The release notes in markdown. */
        @NonNull public final String notes;
        /** The publish time like "2026-05-01T12:00:00Z", or an empty string. */
        @NonNull public final String publishedAt;
        /** The APK for this device, or {@code null} if none was found. */
        @Nullable public final String apkUrl;
        /** The file name of the APK for this device, or {@code null} if none was found. */
        @Nullable public final String apkName;
        /** The size of the APK for this device in bytes, or -1 if unknown. */
        public final long apkSize;
        /** The lowercase hex SHA-256 of the APK for this device from the digest GitHub shows for the asset, or {@code null} if there is none. */
        @Nullable public final String apkSha256;

        Release(@NonNull String version, @NonNull String name, @NonNull String htmlUrl, @NonNull String notes,
                @NonNull String publishedAt, @Nullable String apkUrl, @Nullable String apkName, long apkSize,
                @Nullable String apkSha256) {
            this.version = version;
            this.name = name;
            this.htmlUrl = htmlUrl;
            this.notes = notes;
            this.publishedAt = publishedAt;
            this.apkUrl = apkUrl;
            this.apkName = apkName;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
        }
    }



    /**
     * Check for a new release if automatic checks are enabled and the last one was at least
     * {@link #AUTO_CHECK_INTERVAL_MILLIS} ago. Shows a dialog if there is one.
     */
    public static void checkForUpdateIfDue(@NonNull Activity activity) {
        // Release APKs can't be installed over debug builds, which are signed with the test key.
        if (BuildConfig.DEBUG || getReleasesApiUrl(TermuxConstants.TERMUX_GITHUB_REPO_URL) == null)
            return;

        // Not on the first start, while the bootstrap is being installed.
        if (!new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH, "bin").isDirectory())
            return;

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity);
        if (preferences == null || !preferences.isUpdateCheckEnabled())
            return;

        long now = System.currentTimeMillis();
        long lastTime = preferences.getUpdateCheckLastTime();
        if (lastTime > 0 && lastTime <= now && now - lastTime < AUTO_CHECK_INTERVAL_MILLIS)
            return;

        if (check(activity, false))
            preferences.setUpdateCheckLastTime(now);
    }

    /**
     * Show a popup once after the app was updated to a newer version, with a link to the changes
     * of the release. Not after a new install.
     */
    public static void showUpdatedPopupIfUpdated(@NonNull Activity activity) {
        android.content.SharedPreferences preferences = activity.getSharedPreferences(VERSION_PREFERENCES, Context.MODE_PRIVATE);
        int current = BuildConfig.VERSION_CODE;
        int last = preferences.getInt(KEY_LAST_VERSION_CODE, -1);
        if (last == current) return;
        preferences.edit().putInt(KEY_LAST_VERSION_CODE, current).apply();

        boolean updated;
        if (last == -1) {
            // Versions before this one did not save their version code, so tell an update from a
            // new install by the install times.
            try {
                android.content.pm.PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                updated = info.lastUpdateTime > info.firstInstallTime;
            } catch (Exception e) {
                updated = false;
            }
        } else {
            updated = current > last;
        }
        if (!updated) return;

        Matcher matcher = VERSION_CORE_PATTERN.matcher(BuildConfig.VERSION_NAME);
        String version = matcher.matches() ? matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) : BuildConfig.VERSION_NAME;
        String repoUrl = TermuxConstants.TERMUX_GITHUB_REPO_URL.replaceAll("(\\.git)?/*$", "");
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.title_app_updated, TermuxConstants.TERMUX_APP_NAME))
            .setMessage(activity.getString(R.string.msg_app_updated, TermuxConstants.TERMUX_APP_NAME, version))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.action_app_updated_whats_new, (dialog, which) ->
                com.termux.shared.interact.ShareUtils.openUrl(activity, repoUrl + "/releases/tag/v" + version))
            .show();
    }

    /** Check for a new release now and show the result, also if there is none or it failed. */
    public static void checkForUpdateNow(@NonNull Activity activity) {
        if (getReleasesApiUrl(TermuxConstants.TERMUX_GITHUB_REPO_URL) == null) {
            Logger.showToast(activity, activity.getString(R.string.msg_update_check_not_supported), true);
            return;
        }

        Logger.showToast(activity, activity.getString(R.string.msg_update_check_running), false);
        check(activity, true);
    }

    /** Returns {@code false} if a check is already running. */
    private static boolean check(@NonNull Activity activity, boolean userRequested) {
        if (!sCheckRunning.compareAndSet(false, true))
            return false;

        final WeakReference<Activity> activityReference = new WeakReference<>(activity);
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Release release = null;
            String error = null;
            try {
                release = getLatestRelease();
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to check for a new release", e);
                error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            } finally {
                sCheckRunning.set(false);
            }

            final Release finalRelease = release;
            final String finalError = error;
            handler.post(() -> {
                Activity currentActivity = activityReference.get();
                if (currentActivity == null || currentActivity.isFinishing() || currentActivity.isDestroyed())
                    return;
                onCheckFinished(currentActivity, finalRelease, finalError, userRequested);
            });
        }, LOG_TAG).start();

        return true;
    }

    private static void onCheckFinished(@NonNull Activity activity, @Nullable Release release,
                                        @Nullable String error, boolean userRequested) {
        if (error != null) {
            if (userRequested)
                Logger.showToast(activity, activity.getString(R.string.msg_update_check_failed, error), true);
            return;
        }

        String installedVersion = BuildConfig.VERSION_NAME;
        if (release == null || compareVersions(release.version, installedVersion) <= 0) {
            Logger.logInfo(LOG_TAG, "No newer release than " + installedVersion + " found");
            if (userRequested)
                Logger.showToast(activity, activity.getString(R.string.msg_update_check_up_to_date, installedVersion), true);
            return;
        }

        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity);
        if (!userRequested && preferences != null && release.version.equals(preferences.getUpdateCheckSkippedVersion())) {
            Logger.logInfo(LOG_TAG, "Not showing the skipped release " + release.version);
            return;
        }

        Logger.logInfo(LOG_TAG, "Release " + release.version + " is newer than " + installedVersion);
        showUpdateDialog(activity, release, installedVersion, preferences);
    }

    private static void showUpdateDialog(@NonNull Activity activity, @NonNull Release release,
                                         @NonNull String installedVersion,
                                         @Nullable TermuxAppSharedPreferences preferences) {
        StringBuilder message = new StringBuilder(activity.getString(R.string.msg_update_available,
            TermuxConstants.TERMUX_APP_NAME, release.version, installedVersion));
        if (release.apkSize > 0)
            message.append("\n\n").append(activity.getString(R.string.msg_update_available_size,
                android.text.format.Formatter.formatShortFileSize(activity, release.apkSize)));
        String notes = release.notes.trim();
        if (!notes.isEmpty()) {
            if (notes.length() > MAX_RELEASE_NOTES_CHARS)
                notes = notes.substring(0, MAX_RELEASE_NOTES_CHARS) + "…";
            message.append("\n\n").append(notes);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.title_update_available)
            .setMessage(MarkdownUtils.getSpannedMarkdownText(activity, message.toString()))
            // The update manager downloads, verifies and installs the APK. Without an APK for this
            // device it shows the release instead.
            .setPositiveButton(release.apkUrl != null ? R.string.action_update_now : R.string.feature_system_update_open, (dialog, which) -> {
                if (release.apkUrl != null) UpdateManagerActivity.startAndDownload(activity);
                else UpdateManagerActivity.start(activity);
            })
            .setNegativeButton(R.string.action_update_later, null);
        if (preferences != null)
            builder.setNeutralButton(R.string.action_update_skip, (dialog, which) ->
                preferences.setUpdateCheckSkippedVersion(release.version));

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null)
                messageView.setMovementMethod(LinkMovementMethod.getInstance());
        });
        dialog.show();
    }



    /** Get the newest release from GitHub, or {@code null} if there is none. Must not be called on the main thread. */
    @Nullable
    private static Release getLatestRelease() throws IOException, JSONException {
        List<Release> releases = fetchReleases();
        return releases.isEmpty() ? null : releases.get(0);
    }

    /**
     * Get the releases from GitHub that can be installed, newest first, also the ones with a lower
     * version than the installed one. Empty if there is none. Must not be called on the main thread.
     */
    @NonNull
    public static List<Release> fetchReleases() throws IOException, JSONException {
        String apiUrl = getReleasesApiUrl(TermuxConstants.TERMUX_GITHUB_REPO_URL);
        if (apiUrl == null) return new ArrayList<>();

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl + "?per_page=30").openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", TermuxConstants.TERMUX_APP_NAME.replace(' ', '-') + "/" + BuildConfig.VERSION_NAME);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK)
                throw new IOException("HTTP " + responseCode + " " + connection.getResponseMessage());

            try (InputStream inputStream = connection.getInputStream()) {
                return getReleases(new JSONArray(readString(inputStream)), Build.SUPPORTED_ABIS);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readString(@NonNull InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
            if (outputStream.size() > MAX_RESPONSE_BYTES)
                throw new IOException("Response is larger than " + MAX_RESPONSE_BYTES + " bytes");
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Get the GitHub API url for the releases of a repository, like
     * "https://api.github.com/repos/owner/repo/releases" for "https://github.com/owner/repo".
     * Returns {@code null} if the url is not for a GitHub repository.
     */
    @VisibleForTesting
    @Nullable
    static String getReleasesApiUrl(@Nullable String repoUrl) {
        if (repoUrl == null) return null;
        Matcher matcher = GITHUB_REPO_URL_PATTERN.matcher(repoUrl.trim());
        if (!matcher.matches()) return null;
        return "https://api.github.com/repos/" + matcher.group(1) + "/" + matcher.group(2) + "/releases";
    }

    /**
     * Get the newest "v<major>.<minor>.<patch>" release in a GitHub API releases list, ignoring
     * drafts, prereleases and releases with other tags. Returns {@code null} if there is none.
     */
    @VisibleForTesting
    @Nullable
    static Release getLatestRelease(@NonNull JSONArray releases, @NonNull String[] supportedAbis) throws JSONException {
        List<Release> list = getReleases(releases, supportedAbis);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * Get all "v<major>.<minor>.<patch>" releases in a GitHub API releases list, ignoring drafts,
     * prereleases and releases with other tags, with the highest version first.
     */
    @VisibleForTesting
    @NonNull
    static List<Release> getReleases(@NonNull JSONArray releases, @NonNull String[] supportedAbis) throws JSONException {
        List<Release> result = new ArrayList<>();
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
            Matcher matcher = RELEASE_TAG_PATTERN.matcher(release.optString("tag_name"));
            if (!matcher.matches()) continue;
            result.add(parseRelease(release, matcher.group(1), supportedAbis));
        }
        // A stable sort, so releases with the same version stay in the order of the API.
        Collections.sort(result, (a, b) -> compareVersions(b.version, a.version));
        return result;
    }

    @NonNull
    private static Release parseRelease(@NonNull JSONObject release, @NonNull String version, @NonNull String[] supportedAbis) throws JSONException {
        List<JSONObject> apks = new ArrayList<>();
        List<String> apkNames = new ArrayList<>();
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name");
                String url = asset.optString("browser_download_url");
                if (name.endsWith(".apk") && !url.isEmpty()) {
                    apks.add(asset);
                    apkNames.add(name);
                }
            }
        }
        int apkIndex = getApkIndex(apkNames, supportedAbis);
        JSONObject apk = apkIndex >= 0 ? apks.get(apkIndex) : null;

        String name = release.optString("name");
        if (name.isEmpty()) name = release.optString("tag_name");
        return new Release(version, name, release.optString("html_url"), release.optString("body"),
            release.optString("published_at"),
            apk != null ? apk.optString("browser_download_url") : null,
            apk != null ? apk.optString("name") : null,
            apk != null ? apk.optLong("size", -1) : -1,
            apk != null ? parseSha256Digest(apk.optString("digest")) : null);
    }

    /**
     * Get the lowercase hex SHA-256 from the "digest" of a GitHub release asset, like
     * "sha256:9feb...", or {@code null} if there is none or it is for another algorithm.
     */
    @VisibleForTesting
    @Nullable
    static String parseSha256Digest(@Nullable String digest) {
        if (digest == null) return null;
        Matcher matcher = SHA256_DIGEST_PATTERN.matcher(digest.trim());
        return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /**
     * Get the index of the release APK for the first supported ABI, or else of the universal
     * release APK, in a list of APK file names like "termux-re_v1.0.0-release_arm64-v8a.apk".
     * Returns -1 if there is none.
     */
    @VisibleForTesting
    static int getApkIndex(@NonNull List<String> apkNames, @NonNull String[] supportedAbis) {
        List<String> abis = new ArrayList<>();
        for (String abi : supportedAbis) abis.add(abi);
        abis.add("universal");
        for (String abi : abis) {
            for (int i = 0; i < apkNames.size(); i++) {
                String name = apkNames.get(i);
                if (name.endsWith("-release_" + abi + ".apk"))
                    return i;
            }
        }
        return -1;
    }

    /**
     * Compare two semantic versions, like "1.2.0", "1.3.0-beta.1" or "1.2.0+3.g1a2b3c4".
     * Build metadata is ignored and a prerelease is lower than the release. Versions that can't
     * be parsed are lower than any other.
     *
     * @return Returns a negative number, zero or a positive number if {@code a} is lower than,
     * equal to or higher than {@code b}.
     */
    public static int compareVersions(@Nullable String a, @Nullable String b) {
        long[] aCore = parseVersionCore(a);
        long[] bCore = parseVersionCore(b);
        if (aCore == null || bCore == null) return (aCore == null ? 0 : 1) - (bCore == null ? 0 : 1);
        for (int i = 0; i < 3; i++) {
            if (aCore[i] != bCore[i]) return Long.compare(aCore[i], bCore[i]);
        }
        return comparePrereleases(getPrerelease(a), getPrerelease(b));
    }

    @Nullable
    private static long[] parseVersionCore(@Nullable String version) {
        if (version == null) return null;
        Matcher matcher = VERSION_CORE_PATTERN.matcher(version.trim());
        if (!matcher.matches()) return null;
        try {
            return new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)), Long.parseLong(matcher.group(3))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String getPrerelease(@NonNull String version) {
        String withoutMetadata = version.trim().split("\\+", 2)[0];
        int index = withoutMetadata.indexOf('-');
        return index >= 0 ? withoutMetadata.substring(index + 1) : null;
    }

    private static int comparePrereleases(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) return (a == null ? 1 : 0) - (b == null ? 1 : 0);
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        for (int i = 0; i < Math.min(aParts.length, bParts.length); i++) {
            boolean aNumeric = aParts[i].matches("\\d+");
            boolean bNumeric = bParts[i].matches("\\d+");
            int result;
            // Numeric identifiers have no leading zeros, so the longer one is higher.
            if (aNumeric && bNumeric) result = aParts[i].length() != bParts[i].length() ?
                Integer.compare(aParts[i].length(), bParts[i].length()) : aParts[i].compareTo(bParts[i]);
            else if (aNumeric != bNumeric) result = aNumeric ? -1 : 1;
            else result = aParts[i].compareTo(bParts[i]);
            if (result != 0) return result;
        }
        return Integer.compare(aParts.length, bParts.length);
    }

}
