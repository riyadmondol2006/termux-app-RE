package com.termux.app.features.terminal.clipboard;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Opens the clipboard history to paste an item into the current session. */
public final class ClipboardHistoryFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "terminal_clipboard_history";
    }

    @Override
    public int getTitle() {
        return R.string.feature_terminal_clipboard_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_terminal_clipboard_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_terminal_ic_clipboard;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        ClipboardHistoryDialog.show(activity);
    }

}
