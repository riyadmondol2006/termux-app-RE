package com.termux.app.features.files;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import com.termux.R;
import com.termux.shared.termux.TermuxConstants;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns archive IO independently of the picker activity's lifecycle. */
public final class BackupService extends Service {
    static volatile String status = "Ready", inspection;
    static volatile boolean busy;
    private static final AtomicBoolean cancelled = new AtomicBoolean();
    private static final int ID = 742;
    private long lastUpdate;
    static void cancel() { cancelled.set(true); }
    public IBinder onBind(Intent intent) { return null; }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || busy) return START_NOT_STICKY;
        busy = true; cancelled.set(false); inspection = null;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("files_backup", "Backup and restore", NotificationManager.IMPORTANCE_LOW));
        status = "Starting…";
        startForeground(ID, notification());
        new Thread(() -> {
            File staged = new File(getCacheDir(), "files-restore.archive");
            try {
                Map<String, File> roots = new LinkedHashMap<>();
                roots.put("home", new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
                roots.put("usr", new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH));
                roots.put("settings", new File(getApplicationInfo().dataDir, "shared_prefs"));
                BackupArchive archive = new BackupArchive(roots, path -> {
                    if (cancelled.get()) throw new IOException("Cancelled; completed changes remain");
                    if (System.currentTimeMillis() - lastUpdate > 300) {
                        status = path; lastUpdate = System.currentTimeMillis(); manager.notify(ID, notification());
                    }
                });
                String action = intent.getAction();
                if ("backup".equals(action)) {
                    try (OutputStream out = getContentResolver().openOutputStream(intent.getData(), "wt")) {
                        if (out == null) throw new IOException("Cannot open document");
                        archive.write(out, intent.getBooleanExtra("home", true), intent.getBooleanExtra("prefix", false),
                            intent.getBooleanExtra("caches", true), getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
                    }
                    status = "Backup complete";
                } else if ("inspect".equals(action)) {
                    try (InputStream in = getContentResolver().openInputStream(intent.getData()); OutputStream out = new FileOutputStream(staged)) {
                        if (in == null) throw new IOException("Cannot open document");
                        byte[] buffer = new byte[65536]; int n; long total = 0;
                        while ((n = in.read(buffer)) != -1) {
                            if (cancelled.get()) throw new IOException("Cancelled");
                            if ((total += n) > 20L * 1024 * 1024 * 1024) throw new IOException("Archive too large");
                            out.write(buffer, 0, n); status = "Reading archive: " + total + " bytes";
                        }
                    }
                    try (InputStream in = new FileInputStream(staged)) { inspection = archive.read(in, false); }
                    status = "Validated. Review before restoring.";
                } else if ("restore".equals(action)) {
                    // Revalidate the private staged copy after process recreation before any writes.
                    try (InputStream in = new FileInputStream(staged)) { archive.read(in, false); }
                    try (InputStream in = new FileInputStream(staged)) { archive.read(in, true); }
                    staged.delete();
                    status = "Restore complete. Exit all Termux sessions and restart the app to reload settings.";
                } else throw new IOException("Unknown operation");
            } catch (Exception e) {
                status = "Failed: " + e.getMessage();
                staged.delete();
            } finally {
                busy = false;
                manager.notify(ID, notification());
                stopForeground(false);
                stopSelf(startId);
            }
        }, "files-backup").start();
        return START_NOT_STICKY;
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, BackupActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, "files_backup").setSmallIcon(R.drawable.ic_tools)
            .setContentTitle("Backup and restore").setContentText(status).setContentIntent(open)
            .setOngoing(busy).setOnlyAlertOnce(true).build();
    }
}
