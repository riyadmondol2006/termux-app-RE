package com.termux.app.features.theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A terminal theme: 16 ANSI colors plus foreground, background and cursor. Colors are ARGB ints (alpha ignored on write). */
public final class TermuxTheme {

    public static final int ANSI_COUNT = 16;
    public static final String[] ANSI_NAMES = {
        "Black", "Red", "Green", "Yellow", "Blue", "Magenta", "Cyan", "White",
        "Bright black", "Bright red", "Bright green", "Bright yellow",
        "Bright blue", "Bright magenta", "Bright cyan", "Bright white"};

    @NonNull public final String id;
    @NonNull public final String name;
    public int foreground;
    public int background;
    public int cursor;
    @NonNull public final int[] ansi = new int[ANSI_COUNT];

    public TermuxTheme(@NonNull String id, @NonNull String name, int foreground, int background, int cursor, @NonNull int[] ansi) {
        if (ansi.length != ANSI_COUNT) throw new IllegalArgumentException("ansi must have 16 entries");
        this.id = id; this.name = name; this.foreground = foreground; this.background = background; this.cursor = cursor;
        System.arraycopy(ansi, 0, this.ansi, 0, ANSI_COUNT);
    }

    @NonNull public TermuxTheme copy(@NonNull String newId, @NonNull String newName) {
        return new TermuxTheme(newId, newName, foreground, background, cursor, ansi.clone());
    }

    /** Parse "#RRGGBB", "RRGGBB" or "#AARRGGBB" (alpha dropped). Returns null when invalid. */
    @Nullable public static Integer parseHex(@Nullable String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 8) s = s.substring(2);
        if (s.length() != 6) return null;
        try { return 0xFF000000 | Integer.parseInt(s, 16); } catch (NumberFormatException e) { return null; }
    }

    @NonNull public static String toHex(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    /** colors.properties content for this theme, in the format read by TerminalColorScheme.updateWith. */
    @NonNull public String toColorsProperties() {
        StringBuilder b = new StringBuilder();
        b.append("# Termux RE theme: ").append(name).append(" (").append(id).append(")\n");
        b.append("foreground=").append(toHex(foreground)).append('\n');
        b.append("background=").append(toHex(background)).append('\n');
        b.append("cursor=").append(toHex(cursor)).append('\n');
        for (int i = 0; i < ANSI_COUNT; i++)
            b.append("color").append(i).append('=').append(toHex(ansi[i])).append('\n');
        return b.toString();
    }

    /** Build a theme from parsed colors.properties; missing entries fall back to {@code fallback}. */
    @NonNull public static TermuxTheme fromProperties(@NonNull String id, @NonNull String name, @NonNull Properties props, @NonNull TermuxTheme fallback) {
        TermuxTheme t = fallback.copy(id, name);
        Integer v;
        if ((v = parseHex(props.getProperty("foreground"))) != null) t.foreground = v;
        if ((v = parseHex(props.getProperty("background"))) != null) t.background = v;
        if ((v = parseHex(props.getProperty("cursor"))) != null) t.cursor = v;
        for (int i = 0; i < ANSI_COUNT; i++)
            if ((v = parseHex(props.getProperty("color" + i))) != null) t.ansi[i] = v;
        return t;
    }

    /** Dependency-free JSON form (kept simple so it is unit-testable without Android's org.json). */
    @NonNull public String toJson() {
        StringBuilder b = new StringBuilder();
        b.append("{\"id\":\"").append(escape(id)).append("\",\"name\":\"").append(escape(name)).append('"');
        b.append(",\"foreground\":\"").append(toHex(foreground)).append("\",\"background\":\"").append(toHex(background))
            .append("\",\"cursor\":\"").append(toHex(cursor)).append("\",\"ansi\":[");
        for (int i = 0; i < ANSI_COUNT; i++) { if (i > 0) b.append(','); b.append('"').append(toHex(ansi[i])).append('"'); }
        return b.append("]}").toString();
    }

    @NonNull public static TermuxTheme fromJson(@NonNull String json) throws IllegalArgumentException {
        Integer fg = parseHex(field(json, "foreground")), bg = parseHex(field(json, "background")), cu = parseHex(field(json, "cursor"));
        if (fg == null || bg == null || cu == null) throw new IllegalArgumentException("missing foreground/background/cursor");
        Matcher m = Pattern.compile("\"ansi\"\\s*:\\s*\\[([^]]*)]").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("missing ansi");
        String[] parts = m.group(1).split(",");
        if (parts.length != ANSI_COUNT) throw new IllegalArgumentException("ansi must have 16 entries");
        int[] ansi = new int[ANSI_COUNT];
        for (int i = 0; i < ANSI_COUNT; i++) {
            Integer c = parseHex(parts[i].replace("\"", "").trim());
            if (c == null) throw new IllegalArgumentException("bad ansi color " + i);
            ansi[i] = c;
        }
        String id = field(json, "id");
        if (id == null || !id.matches("[A-Za-z0-9_.-]+")) id = "custom-" + Math.abs(String.valueOf(id).hashCode());
        String name = field(json, "name");
        return new TermuxTheme(id, name == null ? id : name, fg, bg, cu, ansi);
    }

    @Nullable private static String field(@NonNull String json, @NonNull String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }
    @NonNull private static String escape(@NonNull String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    /** Slugify a display name into a stable theme id fragment. */
    @NonNull public static String slugify(@NonNull String name) {
        String s = name.toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "custom" : s;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof TermuxTheme)) return false;
        TermuxTheme t = (TermuxTheme) o;
        return id.equals(t.id) && foreground == t.foreground && background == t.background && cursor == t.cursor && Arrays.equals(ansi, t.ansi);
    }
    @Override public int hashCode() { return id.hashCode() * 31 + Arrays.hashCode(ansi); }
}
