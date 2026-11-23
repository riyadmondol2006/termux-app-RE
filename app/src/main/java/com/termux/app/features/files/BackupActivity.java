package com.termux.app.features.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;
import java.io.File;

public final class BackupActivity extends AppCompatActivity {
    private MaterialSwitch home, prefix, caches;
    private TextView status;
    private LinearProgressIndicator progress;
    private MaterialButton backup, restore, apply, cancel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        public void run() {
            status.setText(BackupService.status);
            boolean busy = BackupService.busy;
            backup.setEnabled(!busy); restore.setEnabled(!busy);
            apply.setEnabled(!busy && BackupService.inspection != null);
            cancel.setEnabled(busy);
            progress.setVisibility(busy ? View.VISIBLE : View.GONE);
            handler.postDelayed(this, 500);
        }
    };

    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_files_backup);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.feature_files_backup_title);

        home = findViewById(R.id.feature_files_backup_home);
        prefix = findViewById(R.id.feature_files_backup_prefix);
        caches = findViewById(R.id.feature_files_backup_caches);
        status = findViewById(R.id.feature_files_backup_status);
        progress = findViewById(R.id.feature_files_backup_progress);
        backup = findViewById(R.id.feature_files_backup_save);
        restore = findViewById(R.id.feature_files_backup_choose);
        apply = findViewById(R.id.feature_files_backup_review);
        cancel = findViewById(R.id.feature_files_backup_cancel);

        SharedPreferences options = getPreferences();
        home.setChecked(options.getBoolean("home", true));
        prefix.setChecked(options.getBoolean("prefix", false));
        caches.setChecked(options.getBoolean("caches", true));

        backup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gzip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "termux-re-backup.tre.gz");
            startActivityForResult(intent, 1);
        });
        restore.setOnClickListener(v ->
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 2));
        apply.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.feature_files_backup_confirm_title)
            .setMessage(getString(R.string.feature_files_backup_confirm_message, BackupService.inspection))
            .setNegativeButton(R.string.feature_files_cancel, (d, w) -> { new File(getCacheDir(), "files-restore.archive").delete(); BackupService.inspection = null; })
            .setPositiveButton(R.string.feature_files_backup_restore, (d, w) -> ContextCompat.startForegroundService(this, new Intent(this, BackupService.class).setAction("restore"))).show());
        cancel.setOnClickListener(v -> BackupService.cancel());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    protected void onResume() { super.onResume(); handler.post(update); }
    private SharedPreferences getPreferences() { return getSharedPreferences("files_options", MODE_PRIVATE); }
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
        ContextCompat.startForegroundService(this, work);
    }
}
