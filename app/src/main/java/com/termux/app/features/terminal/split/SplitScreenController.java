package com.termux.app.features.terminal.split;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows two sessions at once, in two panes of a {@link SplitTerminalLayout}.
 *
 * The pane with the input focus always shows the terminal view of the activity, called the primary
 * view, so the soft keyboard, the extra keys and everything else that uses the terminal view of the
 * activity work as without a split. The other pane shows a secondary view that only displays its
 * session. Moving the focus to the other pane swaps the places of the two views and the sessions
 * they show, so each session stays where it was and keeps its size. When the split is off, the
 * secondary view is removed and the terminal view of the activity is laid out as before.
 */
public final class SplitScreenController implements SplitTerminalLayout.Listener, SplitDividerView.Listener {

    private static final String LOG_TAG = "SplitScreenController";

    private static final String PREFERENCES_NAME = "feature_terminal_split";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_RATIO = "ratio";
    private static final String KEY_PRIMARY_PANE = "primary_pane";
    private static final String KEY_SECONDARY_SESSION = "secondary_session";

    /** Same limit as {@link com.termux.app.terminal.TermuxTerminalSessionActivityClient}. */
    private static final int MAX_SESSIONS = 8;

    private static final int MENU_CHOOSE_SESSION = 1;
    private static final int MENU_SWAP_PANES = 2;
    private static final int MENU_CLOSE = 3;

    /** Notified when the session of the primary view changes because the focus moved to the other pane. */
    public interface Listener {
        void onFocusedPaneChanged();
    }

    private final TermuxActivity mActivity;
    private final SplitTerminalLayout mLayout;
    private final SharedPreferences mPreferences;
    @Nullable private final Listener mListener;

    @Nullable private TerminalView mSecondaryView;
    @Nullable private SplitDividerView mDivider;

    /** Keeps the text size, typeface and background image of the secondary view the same as of the primary view. */
    private final ViewTreeObserver.OnPreDrawListener mRendererSyncListener = () -> {
        syncRenderer();
        return true;
    };
    private boolean mRendererSyncListenerAdded;

