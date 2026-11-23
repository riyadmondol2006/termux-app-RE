package com.termux.app.features.system.update;

import android.content.pm.PackageInstaller;

import org.junit.Assert;
import org.junit.Test;

public class InstallStatusTest {

    @Test
    public void testBasicStatuses() {
        Assert.assertEquals(InstallStatus.USER_ACTION_REQUIRED, InstallStatus.classify(PackageInstaller.STATUS_PENDING_USER_ACTION, null));
        Assert.assertEquals(InstallStatus.SUCCESS, InstallStatus.classify(PackageInstaller.STATUS_SUCCESS, "INSTALL_SUCCEEDED"));
        Assert.assertEquals(InstallStatus.CANCELLED, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_ABORTED, "INSTALL_FAILED_ABORTED: User rejected permissions"));
        Assert.assertEquals(InstallStatus.BLOCKED, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_BLOCKED, null));
        Assert.assertEquals(InstallStatus.INVALID, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_INVALID, null));
        Assert.assertEquals(InstallStatus.STORAGE, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_STORAGE, null));
        Assert.assertEquals(InstallStatus.FAILED, InstallStatus.classify(PackageInstaller.STATUS_FAILURE, null));
        Assert.assertEquals(InstallStatus.FAILED, InstallStatus.classify(12345, null));
    }

    @Test
    public void testSignatureMismatch() {
        String message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.termux.re signatures do not match newer version; ignoring!";
        // The status of a signature mismatch differs between Android versions, the message is what counts.
        Assert.assertEquals(InstallStatus.SIGNATURE_MISMATCH, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, message));
        Assert.assertEquals(InstallStatus.SIGNATURE_MISMATCH, InstallStatus.classify(PackageInstaller.STATUS_FAILURE, message));
        Assert.assertEquals(InstallStatus.SIGNATURE_MISMATCH, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_CONFLICT,
            "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE: Package signatures do not match the previously installed version"));
    }

    @Test
    public void testIncompatibleDevice() {
        Assert.assertEquals(InstallStatus.INVALID, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            "INSTALL_FAILED_NO_MATCHING_ABIS: Failed to extract native libraries"));
        Assert.assertEquals(InstallStatus.INVALID, InstallStatus.classify(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            "INSTALL_FAILED_OLDER_SDK: Requires newer sdk version"));
    }

}
