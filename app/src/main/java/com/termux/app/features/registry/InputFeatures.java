package com.termux.app.features.registry;

import androidx.annotation.NonNull;

import com.termux.app.features.TermuxFeature;

import java.util.List;

/**
 * Features for: Command palette, quick actions, customizable toolbar, custom keyboard editor,
 * command history.
 */
public final class InputFeatures {

    private InputFeatures() {}

    /** Add the features of this area to {@code features}, in the order they should be listed. */
    public static void register(@NonNull List<TermuxFeature> features) {
        features.add(
                new com.termux.app.features.input.InputFeature(
                        "palette",
                        com.termux.R.string.feature_input_palette,
                        com.termux.R.string.feature_input_palette_summary));
        features.add(
                new com.termux.app.features.input.InputFeature(
                        "quick",
                        com.termux.R.string.feature_input_quick,
                        com.termux.R.string.feature_input_quick_summary));
        features.add(
                new com.termux.app.features.input.InputFeature(
                        "toolbar",
                        com.termux.R.string.feature_input_toolbar,
                        com.termux.R.string.feature_input_toolbar_summary));
        features.add(
                new com.termux.app.features.input.InputFeature(
                        "keyboard",
                        com.termux.R.string.feature_input_keyboard,
                        com.termux.R.string.feature_input_keyboard_summary));
        features.add(
                new com.termux.app.features.input.InputFeature(
                        "history",
                        com.termux.R.string.feature_input_history,
                        com.termux.R.string.feature_input_history_summary));
    }
}
