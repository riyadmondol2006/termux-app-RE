package com.termux.app.features.system.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs an APK with a {@link PackageInstaller} session. The result is sent to
 * {@link InstallResultReceiver}, which hands it to {@link UpdateManager}.
 */
final class ApkInstaller {

    private static final String SESSION_FILE_NAME = "base.apk";

    private ApkInstaller() {}

    /**
     * Write {@code apk} to a new install session of this app and commit it. Returns after the
     * session was committed, the result of the install comes later. Throws if the session could not
     * be created or written, in which case it is abandoned.
     */
    @WorkerThread
    static void install(@NonNull Context context, @NonNull File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apk.length());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Don't ask for a confirmation if the system allows to update this app without it.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        boolean committed = false;
        try {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite(SESSION_FILE_NAME, 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                session.fsync(out);
            }
            session.commit(createStatusReceiver(context, sessionId));
            committed = true;
        } finally {
            session.close();
            if (!committed) installer.abandonSession(sessionId);
        }
    }

    @NonNull
    private static IntentSender createStatusReceiver(@NonNull Context context, int sessionId) {
        Intent intent = new Intent(context, InstallResultReceiver.class).setAction(InstallResultReceiver.ACTION_INSTALL_STATUS);
        // The system adds the status to the intent, so it has to be mutable on Android 12+.
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(context, sessionId, intent, flags).getIntentSender();
    }

}
