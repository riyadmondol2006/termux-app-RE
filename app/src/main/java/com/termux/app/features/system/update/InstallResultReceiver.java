package com.termux.app.features.system.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives the status of the install session of {@link ApkInstaller}. */
public final class InstallResultReceiver extends BroadcastReceiver {

    static final String ACTION_INSTALL_STATUS = "com.termux.re.system.update.INSTALL_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction())) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        Intent confirmIntent = null;
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Object extra = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (extra instanceof Intent) confirmIntent = (Intent) extra;
        }
        UpdateManager.getInstance(context).onInstallStatus(InstallStatus.classify(status, message), message, confirmIntent);
    }

}
