package com.termux.app.features.sessions;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Tools entries of the session features that open their settings or screens. */
public final class SessionFeatureEntries {

    private SessionFeatureEntries() {}

    /** Opens the terminal profiles manager. */
    public static final class Profiles extends TermuxFeature {
        @NonNull @Override public String getId() { return "terminal_profiles"; }
        @Override public int getTitle() { return R.string.feature_sessions_profiles_title; }
        @Override public int getSummary() { return R.string.feature_sessions_profiles_summary; }
        @Override public int getIcon() { return R.drawable.feature_sessions_ic_profiles; }
        @Override public void open(@NonNull TermuxActivity activity) { ProfilesActivity.start(activity); }
    }

    /** Opens the startup automation settings. */
    public static final class StartupAutomation extends TermuxFeature {
        @NonNull @Override public String getId() { return "startup_automation"; }
        @Override public int getTitle() { return R.string.feature_sessions_startup_title; }
        @Override public int getSummary() { return R.string.feature_sessions_startup_summary; }
        @Override public int getIcon() { return R.drawable.feature_sessions_ic_startup; }
        @Override public void open(@NonNull TermuxActivity activity) { SessionSettingsActivity.start(activity, "category_startup"); }
    }

}
