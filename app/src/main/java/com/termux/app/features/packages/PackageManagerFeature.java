package com.termux.app.features.packages;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class PackageManagerFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "package_manager";
    }

    @Override
    public int getTitle() {
        return R.string.feature_packages_manager_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_packages_manager_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_packages_manager;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        PackageTerminalLauncher.setTerminalActivity(activity);
        activity.startActivity(new Intent(activity, PackageManagerActivity.class));
    }

}
