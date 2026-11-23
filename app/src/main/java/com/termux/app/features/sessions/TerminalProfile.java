package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A terminal profile: the shell, working directory, startup commands and appearance that sessions
 * started from it use. See {@link ProfileStore}.
 */
public final class TerminalProfile {

    /** The {@link #shell} value to use the login shell, like sessions not started from a profile. */
    public static final String SHELL_DEFAULT = "";

    /** The shells that can be selected, if installed in $PREFIX/bin. */
    public static final String[] SHELLS = {"bash", "zsh", "fish", "sh"};

    public static final int MIN_FONT_SIZE = 4;
    public static final int MAX_FONT_SIZE = 64;

    @NonNull public final String id;
    @NonNull public String name = "";
    /** The name of the shell in $PREFIX/bin, like "zsh", or {@link #SHELL_DEFAULT}. */
    @NonNull public String shell = SHELL_DEFAULT;
    /** The working directory, which may start with "~/", or empty for the default one. */
    @NonNull public String workingDirectory = "";
    /** Commands run by the shell when the session starts, see {@link SessionStartup}. */
    @NonNull public String startupCommands = "";
    /**
     * The id of the color scheme of the session, or empty for the default one. Only stored for
     * now, for the color schemes of the theme features.
     */
    @NonNull public String colorSchemeId = "";
    /** The font size in dp, or 0 for the font size of the terminal. */
    public int fontSize;

    public TerminalProfile(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        return new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("shell", shell)
            .put("cwd", workingDirectory)
            .put("startup", startupCommands)
            .put("colorScheme", colorSchemeId)
            .put("fontSize", fontSize);
    }

    @Nullable
    public static TerminalProfile fromJson(@NonNull JSONObject object) {
        String id = object.optString("id", "");
        if (id.isEmpty()) return null;
        TerminalProfile profile = new TerminalProfile(id);
        profile.name = object.optString("name", "");
        profile.shell = object.optString("shell", SHELL_DEFAULT);
        profile.workingDirectory = object.optString("cwd", "");
        profile.startupCommands = object.optString("startup", "");
        profile.colorSchemeId = object.optString("colorScheme", "");
        profile.fontSize = clampFontSize(object.optInt("fontSize", 0));
        return profile;
    }

    /** Clamp {@code fontSize} to the valid range, or return 0 for the default font size. */
    public static int clampFontSize(int fontSize) {
        if (fontSize <= 0) return 0;
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
    }

}
