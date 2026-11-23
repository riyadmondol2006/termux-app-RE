package com.termux.view.textselection;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.view.TerminalView;

/**
 * Adds actions to the floating toolbar shown while selecting text in a {@link TerminalView}, after
 * its own copy, paste and select all actions. Set with
 * {@link TerminalView#setTextSelectionActionProvider(TextSelectionActionProvider)}.
 */
public interface TextSelectionActionProvider {

    /** The first item id and order that can be used for the added actions. */
    int FIRST_ACTION_ID = 100;

    /** Add the actions to {@code menu}, with ids and orders from {@link #FIRST_ACTION_ID}. */
    void onCreateTextSelectionActions(@NonNull TerminalView terminalView, @NonNull Menu menu);

    /**
     * Called when an added action is clicked. Text selection is stopped afterwards.
     *
     * @param selectedText The selected text.
     * @return Whether the action was handled.
     */
    boolean onTextSelectionActionClicked(@NonNull TerminalView terminalView, @NonNull MenuItem item,
                                         @Nullable String selectedText);

}
