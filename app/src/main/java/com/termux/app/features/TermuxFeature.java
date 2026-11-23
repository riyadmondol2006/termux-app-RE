package com.termux.app.features;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.termux.R;
import com.termux.app.TermuxActivity;

/**
 * A feature of the app that is listed in the Tools dialog ({@link FeaturesDialog}) and, through
 * {@link FeatureRegistry}, can be offered by other entry points such as a command palette.
 *
 * Features are registered in the {@code com.termux.app.features.registry} classes, one class per
 * area so features of different areas can be added independently.
 */
public abstract class TermuxFeature {

    /** A stable id, like "terminal_search". Used for preferences and as an action id. */
    @NonNull
    public abstract String getId();

    /** The title shown in lists. */
    @StringRes
    public abstract int getTitle();

    /** A short description shown below the title. */
    @StringRes
    public abstract int getSummary();

    /** The icon shown in lists. */
    @DrawableRes
    public int getIcon() {
        return R.drawable.ic_tools;
    }

    /** Whether the feature can be opened now, for example only if a session exists. */
    public boolean isAvailable(@NonNull TermuxActivity activity) {
        return true;
    }

    /**
     * Open the feature from the terminal activity, for example by showing a dialog, a panel in the
     * activity, or by starting another activity.
     */
    public abstract void open(@NonNull TermuxActivity activity);

}
