package com.termux.app.features.terminal.search;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Opens the terminal search bar. */
public final class TerminalSearchFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "terminal_search";
    }

    @Override
    public int getTitle() {
        return R.string.feature_terminal_search_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_terminal_search_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_terminal_ic_search;
    }

    @Override
    public boolean isAvailable(@NonNull TermuxActivity activity) {
        return activity.getCurrentSession() != null && activity.getTerminalFeaturesController() != null;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        if (activity.getTerminalFeaturesController() != null)
            activity.getTerminalFeaturesController().getSearchController().open(null);
    }

}
