package com.termux.app.features.terminal.split;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Splits the terminal into two panes, or shows the split screen options if already split. */
public final class SplitScreenFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "terminal_split_screen";
    }

    @Override
    public int getTitle() {
        return R.string.feature_terminal_split_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_terminal_split_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_terminal_ic_split;
    }

    @Override
    public boolean isAvailable(@NonNull TermuxActivity activity) {
        return activity.getCurrentSession() != null && activity.getTermuxService() != null &&
            activity.getTerminalFeaturesController() != null;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        if (activity.getTerminalFeaturesController() != null)
            activity.getTerminalFeaturesController().getSplitScreenController().open();
    }

}
