package com.termux.app.features.system.monitor;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** The system resource monitor, see {@link SystemMonitorActivity}. */
public final class SystemMonitorFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "system_monitor";
    }

    @Override
    public int getTitle() {
        return R.string.feature_system_monitor_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_system_monitor_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_system_ic_monitor;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        SystemMonitorActivity.start(activity);
    }

}
