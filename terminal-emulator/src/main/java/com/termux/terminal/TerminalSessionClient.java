package com.termux.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The interface for communication between {@link TerminalSession} and its client. It is used to
 * send callbacks to the client when {@link TerminalSession} changes or for sending other
 * back data to the client like logs.
 */
public interface TerminalSessionClient {

    void onTextChanged(@NonNull TerminalSession changedSession);

    void onTitleChanged(@NonNull TerminalSession changedSession);

    void onSessionFinished(@NonNull TerminalSession finishedSession);

    void onCopyTextToClipboard(@NonNull TerminalSession session, String text);

    void onPasteTextFromClipboard(@Nullable TerminalSession session);

    void onBell(@NonNull TerminalSession session);

    void onColorsChanged(@NonNull TerminalSession session);

    void onTerminalCursorStateChange(boolean state);

    void setTerminalShellPid(@NonNull TerminalSession session, int pid);

    /**
     * Called when a program in the session asked to show a notification with an `OSC 9` or
     * `OSC 777;notify` escape sequence, like when a long running command finishes. The text is
     * passed as received and must be sanitized before it is shown.
     *
     * @param title The title, or {@code null} if only a body was sent.
     * @param body The body, which may be empty.
     */
    default void onTerminalNotification(@NonNull TerminalSession session, @Nullable String title, @NonNull String body) {
    }



    Integer getTerminalCursorStyle();



    void logError(String tag, String message);

    void logWarn(String tag, String message);

    void logInfo(String tag, String message);

    void logDebug(String tag, String message);

    void logVerbose(String tag, String message);

    void logStackTraceWithMessage(String tag, String message, Exception e);

    void logStackTrace(String tag, Exception e);

}
