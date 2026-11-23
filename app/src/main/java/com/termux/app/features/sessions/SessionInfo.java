package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The session features' data of a terminal session that is not stored in the session itself, like
 * its workspace. Kept in {@link SessionRegistry} while the session exists, and saved by
 * {@link SessionPersistence} so it can be restored after the app restarts.
 */
public final class SessionInfo {

    /** The {@link com.termux.terminal.TerminalSession#mHandle} of the session. */
    @NonNull public final String handle;

    /** Increases with each session, used to keep the creation order. */
    public final long sequence;

    @NonNull public String workspaceId = Workspace.DEFAULT_ID;

    /** If the session is listed before the other sessions of its workspace. */
    public boolean pinned;

    /** The {@link TerminalProfile#id} of the profile the session was started with, if any. */
    @Nullable public String profileId;

    /** The saved transcript of the session this session was restored from, if any. */
    @Nullable public String restoredTranscript;

    /** The time {@link #restoredTranscript} was saved at, in milliseconds since the epoch. */
    public long restoredTranscriptTime;

    /** If the user asked to close the session, so it is removed once its process has exited. */
    public boolean closeRequested;

    SessionInfo(@NonNull String handle, long sequence) {
        this.handle = handle;
        this.sequence = sequence;
    }

}
