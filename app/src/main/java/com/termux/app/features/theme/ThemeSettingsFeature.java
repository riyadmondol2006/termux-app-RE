package com.termux.app.features.theme;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.features.TermuxFeature;

public final class ThemeSettingsFeature extends TermuxFeature {
    @NonNull @Override public String getId() { return "theme_settings"; }
    @Override public int getTitle() { return R.string.feature_theme_settings_title; }
    @Override public int getSummary() { return R.string.feature_theme_settings_summary; }
    @Override public int getIcon() { return R.drawable.ic_settings; }
    @Override public void open(@NonNull TermuxActivity activity) {
        activity.startActivity(new Intent(activity, SettingsActivity.class));
    }
}
