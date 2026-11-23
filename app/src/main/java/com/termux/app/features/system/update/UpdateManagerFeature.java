package com.termux.app.features.system.update;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** The update manager, see {@link UpdateManagerActivity}. */
public final class UpdateManagerFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "system_update_manager";
    }

    @Override
    public int getTitle() {
        return R.string.feature_system_update_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_system_update_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_system_ic_update;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        UpdateManagerActivity.start(activity);
    }

}
