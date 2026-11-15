package com.termux.app.fragments.settings.termux;

import android.Manifest;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;

/**
 * Shows the state of the permissions that Termux needs on newer Android versions and lets the user
 * grant them, including after they have been denied, in which case the system does not show
 * the permission prompt anymore and the user must grant them from Android settings.
 */
@Keep
public class PermissionsPreferencesFragment extends PreferenceFragmentCompat {

    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_STORAGE = "storage";
    private static final String KEY_DISPLAY_OVER_OTHER_APPS = "display_over_other_apps";
    private static final String KEY_BATTERY_OPTIMIZATIONS = "battery_optimizations";

    private static final String LOG_TAG = "PermissionsPreferencesFragment";

    /** The runtime permission currently being requested with {@link #mRequestPermissionLauncher}. */
    @Nullable
    private String mRequestedPermission;

    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onRequestPermissionResult);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.termux_permissions_preferences, rootKey);

        setOnPreferenceClickListener(KEY_NOTIFICATIONS, this::onNotificationsClicked);
        setOnPreferenceClickListener(KEY_STORAGE, this::onStorageClicked);
        setOnPreferenceClickListener(KEY_DISPLAY_OVER_OTHER_APPS, this::onDisplayOverOtherAppsClicked);
        setOnPreferenceClickListener(KEY_BATTERY_OPTIMIZATIONS, this::onBatteryOptimizationsClicked);

        // Starting activities from background is only restricted on Android 10+.
        Preference preference = findPreference(KEY_DISPLAY_OVER_OTHER_APPS);
        if (preference != null)
            preference.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Permissions may have been changed from Android settings.
        updatePreferences();
    }

    private void updatePreferences() {
        Activity activity = getActivity();
        if (activity == null) return;

        setSummary(KEY_NOTIFICATIONS, PermissionUtils.checkNotificationsEnabled(activity) ?
            R.string.termux_permission_notifications_granted : R.string.termux_permission_notifications_not_granted);

        setSummary(KEY_STORAGE, PermissionUtils.checkStoragePermission(activity, PermissionUtils.isLegacyExternalStoragePossible(activity)) ?
            R.string.termux_permission_storage_granted : R.string.termux_permission_storage_not_granted);

        setSummary(KEY_DISPLAY_OVER_OTHER_APPS, PermissionUtils.checkDisplayOverOtherAppsPermission(activity) ?
            R.string.termux_permission_display_over_other_apps_granted : R.string.termux_permission_display_over_other_apps_not_granted);

        setSummary(KEY_BATTERY_OPTIMIZATIONS, PermissionUtils.checkIfBatteryOptimizationsDisabled(activity) ?
            R.string.termux_permission_battery_optimizations_granted : R.string.termux_permission_battery_optimizations_not_granted);
    }



    private void onNotificationsClicked(@NonNull Activity activity) {
        if (!PermissionUtils.checkNotificationsEnabled(activity) &&
            PermissionUtils.canRequestNotificationsPermission(activity) &&
            !PermissionUtils.checkPermission(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            // The permission cannot be requested by apps targeting API < 33, notifications may also
            // have been disabled from notification settings, or are already enabled and user may
            // want to manage them.
            PermissionUtils.openAppNotificationSettings(activity);
        }
    }

    private void onStorageClicked(@NonNull Activity activity) {
        boolean isLegacyExternalStoragePossible = PermissionUtils.isLegacyExternalStoragePossible(activity);
        if (PermissionUtils.checkStoragePermission(activity, isLegacyExternalStoragePossible)) {
            PermissionUtils.openAppDetailsSettings(activity);
        } else if (isLegacyExternalStoragePossible || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            PermissionUtils.requestManageStorageExternalPermission(activity);
        }
    }

    private void onDisplayOverOtherAppsClicked(@NonNull Activity activity) {
        // On Android 11+, this opens the list of all apps instead of the page for the app.
        PermissionUtils.requestDisplayOverOtherAppsPermission(activity);
    }

    private void onBatteryOptimizationsClicked(@NonNull Activity activity) {
        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(activity))
            PermissionUtils.requestDisableBatteryOptimizations(activity);
        else
            PermissionUtils.openAppDetailsSettings(activity);
    }



    private void requestPermission(@NonNull String permission) {
        Logger.logInfo(LOG_TAG, "Requesting " + permission + " permission");
        mRequestedPermission = permission;
        mRequestPermissionLauncher.launch(permission);
    }

    private void onRequestPermissionResult(boolean granted) {
        String permission = mRequestedPermission;
        mRequestedPermission = null;

        Activity activity = getActivity();
        if (activity == null || permission == null) return;

        Logger.logInfo(LOG_TAG, "The " + permission + " permission " + (granted ? "granted" : "not granted") + " by user on request");
        updatePreferences();
        if (granted) return;

        // If the user has denied the permission before, the system may not show the prompt anymore,
        // like on Android 11+ after it has been denied twice, so open Android settings instead.
        if (PermissionUtils.isPermissionPermanentlyDenied(activity, permission)) {
            if (Manifest.permission.POST_NOTIFICATIONS.equals(permission))
                PermissionUtils.openAppNotificationSettings(activity);
            else
                PermissionUtils.openAppDetailsSettings(activity);
        }
    }



    private interface OnClickAction {
        void run(@NonNull Activity activity);
    }

    private void setOnPreferenceClickListener(@NonNull String key, @NonNull OnClickAction action) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        preference.setOnPreferenceClickListener(p -> {
            Activity activity = getActivity();
            if (activity != null)
                action.run(activity);
            return true;
        });
    }

    private void setSummary(@NonNull String key, int summaryResId) {
        Preference preference = findPreference(key);
        if (preference != null)
            preference.setSummary(summaryResId);
    }

}
