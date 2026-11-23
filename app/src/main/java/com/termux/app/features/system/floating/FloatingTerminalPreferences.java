package com.termux.app.features.system.floating;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** The position, size and font size of the floating terminal, in their own preferences file. */
final class FloatingTerminalPreferences {

    private static final String PREFERENCES_NAME = "termux_system_floating_terminal";

    static final int NOT_SET = Integer.MIN_VALUE;

    private final SharedPreferences mPreferences;

    FloatingTerminalPreferences(@NonNull Context context) {
        mPreferences = context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    int getWindowX() { return mPreferences.getInt("window_x", NOT_SET); }
    int getWindowY() { return mPreferences.getInt("window_y", NOT_SET); }
    int getWindowWidth() { return mPreferences.getInt("window_width", NOT_SET); }
    int getWindowHeight() { return mPreferences.getInt("window_height", NOT_SET); }

    void setWindowBounds(int x, int y, int width, int height) {
        mPreferences.edit().putInt("window_x", x).putInt("window_y", y)
            .putInt("window_width", width).putInt("window_height", height).apply();
    }

    int getBubbleX() { return mPreferences.getInt("bubble_x", NOT_SET); }
    int getBubbleY() { return mPreferences.getInt("bubble_y", NOT_SET); }

    void setBubblePosition(int x, int y) {
        mPreferences.edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }

    /** The font size set by pinching, or {@link #NOT_SET} to use the font size of the terminal. */
    int getFontSize() { return mPreferences.getInt("font_size", NOT_SET); }

    void setFontSize(int size) {
        mPreferences.edit().putInt("font_size", size).apply();
    }

}
