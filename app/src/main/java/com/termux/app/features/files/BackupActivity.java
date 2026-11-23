package com.termux.app.features.files;

import android.content.Intent;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;

public final class BackupActivity extends AppCompatActivity {
    private CheckBox home, prefix, caches;
    private TextView status;
    private Button backup, restore, apply;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        public void run() {
            status.setText(BackupService.status);
            backup.setEnabled(!BackupService.busy); restore.setEnabled(!BackupService.busy);
            apply.setEnabled(!BackupService.busy && BackupService.inspection != null);
            handler.postDelayed(this, 500);
        }
    };
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Backup and restore");
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density); body.setPadding(pad, pad, pad, pad);
        TextView info = new TextView(this);
        info.setText("Save a compressed Termux RE archive. Settings and ~/.termux are always included. Storage symlinks are saved as links; shared storage contents are not followed. Stop commands that modify files before backing up or restoring. Archives may contain passwords and private keys.");
        body.addView(info);
        home = check(body, "Include entire home directory", getPreferences().getBoolean("home", true));
        prefix = check(body, "Include installed packages ($PREFIX)", getPreferences().getBoolean("prefix", false));
        caches = check(body, "Skip cache, .cache and tmp directories", getPreferences().getBoolean("caches", true));
        backup = button(body, "Save backup", () -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gzip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "termux-re-backup.tre.gz");
            startActivityForResult(intent, 1);
        });
        restore = button(body, "Choose archive to restore", () ->
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 2));
        apply = button(body, "Review and restore", () -> new MaterialAlertDialogBuilder(this)
            .setTitle("Replace matching files?").setMessage(BackupService.inspection +
                "\n\nExisting files with matching paths will be replaced. Other files remain. This may change settings and installed packages. Restore is not atomic; cancellation leaves completed changes. Stop terminal commands first. Restart Termux after restoring.")
            .setNegativeButton("Cancel", (d, w) -> { new File(getCacheDir(), "files-restore.archive").delete(); BackupService.inspection = null; })
            .setPositiveButton("Restore", (d, w) -> androidx.core.content.ContextCompat.startForegroundService(this, new Intent(this, BackupService.class).setAction("restore"))).show());
        button(body, "Cancel current operation", BackupService::cancel);
        status = new TextView(this); body.addView(status);
        ScrollView scroll = new ScrollView(this); scroll.addView(body); setContentView(scroll);
    }
    private CheckBox check(LinearLayout body, String text, boolean checked) {
        CheckBox box = new CheckBox(this); box.setText(text); box.setChecked(checked); body.addView(box); return box;
    }
    private Button button(LinearLayout body, String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setOnClickListener(v -> action.run()); body.addView(button); return button;
    }
    protected void onResume() { super.onResume(); handler.post(update); }
    private android.content.SharedPreferences getPreferences() { return getSharedPreferences("files_options", MODE_PRIVATE); }
    protected void onPause() {
        handler.removeCallbacks(update);
        getPreferences().edit().putBoolean("home", home.isChecked()).putBoolean("prefix", prefix.isChecked())
            .putBoolean("caches", caches.isChecked()).apply();
        super.onPause();
    }
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || BackupService.busy) return;
        Intent work = new Intent(this, BackupService.class).setAction(request == 1 ? "backup" : "inspect").setData(data.getData());
        work.putExtra("home", home.isChecked()).putExtra("prefix", prefix.isChecked()).putExtra("caches", caches.isChecked());
        androidx.core.content.ContextCompat.startForegroundService(this, work);
    }
}
