package com.termux.app.features.devtools;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Git status, branches, log and common actions. Listed in the Tools dialog. */
public final class GitFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "git_integration";
    }

    @Override
    public int getTitle() {
        return R.string.feature_devtools_git_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_devtools_git_summary;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        GitDialogs.showRepoPicker(activity);
    }
}
