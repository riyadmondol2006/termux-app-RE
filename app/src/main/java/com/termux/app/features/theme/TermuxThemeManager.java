package com.termux.app.features.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Stable theme-id API for other areas (e.g. terminal profiles): {@link #applyThemeById(Context, String)}
 * applies a bundled or user theme to the current session without restarting the activity.
 * Applying writes ~/.termux/colors.properties and ~/.termux/font.ttf (backing up existing files to
 * colors.properties.bak / font.ttf.bak), updates the cursor/font properties, then broadcasts a style reload.
 */
public final class TermuxThemeManager {

    private static final String PREFS = "termux_theme_studio";
    private static final String KEY_CURRENT_ID = "current_theme_id";
    private static final String KEY_BG_ALPHA = "bg_alpha"; // 0..100, 100 = opaque background (no image effect)
    private static final String KEY_BG_BLUR = "bg_blur";   // 0..25
    public static final String BG_IMAGE_NAME = "theme-background.png";

    private TermuxThemeManager() {}

    @NonNull public static List<TermuxTheme> getThemes(@NonNull Context context) {
        List<TermuxTheme> all = new ArrayList<>(BundledThemes.all());
        File dir = userThemesDir(context);
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                try { all.add(TermuxTheme.fromJson(readText(f))); } catch (Exception ignored) {}
            }
        }
        return all;
    }

    @Nullable public static TermuxTheme getThemeById(@NonNull Context context, @NonNull String id) {
        for (TermuxTheme t : getThemes(context)) if (t.id.equals(id)) return t;
        return null;
    }

    @NonNull public static String getCurrentThemeId(@NonNull Context context) {
        return prefs(context).getString(KEY_CURRENT_ID, BundledThemes.defaultTheme().id);
    }

    /** The theme currently in effect, reconstructed from ~/.termux/colors.properties when present. */
    @NonNull public static TermuxTheme getCurrentTheme(@NonNull Context context) {
        TermuxTheme saved = getThemeById(context, getCurrentThemeId(context));
        TermuxTheme fallback = saved != null ? saved : BundledThemes.defaultTheme();
        File colors = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        if (colors.isFile()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(colors)) { p.load(in); } catch (IOException ignored) {}
            if (!p.isEmpty()) return TermuxTheme.fromProperties(fallback.id, fallback.name, p, fallback);
        }
        return fallback;
    }

    public static void saveUserTheme(@NonNull Context context, @NonNull TermuxTheme theme) throws IOException {
        File dir = userThemesDir(context);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        writeText(new File(dir, theme.id + ".json"), themeJson(theme));
    }

    public static void deleteUserTheme(@NonNull Context context, @NonNull String id) {
        new File(userThemesDir(context), id + ".json").delete();
    }

    public static boolean isBundled(@NonNull String id) {
        for (TermuxTheme t : BundledThemes.all()) if (t.id.equals(id)) return true;
        return false;
    }

    /** Apply a bundled/user theme by its stable id. Returns false when the id is unknown. */
    public static boolean applyThemeById(@NonNull Context context, @NonNull String id) {
        TermuxTheme t = getThemeById(context, id);
        if (t == null) return false;
        try { applyTheme(context, t, null, 0, null, false); return true; } catch (IOException e) { return false; }
    }

    /**
     * Apply a theme now. {@code fontTtf} (nullable) is copied to ~/.termux/font.ttf; when
     * {@code clearFont} is set any existing font.ttf is backed up and removed. Cursor style/blink and
     * font size are written to termux.properties / app preferences by the caller-facing overload in the Studio.
     */
    public static void applyTheme(@NonNull Context context, @NonNull TermuxTheme theme,
                                  @Nullable File fontTtf, int fontSizeSp,
                                  @Nullable String cursorStyle, boolean cursorBlink) throws IOException {
        File home = new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);
        if (!home.exists() && !home.mkdirs()) throw new IOException("Cannot create " + home);
        File colors = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        backup(colors);
        writeText(colors, theme.toColorsProperties());
        File font = TermuxConstants.TERMUX_FONT_FILE;
        if (fontTtf != null) { backup(font); copy(fontTtf, font); }
        if (cursorStyle != null) {
            setTermuxProperty("terminal-cursor-style", cursorStyle);
            setTermuxProperty("terminal-cursor-blink-rate", cursorBlink ? "530" : "0");
        }
        if (fontSizeSp > 0) {
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences prefs =
                com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(context, false);
            if (prefs != null) prefs.setFontSize(fontSizeSp);
        }
        prefs(context).edit().putString(KEY_CURRENT_ID, theme.id).apply();
        TermuxActivity.updateTermuxActivityStyling(context, false);
    }

    @NonNull public static String themeJson(@NonNull TermuxTheme theme) { return theme.toJson(); }

    @NonNull public static String exportTheme(@NonNull Context context, @NonNull String id) {
        TermuxTheme t = getThemeById(context, id);
        return t == null ? "" : themeJson(t);
    }

    /** Import a theme from JSON text or colors.properties text. Saves it as a user theme and returns it. */
    @NonNull public static TermuxTheme importTheme(@NonNull Context context, @NonNull String text) throws Exception {
        String trimmed = text.trim();
        TermuxTheme theme;
        if (trimmed.startsWith("{")) {
            theme = TermuxTheme.fromJson(trimmed);
            if (isBundled(theme.id) || getThemeById(context, theme.id) != null)
                theme = theme.copy(theme.id + "-imported", theme.name);
        } else {
            Properties p = new Properties();
            p.load(new java.io.StringReader(trimmed));
            String name = "Imported " + System.currentTimeMillis();
            theme = TermuxTheme.fromProperties("user-" + TermuxTheme.slugify(name), name, p, BundledThemes.defaultTheme());
        }
        saveUserTheme(context, theme);
        return theme;
    }

    public static void setBackground(@NonNull Context context, @Nullable Uri imageUri, int alphaPercent, int blur) throws IOException {
        prefs(context).edit().putInt(KEY_BG_ALPHA, alphaPercent).putInt(KEY_BG_BLUR, blur).apply();
        File target = backgroundFile(context);
        if (imageUri == null) { target.delete(); return; }
        try (InputStream in = context.getContentResolver().openInputStream(imageUri);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Cannot open image");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    @NonNull public static File backgroundFile(@NonNull Context context) {
        return new File(context.getFilesDir(), BG_IMAGE_NAME);
    }
    /**
     * Load the background image scaled down to at most {@code maxWidth} x {@code maxHeight} (so a
     * large photo doesn't use tens of MB) with the configured blur applied, or {@code null} if no
     * background image is set or it can't be decoded.
     */
    @Nullable
    public static android.graphics.Bitmap loadBackgroundBitmap(@NonNull Context context, int maxWidth, int maxHeight) {
        File file = backgroundFile(context);
        if (!file.isFile()) return null;
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        maxWidth = Math.max(1, maxWidth);
        maxHeight = Math.max(1, maxHeight);
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= maxWidth && bounds.outHeight / (sampleSize * 2) >= maxHeight)
            sampleSize *= 2;
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) return null;
        int blur = getBackgroundBlur(context);
        if (blur > 0) { // Cheap blur: scale down, then back up with filtering.
            int w = Math.max(1, bitmap.getWidth() / (blur * 2)), h = Math.max(1, bitmap.getHeight() / (blur * 2));
            bitmap = android.graphics.Bitmap.createScaledBitmap(
                android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true), bitmap.getWidth(), bitmap.getHeight(), true);
        }
        return bitmap;
    }

    public static int getBackgroundAlpha(@NonNull Context context) { return prefs(context).getInt(KEY_BG_ALPHA, 100); }
    public static int getBackgroundBlur(@NonNull Context context) { return prefs(context).getInt(KEY_BG_BLUR, 0); }

    /** Edit one key in ~/.termux/termux.properties, preserving other lines. */
    public static void setTermuxProperty(@NonNull String key, @NonNull String value) throws IOException {
        File file = new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
        File termuxDir = file.getParentFile();
        // termux.properties normally lives in ~/.termux; fall back to the first configured path's parent.
        List<String> lines = new ArrayList<>();
        boolean replaced = false;
        if (file.isFile()) {
            for (String line : readText(file).split("\n")) {
                if (line.trim().startsWith(key + "=")) { lines.add(key + "=" + value); replaced = true; }
                else lines.add(line);
            }
        }
        if (!replaced) lines.add(key + "=" + value);
        if (termuxDir != null && !termuxDir.exists()) termuxDir.mkdirs();
        writeText(file, String.join("\n", lines) + "\n");
    }

    private static File userThemesDir(@NonNull Context context) { return new File(context.getFilesDir(), "themes"); }
    private static SharedPreferences prefs(@NonNull Context context) { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    private static void backup(@NonNull File file) throws IOException {
        if (file.isFile()) copy(file, new File(file.getParentFile(), file.getName() + ".bak"));
    }
    private static void copy(@NonNull File from, @NonNull File to) throws IOException {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
    @NonNull private static String readText(@NonNull File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static void writeText(@NonNull File file, @NonNull String text) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }
}
