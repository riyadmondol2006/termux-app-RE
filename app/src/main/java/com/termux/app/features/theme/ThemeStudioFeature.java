package com.termux.app.features.theme;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

public final class ThemeStudioFeature extends TermuxFeature {
    @NonNull @Override public String getId() { return "theme_studio"; }
    @Override public int getTitle() { return R.string.feature_theme_studio_title; }
    @Override public int getSummary() { return R.string.feature_theme_studio_summary; }
    @Override public int getIcon() { return R.drawable.ic_tools; }
    @Override public void open(@NonNull TermuxActivity activity) { ThemeStudioActivity.start(activity); }
}
