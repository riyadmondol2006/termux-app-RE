package com.termux.app.features.sessions;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The settings of the session features, stored in their own {@link SharedPreferences} file. The
 * keys are also used by the preferences of {@link SessionSettingsActivity}.
 */
public final class SessionsPreferences {

    /** The name of the {@link SharedPreferences} file. */
    public static final String PREFERENCES_NAME = "termux_re_sessions";

    static final String KEY_WORKSPACES = "workspaces";
    static final String KEY_WORKSPACE_FILTER = "workspace_filter";
    static final String KEY_PROFILES = "profiles";
    static final String KEY_DEFAULT_PROFILE = "default_profile";

    public static final String KEY_RESTORE_SESSIONS = "restore_sessions";
    public static final String KEY_SAVE_TRANSCRIPTS = "save_transcripts";
    public static final String KEY_TRANSCRIPT_LINES = "transcript_lines";
    public static final String KEY_CLEAR_SAVED_SESSIONS = "clear_saved_sessions";

    public static final String KEY_STARTUP_COMMANDS = "startup_commands";
    public static final String KEY_STARTUP_FILES = "startup_files";

    public static final String KEY_NOTIFICATIONS = "terminal_notifications";
    public static final String KEY_NOTIFY_COMMANDS = "notify_long_commands";
    public static final String KEY_NOTIFY_COMMANDS_SECONDS = "notify_long_commands_seconds";
    public static final String KEY_NOTIFICATION_SETTINGS = "notification_settings";

    public static final String KEY_MANAGE_PROFILES = "manage_profiles";

    /** Restoring the session layout is cheap and the data is not sensitive, so it is on by default. */
    public static final boolean DEFAULT_RESTORE_SESSIONS = true;
    /** Transcripts may contain secrets shown in the terminal, so saving them is opt-in. */
    public static final boolean DEFAULT_SAVE_TRANSCRIPTS = false;
    public static final int DEFAULT_TRANSCRIPT_LINES = 1000;
    public static final int MAX_TRANSCRIPT_LINES = 5000;
    public static final boolean DEFAULT_NOTIFICATIONS = true;
    public static final boolean DEFAULT_NOTIFY_COMMANDS = false;
    public static final int DEFAULT_NOTIFY_COMMANDS_SECONDS = 10;

    private SessionsPreferences() {}

    @NonNull
    public static SharedPreferences get(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isRestoreSessionsEnabled(@NonNull Context context) {
        return get(context).getBoolean(KEY_RESTORE_SESSIONS, DEFAULT_RESTORE_SESSIONS);
    }

    public static boolean isSaveTranscriptsEnabled(@NonNull Context context) {
        return get(context).getBoolean(KEY_SAVE_TRANSCRIPTS, DEFAULT_SAVE_TRANSCRIPTS);
    }

    public static int getTranscriptLines(@NonNull Context context) {
        return parseInt(get(context).getString(KEY_TRANSCRIPT_LINES, null), DEFAULT_TRANSCRIPT_LINES, 1, MAX_TRANSCRIPT_LINES);
    }

    @NonNull
    public static String getStartupCommands(@NonNull Context context) {
        String commands = get(context).getString(KEY_STARTUP_COMMANDS, null);
        return commands == null ? "" : commands;
    }

    public static boolean areNotificationsEnabled(@NonNull Context context) {
        return get(context).getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS);
    }

    public static boolean isNotifyCommandsEnabled(@NonNull Context context) {
        return get(context).getBoolean(KEY_NOTIFY_COMMANDS, DEFAULT_NOTIFY_COMMANDS);
    }

    public static int getNotifyCommandsSeconds(@NonNull Context context) {
        return parseInt(get(context).getString(KEY_NOTIFY_COMMANDS_SECONDS, null), DEFAULT_NOTIFY_COMMANDS_SECONDS, 1, 86400);
    }

    /** Parse {@code value} as an int in the range [{@code min}, {@code max}], or return {@code def}. */
    static int parseInt(@Nullable String value, int def, int min, int max) {
        if (value == null) return def;
        try {
            int result = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, result));
        } catch (NumberFormatException e) {
            return def;
        }
    }

}
