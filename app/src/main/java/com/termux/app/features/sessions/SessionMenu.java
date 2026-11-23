package com.termux.app.features.sessions;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/** The actions for a session, shown when it is long pressed in the drawer. */
public final class SessionMenu {

    private SessionMenu() {}

    public static void show(@NonNull TermuxActivity activity, @NonNull TerminalSession session) {
        SessionInfo info = SessionRegistry.get(session);
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        items.add(activity.getString(R.string.feature_sessions_action_rename_session));
        actions.add(() -> activity.getTermuxTerminalSessionClient().renameSession(session));

        items.add(activity.getString(info.pinned ? R.string.feature_sessions_action_unpin_session : R.string.feature_sessions_action_pin_session));
        actions.add(() -> SessionWorkspaces.togglePinned(activity, session));

        items.add(activity.getString(R.string.feature_sessions_action_move_session));
        actions.add(() -> WorkspaceDialogs.showMoveSession(activity, session));

        if (info.restoredTranscript != null) {
            items.add(activity.getString(R.string.feature_sessions_action_view_restored_history));
            actions.add(() -> RestoredHistoryDialog.show(activity, session));
        }

        items.add(activity.getString(R.string.feature_sessions_action_close_session));
        actions.add(() -> confirmCloseSession(activity, session));

        String title = TextUtils.isEmpty(session.mSessionName) ? session.getTitle() : session.mSessionName;
        new MaterialAlertDialogBuilder(activity)
            .setTitle(TextUtils.isEmpty(title) ? activity.getString(R.string.feature_sessions_title_session) : title)
            .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void confirmCloseSession(@NonNull TermuxActivity activity, @NonNull TerminalSession session) {
        if (!session.isRunning()) {
            activity.getTermuxTerminalSessionClient().closeSession(session);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_sessions_title_close_session)
            .setMessage(R.string.feature_sessions_msg_close_session)
            .setPositiveButton(R.string.feature_sessions_action_close, (dialog, which) ->
                activity.getTermuxTerminalSessionClient().closeSession(session))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

}
