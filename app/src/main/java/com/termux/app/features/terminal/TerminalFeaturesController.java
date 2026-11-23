package com.termux.app.features.terminal;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.terminal.clipboard.ClipboardHistory;
import com.termux.app.features.terminal.clipboard.ClipboardHistoryDialog;
import com.termux.app.features.terminal.search.TerminalSearchController;
import com.termux.app.features.terminal.split.SplitScreenController;
import com.termux.app.features.terminal.split.SplitTerminalLayout;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.view.textselection.TextSelectionActionProvider;

/**
 * The terminal view features of a {@link TermuxActivity}: terminal search, split screen and the
 * enhanced copy and paste.
 *
 * The activity and its terminal clients call the {@code on*} hook methods. They do nothing that
 * changes the behaviour of the single terminal view while the features are not used.
 */
public final class TerminalFeaturesController {

    /** The ids of the items added to the context menu of the terminal, above the ids used by the activity. */
    private static final int CONTEXT_MENU_SEARCH_ID = 1000;
    private static final int CONTEXT_MENU_PASTE_FROM_HISTORY_ID = 1001;

    /** The ids of the actions added to the text selection toolbar. */
    private static final int SELECTION_ACTION_SEARCH_ID = TextSelectionActionProvider.FIRST_ACTION_ID;
    private static final int SELECTION_ACTION_PASTE_FROM_HISTORY_ID = TextSelectionActionProvider.FIRST_ACTION_ID + 1;

    private final TermuxActivity mActivity;
    private final TerminalSearchController mSearchController;
    private final SplitScreenController mSplitScreenController;

    public TerminalFeaturesController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        ViewGroup container = activity.findViewById(R.id.feature_terminal_container);
        mSearchController = new TerminalSearchController(activity, container);
        SplitTerminalLayout splitLayout = activity.findViewById(R.id.feature_terminal_split_layout);
        // The search follows the focused pane.
        mSplitScreenController = new SplitScreenController(activity, splitLayout, mSearchController::onTerminalViewChanged);

        // Keep a history of the text copied and pasted in the app.
        ClipboardHistory.install(activity);

        activity.getTerminalView().setTextSelectionActionProvider(new SelectionActionProvider());
    }

    @NonNull
    public TerminalSearchController getSearchController() {
        return mSearchController;
    }

    @NonNull
    public SplitScreenController getSplitScreenController() {
        return mSplitScreenController;
    }



    /** Called when the text of {@code session} changed, while the activity is visible. */
    public void onTextChanged(@NonNull TerminalSession session) {
        mSplitScreenController.onTextChanged(session);
        mSearchController.onTextChanged(session);
    }

    /**
     * Called before {@code session} is set as the current session of the terminal view.
     *
     * @return Whether it was handled, like by moving the focus to the split screen pane showing it.
     */
    public boolean onSetCurrentSession(@Nullable TerminalSession session) {
        return mSplitScreenController.onSetCurrentSession(session);
    }

    /** Called when sessions were added, removed or renamed. */
    public void onSessionsChanged() {
        mSplitScreenController.onSessionsChanged();
    }

    /** Called when the activity connected to the service and the current session is set. */
    public void onServiceConnected() {
        mSplitScreenController.onServiceConnected();
    }

    /**
     * Called for every key event of the activity before it is dispatched, for the keyboard shortcuts
     * of the features.
     *
     * @return Whether the event was consumed.
     */
    public boolean onDispatchKeyEvent(@NonNull KeyEvent event) {
        if (mActivity.getProperties() == null || mActivity.getProperties().areHardwareKeyboardShortcutsDisabled())
            return false;

        // Ctrl+Shift+F: search.
        if (event.getKeyCode() == KeyEvent.KEYCODE_F && event.isCtrlPressed() && event.isShiftPressed() && !event.isAltPressed()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                mSearchController.toggleOrFocus();
            return true;
        }

        // Ctrl+Shift+O: type in the other pane of the split screen.
        if (event.getKeyCode() == KeyEvent.KEYCODE_O && event.isCtrlPressed() && event.isShiftPressed() && !event.isAltPressed()
            && mSplitScreenController.isSplit()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                mSplitScreenController.focusSecondaryPane(false);
            return true;
        }
        return false;
    }

    /**
     * Called when the back button is pressed.
     *
     * @return Whether it was handled, like by closing the search bar.
     */
    public boolean onBackPressed() {
        if (mSearchController.isOpen()) {
            mSearchController.close();
            return true;
        }
        return false;
    }

    /** Add the items of the features to the context menu of the terminal. */
    public void onCreateContextMenu(@NonNull Menu menu) {
        menu.add(Menu.NONE, CONTEXT_MENU_SEARCH_ID, Menu.NONE, R.string.feature_terminal_search_action);
        menu.add(Menu.NONE, CONTEXT_MENU_PASTE_FROM_HISTORY_ID, Menu.NONE, R.string.feature_terminal_clipboard_paste_action);
    }

    /** @return Whether {@code item} is an item of the features, which has been handled. */
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case CONTEXT_MENU_SEARCH_ID:
                mSearchController.open(null);
                return true;
            case CONTEXT_MENU_PASTE_FROM_HISTORY_ID:
                ClipboardHistoryDialog.show(mActivity);
                return true;
            default:
                return false;
        }
    }




    /** Adds search and paste from history to the text selection toolbar. */
    private final class SelectionActionProvider implements TextSelectionActionProvider {

        @Override
        public void onCreateTextSelectionActions(@NonNull TerminalView terminalView, @NonNull Menu menu) {
            menu.add(Menu.NONE, SELECTION_ACTION_SEARCH_ID, SELECTION_ACTION_SEARCH_ID, R.string.feature_terminal_clipboard_search_action)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
            menu.add(Menu.NONE, SELECTION_ACTION_PASTE_FROM_HISTORY_ID, SELECTION_ACTION_PASTE_FROM_HISTORY_ID,
                R.string.feature_terminal_clipboard_paste_action);
        }

        @Override
        public boolean onTextSelectionActionClicked(@NonNull TerminalView terminalView, @NonNull MenuItem item,
                                                    @Nullable String selectedText) {
            switch (item.getItemId()) {
                case SELECTION_ACTION_SEARCH_ID:
                    mSearchController.open(getSearchText(selectedText));
                    return true;
                case SELECTION_ACTION_PASTE_FROM_HISTORY_ID:
                    ClipboardHistoryDialog.show(mActivity);
                    return true;
                default:
                    return false;
            }
        }

    }

    /** Get the text to search for from selected text, its first line, since a match can't span lines. */
    @Nullable
    static String getSearchText(@Nullable String selectedText) {
        if (selectedText == null) return null;
        String text = selectedText.trim();
        int newline = text.indexOf('\n');
        if (newline >= 0) text = text.substring(0, newline).trim();
        return text.isEmpty() ? null : text;
    }

}