    public SplitScreenController(@NonNull TermuxActivity activity, @NonNull SplitTerminalLayout layout,
                                 @Nullable Listener listener) {
        mActivity = activity;
        mLayout = layout;
        mListener = listener;
        mPreferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mLayout.setListener(this);
        mLayout.setRatio(mPreferences.getFloat(KEY_RATIO, 0.5f));

        activity.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_START) {
                // The secondary view did not get updates while the activity was not visible.
                if (mSecondaryView != null && isSplit()) mSecondaryView.onScreenUpdated();
            } else if (event == Lifecycle.Event.ON_STOP) {
                saveState();
            }
        });
    }

    public boolean isSplit() {
        return mLayout.isSplit() && mSecondaryView != null;
    }

    @Nullable
    private TerminalView getPrimaryView() {
        return mActivity.getTerminalView();
    }

    /** Get the session shown in the pane without focus, or {@code null} if not split. */
    @Nullable
    public TerminalSession getSecondarySession() {
        return isSplit() ? mSecondaryView.getCurrentSession() : null;
    }



    /**
     * Split the terminal and show {@code session} in the new pane.
     *
     * @param focusNewPane Whether to move the focus to the new pane.
     */
    @MainThread
    public void split(@NonNull TerminalSession session, boolean focusNewPane) {
        split(session, focusNewPane, 0);
    }

    /**
     * @param primaryPane The pane of the primary view if the focus does not move to the new pane,
     *                    0 for the top or left pane.
     */
    private void split(@NonNull TerminalSession session, boolean focusNewPane, int primaryPane) {
        TerminalView primaryView = getPrimaryView();
        if (primaryView == null) return;
        TerminalSession currentSession = primaryView.getCurrentSession();
        if (currentSession == null || session == currentSession) return;
        if (isSplit()) {
            setSecondarySession(session);
            if (focusNewPane) focusSecondaryPane(false);
            return;
        }

        TerminalView secondaryView = getOrCreateSecondaryView();
        if (focusNewPane) {
            // The current session stays in the first pane, shown by the secondary view, and the
            // new session is shown in the second pane by the primary view that has the focus.
            primaryView.stopTextSelectionMode();
            primaryView.attachSession(null);
            mLayout.setPrimaryPane(1);
            mLayout.split(secondaryView, getOrCreateDivider());
            secondaryView.attachSession(currentSession);
            setPrimarySession(session);
        } else {
            mLayout.setPrimaryPane(primaryPane);
            mLayout.split(secondaryView, getOrCreateDivider());
            secondaryView.attachSession(session);
        }
        secondaryView.onScreenUpdated();
        addRendererSyncListener();
        saveState();
        if (focusNewPane && mListener != null) mListener.onFocusedPaneChanged();
    }

    /** Remove the split, the session of the focused pane stays. */
    @MainThread
    public void unsplit() {
        if (!mLayout.isSplit()) return;
        if (mSecondaryView != null) mSecondaryView.attachSession(null);
        removeRendererSyncListener();
        mLayout.unsplit();
        mLayout.setPrimaryPane(0);
        saveState();
    }

    /** Show {@code session} in the pane without focus. */
    @MainThread
    public void setSecondarySession(@NonNull TerminalSession session) {
        if (!isSplit()) return;
        TerminalView primaryView = getPrimaryView();
        if (primaryView != null && primaryView.getCurrentSession() == session) return;
        mSecondaryView.attachSession(session);
        mSecondaryView.onScreenUpdated();
        saveState();
    }

    /**
     * Move the input focus to the pane without focus. The two views swap places and sessions, so
     * that the primary view, which gets the input, is in that pane.
     *
     * @param showSoftKeyboard Whether to show the soft keyboard, like after tapping the pane.
     */
    @MainThread
    public void focusSecondaryPane(boolean showSoftKeyboard) {
        TerminalView primaryView = getPrimaryView();
        if (!isSplit() || primaryView == null) return;
        TerminalSession focusedSession = primaryView.getCurrentSession();
        TerminalSession otherSession = mSecondaryView.getCurrentSession();
        if (otherSession == null) return;

        primaryView.stopTextSelectionMode();
        // Detach the sessions first, so they are not resized while the views move.
        primaryView.attachSession(null);
        mSecondaryView.attachSession(null);
        mLayout.setPrimaryPane(1 - mLayout.getPrimaryPane());
        if (focusedSession != null) mSecondaryView.attachSession(focusedSession);
        setPrimarySession(otherSession);
        mSecondaryView.onScreenUpdated();
        saveState();

        if (showSoftKeyboard && !KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity))
            KeyboardUtils.showSoftKeyboard(mActivity, primaryView);
        if (mListener != null) mListener.onFocusedPaneChanged();
    }

    /** Swap the places of the panes, keeping the focus on the same session. */
    @MainThread
    public void swapPanes() {
        if (!isSplit()) return;
        mLayout.setPrimaryPane(1 - mLayout.getPrimaryPane());
        saveState();
    }

    /** Show the session in the primary view, like selecting it in the drawer. */
    private void setPrimarySession(@NonNull TerminalSession session) {
        TerminalView primaryView = getPrimaryView();
        if (primaryView == null) return;
        // Attach first so that no "session changed" toast is shown, then let the client update the
        // drawer selection and background color.
        primaryView.attachSession(session);
        if (mActivity.getTermuxTerminalSessionClient() != null)
            mActivity.getTermuxTerminalSessionClient().setCurrentSession(session);
        // The cursor blinker of the activity's view now runs for this session.
        if (mActivity.getTermuxTerminalViewClient() != null)
            mActivity.getTermuxTerminalViewClient().setTerminalCursorBlinkerState(mActivity.isVisible());
    }



    /** Called when the text of {@code session} changed, while the activity is visible. */
    public void onTextChanged(@NonNull TerminalSession session) {
        if (isSplit() && mSecondaryView.getCurrentSession() == session) mSecondaryView.onScreenUpdated();
    }

    /**
     * Called before {@code session} is set as the current session, like when selected in the drawer.
     *
     * @return Whether it was handled by moving the focus to the pane that shows the session.
     */
    public boolean onSetCurrentSession(@Nullable TerminalSession session) {
        if (!isSplit() || session == null || session != mSecondaryView.getCurrentSession()) return false;

        TerminalView primaryView = getPrimaryView();
        TerminalSession focusedSession = primaryView != null ? primaryView.getCurrentSession() : null;
        if (focusedSession != null && isSessionAlive(focusedSession)) {
            focusSecondaryPane(false);
            return true;
        }

        // The session of the focused pane was removed and the other pane's session takes its place,
        // so the other pane needs another session.
        TerminalSession replacement = findOtherSession(session);
        if (replacement != null) setSecondarySession(replacement);
        else unsplit();
        return false;
    }

    /** Called when sessions were added, removed or renamed. */
    public void onSessionsChanged() {
        if (!isSplit()) return;
        TerminalView primaryView = getPrimaryView();
        TerminalSession focusedSession = primaryView != null ? primaryView.getCurrentSession() : null;
        TerminalSession otherSession = mSecondaryView.getCurrentSession();
        if (otherSession != null && otherSession != focusedSession && isSessionAlive(otherSession)) return;

        TerminalSession replacement = findOtherSession(focusedSession);
        if (replacement != null) setSecondarySession(replacement);
        else unsplit();
    }

    /** Called when the activity connected to the service, to restore the split. */
    public void onServiceConnected() {
        if (!mPreferences.getBoolean(KEY_ENABLED, false) || isSplit()) return;
        TermuxService service = mActivity.getTermuxService();
        TerminalView primaryView = getPrimaryView();
        if (service == null || primaryView == null || primaryView.getCurrentSession() == null) return;

        TerminalSession session = null;
        String handle = mPreferences.getString(KEY_SECONDARY_SESSION, null);
        if (handle != null) session = service.getTerminalSessionForHandle(handle);
        if (session == null || session == primaryView.getCurrentSession())
            session = findOtherSession(primaryView.getCurrentSession());
        if (session == null) {
            saveState();
            return;
        }

        Logger.logDebug(LOG_TAG, "Restoring the split screen");
        split(session, false, mPreferences.getInt(KEY_PRIMARY_PANE, 0));
    }



    @Override
    public void onSecondaryPaneTapped() {
        focusSecondaryPane(true);
    }

    @Override
    public void onDividerDragged(float ratio) {
        mLayout.setRatio(ratio);
        saveState();
    }

    @Override
    public void onDividerClicked(@NonNull View divider) {
        PopupMenu popupMenu = new PopupMenu(mActivity, divider);
        addOptions(popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(item -> onOptionSelected(item.getItemId()));
        popupMenu.show();
    }

    private void addOptions(@NonNull Menu menu) {
        menu.add(Menu.NONE, MENU_CHOOSE_SESSION, Menu.NONE, R.string.feature_terminal_split_choose_session);
        menu.add(Menu.NONE, MENU_SWAP_PANES, Menu.NONE, R.string.feature_terminal_split_swap);
        menu.add(Menu.NONE, MENU_CLOSE, Menu.NONE, R.string.feature_terminal_split_close);
    }

    private boolean onOptionSelected(int id) {
        switch (id) {
            case MENU_CHOOSE_SESSION:
                showSessionPicker();
                return true;
            case MENU_SWAP_PANES:
                swapPanes();
                return true;
            case MENU_CLOSE:
                unsplit();
                return true;
            default:
                return false;
        }
    }

    /** Split the terminal, or show the options if already split. Called from the Tools dialog. */
    public void open() {
        if (isSplit()) {
            CharSequence[] options = {
                mActivity.getString(R.string.feature_terminal_split_choose_session),
                mActivity.getString(R.string.feature_terminal_split_focus_other),
                mActivity.getString(R.string.feature_terminal_split_swap),
                mActivity.getString(R.string.feature_terminal_split_close)};
            new MaterialAlertDialogBuilder(mActivity)
                .setTitle(R.string.feature_terminal_split_title)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showSessionPicker(); break;
                        case 1: focusSecondaryPane(false); break;
                        case 2: swapPanes(); break;
                        case 3: unsplit(); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        TerminalView primaryView = getPrimaryView();
        if (primaryView == null || primaryView.getCurrentSession() == null) return;
        if (getOtherSessions(primaryView.getCurrentSession()).isEmpty()) {
            // The only session, so show a new one next to it.
            TerminalSession newSession = createSession();
            if (newSession != null) split(newSession, true);
        } else {
            showSessionPicker();
        }
    }

    /** Let the user choose the session of the pane without focus, or a new session. */
    private void showSessionPicker() {
        TerminalView primaryView = getPrimaryView();
        if (primaryView == null || primaryView.getCurrentSession() == null) return;
        final TerminalSession focusedSession = primaryView.getCurrentSession();
        final List<TerminalSession> sessions = getOtherSessions(focusedSession);
        TermuxService service = mActivity.getTermuxService();

        List<CharSequence> names = new ArrayList<>();
        for (TerminalSession session : sessions)
            names.add(getSessionName(service, session));
        names.add(mActivity.getString(R.string.feature_terminal_split_new_session));

        new MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.feature_terminal_split_choose_session)
            .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                if (which < sessions.size()) {
                    split(sessions.get(which), false);
                } else {
                    TerminalSession newSession = createSession();
                    if (newSession != null) split(newSession, true);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private static String getSessionName(@Nullable TermuxService service, @NonNull TerminalSession session) {
        int index = service != null ? service.getIndexOfSession(session) : -1;
        StringBuilder name = new StringBuilder("[").append(index + 1).append("]");
        if (!TextUtils.isEmpty(session.mSessionName)) name.append(' ').append(session.mSessionName);
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) name.append(TextUtils.isEmpty(session.mSessionName) ? " " : " - ").append(title);
        return name.toString();
    }

    /** Create a new session in the current directory, without showing it. */
    @Nullable
    private TerminalSession createSession() {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            new MaterialAlertDialogBuilder(mActivity).setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached).setPositiveButton(android.R.string.ok, null).show();
            return null;
        }

        TerminalSession currentSession = mActivity.getCurrentSession();
        String workingDirectory = currentSession != null ? currentSession.getCwd() :
            mActivity.getProperties().getDefaultWorkingDirectory();
        TermuxSession termuxSession = service.createTermuxSession(null, null, null, workingDirectory, false, null);
        if (termuxSession == null) return null;
        mActivity.termuxSessionListNotifyUpdated();
        return termuxSession.getTerminalSession();
    }



    private boolean isSessionAlive(@NonNull TerminalSession session) {
        TermuxService service = mActivity.getTermuxService();
        return service != null && service.getIndexOfSession(session) >= 0;
    }

    /** Get the sessions other than {@code exclude}, in the order of the drawer. */
    @NonNull
    private List<TerminalSession> getOtherSessions(@Nullable TerminalSession exclude) {
        List<TerminalSession> sessions = new ArrayList<>();
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return sessions;
        for (TermuxSession termuxSession : new ArrayList<>(service.getTermuxSessions())) {
            TerminalSession session = termuxSession.getTerminalSession();
            if (session != exclude) sessions.add(session);
        }
        return sessions;
    }

    /** Find a session to show in the pane without focus, the one after {@code exclude} if possible. */
    @Nullable
    private TerminalSession findOtherSession(@Nullable TerminalSession exclude) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        List<TerminalSession> sessions = getOtherSessions(exclude);
        if (sessions.isEmpty()) return null;
        int index = exclude != null ? service.getIndexOfSession(exclude) : -1;
        // The session after the excluded one in the drawer, or the last one.
        return index >= 0 && index < sessions.size() ? sessions.get(index) : sessions.get(sessions.size() - 1);
    }

    @NonNull
    private TerminalView getOrCreateSecondaryView() {
        if (mSecondaryView == null) {
            mSecondaryView = (TerminalView) LayoutInflater.from(mActivity).inflate(R.layout.feature_terminal_split_pane, mLayout, false);
            SplitPaneViewClient client = new SplitPaneViewClient(mActivity, this);
            client.setView(mSecondaryView);
            mSecondaryView.setTerminalViewClient(client);
        }
        TerminalView primaryView = getPrimaryView();
        if (primaryView != null && primaryView.mRenderer != null) mSecondaryView.setRenderer(primaryView.mRenderer);
        return mSecondaryView;
    }

    @NonNull
    private SplitDividerView getOrCreateDivider() {
        if (mDivider == null) mDivider = new SplitDividerView(mActivity, this);
        return mDivider;
    }

    private void syncRenderer() {
        TerminalView primaryView = getPrimaryView();
        if (!isSplit() || primaryView == null) return;
        if (primaryView.mRenderer != null && mSecondaryView.mRenderer != primaryView.mRenderer)
            mSecondaryView.setRenderer(primaryView.mRenderer);
        // Show the Theme Studio background image in both panes.
        if (mSecondaryView.getTerminalBackgroundImage() != primaryView.getTerminalBackgroundImage()
            || mSecondaryView.getTerminalBackgroundAlpha() != primaryView.getTerminalBackgroundAlpha())
            mSecondaryView.setTerminalBackgroundImage(primaryView.getTerminalBackgroundImage(), primaryView.getTerminalBackgroundAlpha());
    }

    private void addRendererSyncListener() {
        if (mRendererSyncListenerAdded) return;
        mLayout.getViewTreeObserver().addOnPreDrawListener(mRendererSyncListener);
        mRendererSyncListenerAdded = true;
    }

    private void removeRendererSyncListener() {
        if (!mRendererSyncListenerAdded) return;
        mLayout.getViewTreeObserver().removeOnPreDrawListener(mRendererSyncListener);
        mRendererSyncListenerAdded = false;
    }

    private void saveState() {
        TerminalSession secondarySession = getSecondarySession();
        mPreferences.edit()
            .putBoolean(KEY_ENABLED, isSplit())
            .putFloat(KEY_RATIO, mLayout.getRatio())
            .putInt(KEY_PRIMARY_PANE, mLayout.getPrimaryPane())
            .putString(KEY_SECONDARY_SESSION, secondarySession != null ? secondarySession.mHandle : null)
            .apply();
    }

}
