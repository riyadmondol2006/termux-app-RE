package com.termux.app.features.registry;

import androidx.annotation.NonNull;

import com.termux.app.features.TermuxFeature;
import com.termux.app.features.terminal.clipboard.ClipboardHistoryFeature;
import com.termux.app.features.terminal.search.TerminalSearchFeature;

import java.util.List;

/** Features for: Terminal search, split-screen terminal, enhanced copy and paste. */
public final class TerminalViewFeatures {

    private TerminalViewFeatures() {}

    /** Add the features of this area to {@code features}, in the order they should be listed. */
    public static void register(@NonNull List<TermuxFeature> features) {
        features.add(new TerminalSearchFeature());
        features.add(new ClipboardHistoryFeature());
    }

}
