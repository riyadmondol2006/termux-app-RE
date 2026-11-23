package com.termux.app.features.files;

import android.content.Intent;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class BackupFeature extends TermuxFeature {
    public String getId() { return "files_backup"; }
    public int getTitle() { return R.string.feature_files_backup; }
    public int getSummary() { return R.string.feature_files_backup_summary; }
    public void open(TermuxActivity activity) { activity.startActivity(new Intent(activity, BackupActivity.class)); }
}
