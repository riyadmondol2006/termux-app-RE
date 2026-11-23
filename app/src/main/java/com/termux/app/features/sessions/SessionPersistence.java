package com.termux.app.features.sessions;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.app.features.TermuxCommandRunner;
import com.termux.shared.logger.Logger;
import com.termux.shared.shell.ShellUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Saves the terminal sessions, so they can be restored when the app is started again after its
 * process ended, like after it was killed by Android, the Exit notification action or a reboot.
 *
 * Processes can't be restored, so the sessions are recreated with the same names, workspaces,
 * pinning, profiles and working directories, and start new shells. If enabled, the transcript of
 * each session is saved too, capped to a number of lines, and shown as read-only history above
 * the first prompt of the restored session and in a viewer from the session menu.
 *
 * The data is stored in the app's private no_backup directory, not in $HOME, readable only by the
 * app user.
 */
public final class SessionPersistence {

    private static final String LOG_TAG = "SessionPersistence";

    /** Max sessions restored, like the max sessions of the drawer. */
    public static final int MAX_SESSIONS = 8;
    /** Max characters saved of a transcript, in addition to the line limit. */
    public static final int MAX_TRANSCRIPT_CHARS = 256 * 1024;

    private static final String SNAPSHOT_FILE_NAME = "sessions.json";
    private static final long SAVE_DELAY_MILLIS = 1000;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Nullable private static WeakReference<TermuxService> sScheduledService;
    private static final Runnable SAVE_RUNNABLE = () -> {
        TermuxService service = sScheduledService != null ? sScheduledService.get() : null;
        if (service != null) save(service, false, false);
    };

    /**
     * Set while the service stops, so removing its sessions does not replace the saved sessions.
     * Cleared when the service is created again.
     */
    private static boolean sFrozen;

    private SessionPersistence() {}

    @NonNull
    static File getDirectory(@NonNull Context context) {
        return new File(context.getApplicationContext().getNoBackupFilesDir(), "sessions");
    }

    @NonNull
    private static File getTranscriptFile(@NonNull File directory, @NonNull String handle) {
        return new File(directory, "transcript-" + handle + ".txt");
    }

    /** Save the sessions of {@code service} soon, without saving new transcripts. */
    @MainThread
    public static void scheduleSave(@Nullable TermuxService service) {
        if (service == null || sFrozen) return;
        sScheduledService = new WeakReference<>(service);
        MAIN_HANDLER.removeCallbacks(SAVE_RUNNABLE);
        MAIN_HANDLER.postDelayed(SAVE_RUNNABLE, SAVE_DELAY_MILLIS);
    }

    /**
     * Save the sessions of {@code service} now, with their transcripts if enabled.
     *
     * @param synchronous Write the files before returning, like when the process may end soon.
     */
    @MainThread
    public static void saveNow(@Nullable TermuxService service, boolean synchronous) {
        if (service == null || sFrozen) return;
        MAIN_HANDLER.removeCallbacks(SAVE_RUNNABLE);
        save(service, true, synchronous);
    }

    /** Save the sessions now and keep them saved while the sessions are removed, like on Exit. */
    @MainThread
    public static void saveAndFreeze(@NonNull TermuxService service) {
        saveNow(service, true);
        sFrozen = true;
    }

    @MainThread
    private static void save(@NonNull TermuxService service, boolean withTranscripts, boolean synchronous) {
        Context context = service.getApplicationContext();
        File directory = getDirectory(context);
        if (!SessionsPreferences.isRestoreSessionsEnabled(context)) {
            EXECUTOR.execute(() -> deleteDirectoryFiles(directory, null));
            return;
        }
        boolean saveTranscripts = SessionsPreferences.isSaveTranscriptsEnabled(context);
        int maxLines = SessionsPreferences.getTranscriptLines(context);

        // Collect the data on the main thread, since sessions are changed on it.
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.time = System.currentTimeMillis();
        Map<String, String> newTranscripts = new HashMap<>();
        Set<String> keptTranscripts = new HashSet<>();
        String currentHandle = getCurrentSessionHandle(context);
        List<TermuxSession> sessions = service.getTermuxSessions();
        for (int i = 0; i < sessions.size() && snapshot.sessions.size() < MAX_SESSIONS; i++) {
            TermuxSession termuxSession = sessions.get(i);
            // Sessions run by plugins for a command are not restored.
            if (termuxSession.getExecutionCommand().isPluginExecutionCommand) continue;
            TerminalSession session = termuxSession.getTerminalSession();
            if (!session.isRunning() && session.getEmulator() != null) continue;

            SessionInfo info = SessionRegistry.get(session);
            SessionSnapshot.Session saved = new SessionSnapshot.Session(session.mHandle);
            saved.name = session.mSessionName;
            saved.workspaceId = info.workspaceId;
            saved.pinned = info.pinned;
            saved.profileId = info.profileId;
            String cwd = session.getCwd();
            saved.workingDirectory = cwd != null ? cwd : termuxSession.getExecutionCommand().workingDirectory;

            if (saveTranscripts) {
                File transcriptFile = getTranscriptFile(directory, session.mHandle);
                if (withTranscripts && session.getEmulator() != null) {
                    String transcript = capTranscript(ShellUtils.getTerminalSessionTranscriptText(session, false, false), maxLines, MAX_TRANSCRIPT_CHARS);
                    if (!transcript.isEmpty()) newTranscripts.put(session.mHandle, transcript);
                } else if (session.getEmulator() == null && info.restoredTranscript != null && !transcriptFile.isFile()) {
                    // A restored session that has not been shown yet keeps its restored transcript.
                    newTranscripts.put(session.mHandle, info.restoredTranscript);
                } else if (transcriptFile.isFile()) {
                    keptTranscripts.add(session.mHandle);
                }
                saved.hasTranscript = newTranscripts.containsKey(session.mHandle) || keptTranscripts.contains(session.mHandle);
            }
            if (session.mHandle.equals(currentHandle)) snapshot.currentHandle = session.mHandle;
            snapshot.sessions.add(saved);
        }

        Runnable write = () -> writeSnapshot(directory, snapshot, newTranscripts);
        if (synchronous) write.run(); else EXECUTOR.execute(write);
    }

