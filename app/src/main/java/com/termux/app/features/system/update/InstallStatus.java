package com.termux.app.features.system.update;

import android.content.pm.PackageInstaller;

import androidx.annotation.Nullable;

/** What the status the system sends after an install session means, see {@link PackageInstaller#EXTRA_STATUS}. */
public enum InstallStatus {
    /** The system needs the user to confirm the install. */
    USER_ACTION_REQUIRED,
    SUCCESS,
    /** The user cancelled the install. */
    CANCELLED,
    /** The install is blocked, for example by the device policy or because installs from this app are not allowed. */
    BLOCKED,
    /** The update is signed with another key than the installed app, so Android refuses to update it. */
    SIGNATURE_MISMATCH,
    /** The APK is not for this device or is broken. */
    INVALID,
    /** There is not enough storage. */
    STORAGE,
    /** Any other failure. */
    FAILED;

    /**
     * Get the meaning of the {@link PackageInstaller#EXTRA_STATUS} and
     * {@link PackageInstaller#EXTRA_STATUS_MESSAGE} of an install result.
     */
    public static InstallStatus classify(int status, @Nullable String message) {
        String text = message != null ? message : "";
        // The message names the reason more precisely than the status, which is FAILURE_INCOMPATIBLE or
        // FAILURE for a signature mismatch depending on the Android version.
        if (text.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE") || text.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE") ||
            text.contains("signatures do not match") || text.contains("signatures are inconsistent"))
            return SIGNATURE_MISMATCH;

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                return USER_ACTION_REQUIRED;
            case PackageInstaller.STATUS_SUCCESS:
                return SUCCESS;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return CANCELLED;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return BLOCKED;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return INVALID;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return STORAGE;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                // An APK for another architecture or a newer Android version.
                return text.contains("INSTALL_FAILED_NO_MATCHING_ABIS") || text.contains("INSTALL_FAILED_OLDER_SDK") ||
                    text.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION") ? INVALID : FAILED;
            default:
                return FAILED;
        }
    }

}
