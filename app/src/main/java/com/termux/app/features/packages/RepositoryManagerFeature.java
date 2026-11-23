package com.termux.app.features.packages;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class RepositoryManagerFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "package_repositories";
    }

    @Override
    public int getTitle() {
        return R.string.feature_packages_repositories_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_packages_repositories_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_packages_repository;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        PackageTerminalLauncher.setTerminalActivity(activity);
        activity.startActivity(new Intent(activity, RepositoryManagerActivity.class));
    }

}
