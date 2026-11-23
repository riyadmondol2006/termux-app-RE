package com.termux.app.features.files;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class FileManagerFeature extends TermuxFeature {
    public String getId() { return "files_manager"; }
    public int getTitle() { return R.string.feature_files_manager; }
    public int getSummary() { return R.string.feature_files_manager_summary; }
    public void open(TermuxActivity activity) { new FileBrowser(activity).show(); }
}
