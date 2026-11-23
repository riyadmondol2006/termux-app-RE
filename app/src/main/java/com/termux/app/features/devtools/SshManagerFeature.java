package com.termux.app.features.devtools;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Saved SSH connections with key management. Listed in the Tools dialog. */
public final class SshManagerFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "ssh_manager";
    }

    @Override
    public int getTitle() {
        return R.string.feature_devtools_ssh_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_devtools_ssh_summary;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        SshManagerDialogs.showConnections(activity);
    }
}
