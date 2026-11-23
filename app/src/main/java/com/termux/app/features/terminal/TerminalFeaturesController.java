package com.termux.app.features.terminal;

import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.terminal.search.TerminalSearchController;
import com.termux.terminal.TerminalSession;

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

    private final TermuxActivity mActivity;
    private final TerminalSearchController mSearchController;

    public TerminalFeaturesController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        ViewGroup container = activity.findViewById(R.id.feature_terminal_container);
        mSearchController = new TerminalSearchController(activity, container);
    }

    @NonNull
    public TerminalSearchController getSearchController() {
        return mSearchController;
    }



    /** Called when the text of {@code session} changed, while the activity is visible. */
    public void onTextChanged(@NonNull TerminalSession session) {
        mSearchController.onTextChanged(session);
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
    }

    /** @return Whether {@code item} is an item of the features, which has been handled. */
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case CONTEXT_MENU_SEARCH_ID:
                mSearchController.open(null);
                return true;
            default:
                return false;
        }
    }

}
