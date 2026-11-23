package com.termux.app.features;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Starts terminal sessions that run a command, and sends input to the current session, for
 * features like the SSH connection manager or the command palette.
 */
public final class TermuxSessionLauncher {

    /** Same limit as {@link com.termux.app.terminal.TermuxTerminalSessionActivityClient}. */
    private static final int MAX_SESSIONS = 8;

    private TermuxSessionLauncher() {}

    /**
     * Start a new session that runs {@code command} with the login shell's environment and make it
     * the current session.
     *
     * @param sessionName The session name shown in the drawer, or {@code null}.
     * @param command A shell command, run with {@code $SHELL -l -c}. Quote arguments with
     *                {@link TermuxCommandRunner#shellQuote(String)}.
     * @param keepShellAfterCommand Start an interactive login shell after the command exits, instead
     *                              of ending the session.
     * @param workingDirectory The working directory, or {@code null} for the current session's or $HOME.
     * @return The new session, or {@code null} if it could not be created.
     */
    @Nullable
    public static TerminalSession runInNewSession(@NonNull TermuxActivity activity, @Nullable String sessionName,
                                                  @NonNull String command, boolean keepShellAfterCommand,
                                                  @Nullable String workingDirectory) {
        TermuxService service = activity.getTermuxService();
        if (service == null) return null;

        if (service.getTermuxSessionsSize() >= MAX_SESSIONS) {
            new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached).setPositiveButton(android.R.string.ok, null).show();
            return null;
        }

        if (workingDirectory == null) {
            TerminalSession currentSession = activity.getCurrentSession();
            workingDirectory = currentSession != null ? currentSession.getCwd() : TermuxConstants.TERMUX_HOME_DIR_PATH;
        }

        String shell = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash";
        String script = keepShellAfterCommand ? command + "\nexec " + shell + " -l" : command;
        TermuxSession termuxSession = service.createTermuxSession(shell, new String[]{"-l", "-c", script}, null,
            workingDirectory, false, sessionName);
        if (termuxSession == null) return null;

        TerminalSession terminalSession = termuxSession.getTerminalSession();
        activity.getTermuxTerminalSessionClient().termuxSessionListNotifyUpdated();
        activity.getTermuxTerminalSessionClient().setCurrentSession(terminalSession);
        activity.getDrawer().closeDrawers();
        return terminalSession;
    }

    /**
     * Write {@code text} to the current session as if typed. Add "\r" to run it as a command.
     *
     * @return Whether there was a running session to write to.
     */
    public static boolean sendTextToCurrentSession(@NonNull TermuxActivity activity, @NonNull String text) {
        TerminalSession session = activity.getCurrentSession();
        if (session == null || !session.isRunning()) return false;
        session.write(text);
        return true;
    }

}
