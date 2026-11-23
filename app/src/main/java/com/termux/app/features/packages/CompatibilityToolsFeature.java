package com.termux.app.features.packages;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class CompatibilityToolsFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "package_compatibility";
    }

    @Override
    public int getTitle() {
        return R.string.feature_packages_compatibility_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_packages_compatibility_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_packages_compatibility;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        PackageTerminalLauncher.setTerminalActivity(activity);
        activity.startActivity(new Intent(activity, CompatibilityToolsActivity.class));
    }

}
