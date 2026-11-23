package com.termux.app.features.sessions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * The order of the sessions in the drawer and switching between workspaces. Switching to the
 * next, previous or n-th session, including with keyboard shortcuts, uses the sessions listed for
 * the selected workspace in the order they are listed.
 */
public final class SessionWorkspaces {

    private SessionWorkspaces() {}

    /** Build the rows of the drawer sessions list for {@code sessions}, the sessions of the service. */
    @NonNull
    public static List<SessionListModel.Row> buildRows(@NonNull Context context, @NonNull List<TermuxSession> sessions) {
        List<SessionListModel.Entry> entries = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) {
            SessionInfo info = SessionRegistry.get(sessions.get(i).getTerminalSession());
            entries.add(new SessionListModel.Entry(i, info.workspaceId, info.pinned));
        }
        List<String> workspaceIds = new ArrayList<>();
        for (Workspace workspace : WorkspaceStore.getWorkspaces(context)) workspaceIds.add(workspace.id);
        return SessionListModel.build(entries, workspaceIds, WorkspaceStore.getSelectedWorkspaceId(context));
    }

    /** Get the sessions listed in the drawer, in the order they are listed. */
    @NonNull
    public static List<TermuxSession> getListedSessions(@NonNull Context context, @NonNull List<TermuxSession> sessions) {
        List<TermuxSession> listed = new ArrayList<>();
        for (int index : SessionListModel.getSessionOrder(buildRows(context, sessions))) {
            if (index < sessions.size()) listed.add(sessions.get(index));
        }
        return listed;
    }

    /** Get the position of {@code session} in {@code sessions}, or -1. */
    public static int indexOf(@NonNull List<TermuxSession> sessions, @Nullable TerminalSession session) {
        if (session == null) return -1;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getTerminalSession() == session) return i;
        }
        return -1;
    }

    /** Get the workspace of {@code session}, or {@link Workspace#DEFAULT_ID} if its workspace was deleted. */
    @NonNull
    public static String getWorkspaceId(@NonNull Context context, @NonNull TerminalSession session) {
        String workspaceId = SessionRegistry.get(session).workspaceId;
        return WorkspaceStore.getWorkspace(context, workspaceId) != null ? workspaceId : Workspace.DEFAULT_ID;
    }

    /**
     * Select the workspace shown in the drawer, or all workspaces if {@code workspaceId} is
     * {@code null}. If the current session is not in the workspace, switch to the session of the
     * workspace that was used last.
     */
    public static void selectWorkspace(@NonNull TermuxActivity activity, @Nullable String workspaceId) {
        WorkspaceStore.setSelectedWorkspaceId(activity, workspaceId);

        TermuxService service = activity.getTermuxService();
        if (service != null) {
            List<TermuxSession> listed = getListedSessions(activity, service.getTermuxSessions());
            if (indexOf(listed, activity.getCurrentSession()) < 0 && !listed.isEmpty()) {
                TermuxSession target = listed.get(0);
                String lastHandle = workspaceId == null ? null : SessionRegistry.getLastActiveSession(workspaceId);
                for (TermuxSession session : listed) {
                    if (session.getTerminalSession().mHandle.equals(lastHandle)) target = session;
                }
                activity.getTermuxTerminalSessionClient().setCurrentSession(target.getTerminalSession());
            }
        }

        activity.termuxSessionListNotifyUpdated();
    }

    /** Select the next workspace, after the "All" view, and show its name. */
    public static void selectNextWorkspace(@NonNull TermuxActivity activity) {
        List<Workspace> workspaces = WorkspaceStore.getWorkspaces(activity);
        if (workspaces.size() <= 1) {
            activity.showToast(activity.getString(R.string.feature_sessions_msg_no_workspaces), false);
            return;
        }

        List<String> cycle = new ArrayList<>();
        cycle.add(null);
        for (Workspace workspace : workspaces) cycle.add(workspace.id);
        int next = (cycle.indexOf(WorkspaceStore.getSelectedWorkspaceId(activity)) + 1) % cycle.size();
        String workspaceId = cycle.get(next);
        selectWorkspace(activity, workspaceId);
        activity.showToast(workspaceId == null ? activity.getString(R.string.feature_sessions_workspace_all) :
            WorkspaceStore.getWorkspaceName(activity, workspaceId), false);
    }

    /**
     * Make sure that {@code session} is listed in the drawer, by selecting its workspace if another
     * workspace is selected.
     *
     * @return Whether the selected workspace was changed.
     */
    public static boolean revealSession(@NonNull Context context, @NonNull TerminalSession session) {
        String selected = WorkspaceStore.getSelectedWorkspaceId(context);
        String workspaceId = getWorkspaceId(context, session);
        if (selected == null || selected.equals(workspaceId)) return false;
        WorkspaceStore.setSelectedWorkspaceId(context, workspaceId);
        return true;
    }

    /** Add a new {@code session} to the workspace shown in the drawer, if one is selected. */
    public static void addToSelectedWorkspace(@NonNull Context context, @NonNull TerminalSession session) {
        String workspaceId = WorkspaceStore.getSelectedWorkspaceId(context);
        if (workspaceId != null) SessionRegistry.get(session).workspaceId = workspaceId;
    }

    /** Move {@code session} to the workspace with {@code workspaceId} and show that workspace. */
    public static void moveSession(@NonNull TermuxActivity activity, @NonNull TerminalSession session, @NonNull String workspaceId) {
        SessionRegistry.get(session).workspaceId = workspaceId;
        if (activity.getCurrentSession() == session) {
            revealSession(activity, session);
            SessionRegistry.setLastActiveSession(workspaceId, session.mHandle);
        }
        activity.termuxSessionListNotifyUpdated();
        activity.getTermuxTerminalSessionClient().checkAndScrollToSession(activity.getCurrentSession());
        SessionPersistence.scheduleSave(activity.getTermuxService());
    }

    public static void togglePinned(@NonNull TermuxActivity activity, @NonNull TerminalSession session) {
        SessionInfo info = SessionRegistry.get(session);
        info.pinned = !info.pinned;
        activity.termuxSessionListNotifyUpdated();
        activity.getTermuxTerminalSessionClient().checkAndScrollToSession(activity.getCurrentSession());
        SessionPersistence.scheduleSave(activity.getTermuxService());
    }

}
