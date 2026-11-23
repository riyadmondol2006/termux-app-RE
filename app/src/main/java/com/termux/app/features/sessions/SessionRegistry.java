package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The {@link SessionInfo} of the terminal sessions of the app process. */
public final class SessionRegistry {

    private static final Map<String, SessionInfo> sInfos = new HashMap<>();

    /** The handle of the session that was last current in each workspace. */
    private static final Map<String, String> sLastActiveSessions = new HashMap<>();

    private static long sNextSequence = 1;

    /** The handle of the session shown in the activity. */
    @Nullable private static String sCurrentSession;

    private SessionRegistry() {}

    /** Get the info of {@code session}, which is created if it does not exist yet. */
    @NonNull
    public static synchronized SessionInfo get(@NonNull TerminalSession session) {
        SessionInfo info = sInfos.get(session.mHandle);
        if (info == null) {
            info = new SessionInfo(session.mHandle, sNextSequence++);
            sInfos.put(session.mHandle, info);
        }
        return info;
    }

    /** Get the info of the session with {@code handle} if it exists. */
    @Nullable
    public static synchronized SessionInfo peek(@Nullable String handle) {
        return handle == null ? null : sInfos.get(handle);
    }

    /** Remove the info of sessions that are not in {@code sessions} anymore. */
    public static synchronized void retain(@NonNull List<TermuxSession> sessions) {
        Set<String> handles = new HashSet<>();
        for (TermuxSession session : sessions) handles.add(session.getTerminalSession().mHandle);
        sInfos.keySet().retainAll(handles);
        sLastActiveSessions.values().retainAll(handles);
    }

    /** Move all sessions of the workspace with {@code workspaceId} to the default workspace. */
    public static synchronized void moveSessionsToDefaultWorkspace(@NonNull String workspaceId) {
        for (SessionInfo info : sInfos.values()) {
            if (info.workspaceId.equals(workspaceId)) info.workspaceId = Workspace.DEFAULT_ID;
        }
        sLastActiveSessions.remove(workspaceId);
    }

    public static synchronized void setLastActiveSession(@NonNull String workspaceId, @NonNull String handle) {
        sLastActiveSessions.put(workspaceId, handle);
    }

    @Nullable
    public static synchronized String getLastActiveSession(@NonNull String workspaceId) {
        return sLastActiveSessions.get(workspaceId);
    }

    /** Set the handle of the session shown in the activity, see {@link SessionPersistence}. */
    public static synchronized void setCurrentSession(@Nullable String handle) {
        sCurrentSession = handle;
    }

    @Nullable
    public static synchronized String getCurrentSession() {
        return sCurrentSession != null && sInfos.containsKey(sCurrentSession) ? sCurrentSession : null;
    }

}