    /** Get the handle of the current session of the activity, or the one it stored when it was stopped. */
    @Nullable
    private static String getCurrentSessionHandle(@NonNull Context context) {
        String handle = SessionRegistry.getCurrentSession();
        if (handle != null) return handle;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        return preferences != null ? preferences.getCurrentSession() : null;
    }

    private static synchronized void writeSnapshot(@NonNull File directory, @NonNull SessionSnapshot snapshot,
                                                   @NonNull Map<String, String> newTranscripts) {
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Failed to create " + directory);
            chmod(directory, 0700);

            Set<String> transcriptFileNames = new HashSet<>();
            for (SessionSnapshot.Session session : snapshot.sessions) {
                if (!session.hasTranscript) continue;
                File file = getTranscriptFile(directory, session.handle);
                transcriptFileNames.add(file.getName());
                String transcript = newTranscripts.get(session.handle);
                if (transcript != null) writeFile(file, transcript);
            }

            writeFile(new File(directory, SNAPSHOT_FILE_NAME), snapshot.toJson());
            transcriptFileNames.add(SNAPSHOT_FILE_NAME);
            deleteDirectoryFiles(directory, transcriptFileNames);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save sessions", e);
        }
    }

    /**
     * Restore the saved sessions in {@code service} if enabled, like when it is created. The
     * sessions only start their shells once they are shown.
     *
     * @return The number of restored sessions.
     */
    @MainThread
    public static int restoreSessions(@NonNull TermuxService service) {
        sFrozen = false;
        Context context = service.getApplicationContext();
        if (!SessionsPreferences.isRestoreSessionsEnabled(context) || !TermuxCommandRunner.isBootstrapInstalled()) return 0;

        File directory = getDirectory(context);
        SessionSnapshot snapshot;
        try {
            File file = new File(directory, SNAPSHOT_FILE_NAME);
            if (!file.isFile()) return 0;
            snapshot = SessionSnapshot.fromJson(readFile(file));
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read saved sessions", e);
            return 0;
        }
        if (snapshot == null || snapshot.sessions.isEmpty()) return 0;

        boolean restoreTranscripts = SessionsPreferences.isSaveTranscriptsEnabled(context);
        String currentHandle = null;
        int restored = 0;
        for (SessionSnapshot.Session saved : snapshot.sessions) {
            if (restored >= MAX_SESSIONS) break;
            TerminalProfile profile = ProfileStore.getProfile(context, saved.profileId);
            String workingDirectory = SessionStartup.resolveWorkingDirectory(saved.workingDirectory);
            if (workingDirectory == null) workingDirectory = TermuxConstants.TERMUX_HOME_DIR_PATH;

            TermuxSession termuxSession = SessionStartup.createSession(context, service, profile, saved.name, workingDirectory, true);
            if (termuxSession == null) continue;
            restored++;

            TerminalSession session = termuxSession.getTerminalSession();
            SessionInfo info = SessionRegistry.get(session);
            info.workspaceId = WorkspaceStore.getWorkspace(context, saved.workspaceId) != null ? saved.workspaceId : Workspace.DEFAULT_ID;
            info.pinned = saved.pinned;
            if (restoreTranscripts && saved.hasTranscript) {
                try {
                    File transcriptFile = getTranscriptFile(directory, saved.handle);
                    if (transcriptFile.isFile()) {
                        info.restoredTranscript = capTranscript(readFile(transcriptFile),
                            SessionsPreferences.MAX_TRANSCRIPT_LINES, MAX_TRANSCRIPT_CHARS);
                        info.restoredTranscriptTime = snapshot.time;
                    }
                } catch (IOException e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read saved transcript", e);
                }
            }
            if (saved.handle.equals(snapshot.currentHandle)) currentHandle = session.mHandle;
        }

        if (currentHandle != null) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
            if (preferences != null) preferences.setCurrentSession(currentHandle);
        }
        Logger.logInfo(LOG_TAG, "Restored " + restored + " sessions");
        scheduleSave(service);
        return restored;
    }

    /**
     * Show the restored transcript of {@code session} above its first prompt. Must be called when
     * its shell was started, before the shell output is shown.
     */
    @MainThread
    public static void onSessionStarted(@NonNull Context context, @NonNull TerminalSession session) {
        SessionInfo info = SessionRegistry.peek(session.mHandle);
        TerminalEmulator emulator = session.getEmulator();
        if (info == null || info.restoredTranscript == null || emulator == null) return;

        String header = context.getString(R.string.feature_sessions_restored_history_header, formatTime(info.restoredTranscriptTime));
        String footer = context.getString(R.string.feature_sessions_restored_history_footer);
        byte[] bytes = formatRestoredHistory(info.restoredTranscript, header, footer).getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    /** Delete the saved transcripts and the restored transcripts of the sessions. */
    public static void deleteSavedTranscripts(@NonNull Context context, @Nullable TermuxService service) {
        if (service != null) {
            for (TermuxSession session : service.getTermuxSessions()) {
                SessionInfo info = SessionRegistry.peek(session.getTerminalSession().mHandle);
                if (info != null) info.restoredTranscript = null;
            }
        }
        File directory = getDirectory(context);
        EXECUTOR.execute(() -> {
            Set<String> keep = new HashSet<>();
            keep.add(SNAPSHOT_FILE_NAME);
            deleteDirectoryFiles(directory, keep);
        });
        if (service != null) scheduleSave(service);
    }

    @NonNull
    static String formatTime(long time) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(time));
    }

    /**
     * Get the last {@code maxLines} lines of {@code transcript}, at most {@code maxChars} chars,
     * without trailing empty lines.
     */
    @NonNull
    static String capTranscript(@Nullable String transcript, int maxLines, int maxChars) {
        if (transcript == null) return "";
        int end = transcript.length();
        while (end > 0 && Character.isWhitespace(transcript.charAt(end - 1))) end--;
        int start = end;
        int lines = 0;
        while (start > 0 && end - start < maxChars) {
            if (transcript.charAt(start - 1) == '\n' && ++lines >= maxLines) break;
            start--;
        }
        // Don't start in the middle of a surrogate pair.
        if (start < end && Character.isLowSurrogate(transcript.charAt(start))) start++;
        return transcript.substring(start, end);
    }

    /**
     * Get the text to append to the terminal to show {@code transcript} as history: control
     * characters are removed so it can't change the terminal state, and it is shown dimmed between
     * a header and a footer line.
     */
    @NonNull
    static String formatRestoredHistory(@NonNull String transcript, @NonNull String header, @NonNull String footer) {
        StringBuilder builder = new StringBuilder(transcript.length() + 128);
        builder.append("\033[0;2m").append(sanitize(header)).append("\033[0m\r\n");
        for (int i = 0; i < transcript.length(); i++) {
            char c = transcript.charAt(i);
            if (c == '\n') builder.append("\r\n");
            else if (c == '\t' || (c >= 0x20 && c != 0x7F && !(c >= 0x80 && c < 0xA0))) builder.append(c);
        }
        builder.append("\r\n\033[0;2m").append(sanitize(footer)).append("\033[0m\r\n");
        return builder.toString();
    }

    @NonNull
    private static String sanitize(@NonNull String text) {
        return text.replaceAll("[\\p{Cntrl}]", " ");
    }

    private static void writeFile(@NonNull File file, @NonNull String text) throws IOException {
        File tempFile = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        chmod(tempFile, 0600);
        if (!tempFile.renameTo(file)) throw new IOException("Failed to rename " + tempFile + " to " + file);
    }

    @NonNull
    private static String readFile(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Delete the files in {@code directory} except the ones named in {@code keep}. */
    private static synchronized void deleteDirectoryFiles(@NonNull File directory, @Nullable Set<String> keep) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (keep == null || !keep.contains(file.getName())) {
                if (!file.delete()) Logger.logWarn(LOG_TAG, "Failed to delete " + file);
            }
        }
    }

    private static void chmod(@NonNull File file, int mode) {
        try {
            Os.chmod(file.getAbsolutePath(), mode);
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to chmod " + file + ": " + e.getMessage());
        }
    }

}
