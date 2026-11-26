package com.termux.app.features.input;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** File transformations kept independent of UI. No shell history is written during reads. */
public final class InputFiles {
    private InputFiles() {}

    // java.io instead of java.nio.file, which needs Android 8 while the app supports Android 7.

    /** The text of {@code file}, or an empty string if it doesn't exist. */
    public static String read(File file) throws IOException {
        if (!file.exists()) return "";
        try (InputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    /**
     * Replace the contents of {@code file} atomically: write a temporary file next to it and rename
     * it over the file. With {@code backup}, the old contents are first copied to "{@code name}.bak".
     */
    public static void replace(File file, String text, boolean backup) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("No parent folder for " + file);
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        if (backup && file.exists()) {
            try (InputStream in = new FileInputStream(file);
                 FileOutputStream out = new FileOutputStream(new File(parent, file.getName() + ".bak"))) {
                out.write(readAll(in));
                out.getFD().sync();
            }
        }
        File temp = File.createTempFile(".input-", ".tmp", parent);
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            // rename(2) replaces the target atomically on the same file system.
            if (!temp.renameTo(file)) throw new IOException("Cannot replace " + file);
        } finally {
            if (temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    /** Replace active properties including their continuation lines, preserving all other bytes. */
    public static String keyboardProperties(String original, String json, String style) {
        StringBuilder out = new StringBuilder();
        boolean skip = false, continued = false;
        for (String line : original.split("(?<=\\n)", -1)) {
            String trimmed = line.trim();
            boolean target = !continued && trimmed.matches("extra-keys(?:-style)?\\s*[:=].*");
            boolean omit = skip || target;
            String content = line.replaceFirst("[\\r\\n]+$", "");
            int slashes = 0;
            for (int i = content.length() - 1; i >= 0 && content.charAt(i) == '\\'; i--) slashes++;
            // Comments never continue a java.util.Properties logical line.
            continued =
                    slashes % 2 == 1
                            && (continued || !(trimmed.startsWith("#") || trimmed.startsWith("!")));
            skip = omit && continued;
            if (!omit) out.append(line);
        }
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
        // java.util.Properties consumes backslashes, so protect JSON escapes.
        return out
                + "extra-keys="
                + json.replace("\\", "\\\\")
                + "\nextra-keys-style="
                + style
                + "\n";
    }

    public static final class Entry {
        public final String command;
        public final int start, end;

        Entry(String command, int start, int end) {
            this.command = command;
            this.start = start;
            this.end = end;
        }
    }

    public static List<Entry> history(String text, String shell) {
        List<Entry> result = new ArrayList<>();
        int offset = 0;
        String[] lines = text.split("(?<=\n)");
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int start = offset;
            offset += raw.length();
            String command = raw.replaceFirst("[\\r\\n]+$", "");
            if (shell.equals("fish")) {
                if (!command.startsWith("- cmd: ")) continue;
                command = decodeFish(command.substring(7));
                while (i + 1 < lines.length && !lines[i + 1].startsWith("- cmd: "))
                    offset += lines[++i].length();
            } else if (shell.equals("zsh")) {
                command = command.replaceFirst("^: \\d+:\\d+;", "");
                while (command.endsWith("\\") && i + 1 < lines.length) {
                    String next = lines[++i];
                    offset += next.length();
                    command =
                            command.substring(0, command.length() - 1)
                                    + "\n"
                                    + next.replaceFirst("[\\r\\n]+$", "");
                }
            } else if (command.matches("#\\d+")) continue;
            else if (shell.equals("bash") && i > 0 && lines[i - 1].trim().matches("#\\d+")) {
                while (i + 1 < lines.length && !lines[i + 1].trim().matches("#\\d+")) {
                    String next = lines[++i];
                    offset += next.length();
                    command += "\n" + next.replaceFirst("[\\r\\n]+$", "");
                }
            }
            if (!command.trim().isEmpty()) result.add(new Entry(command, start, offset));
        }
        return result;
    }

    private static String decodeFish(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'n') result.append('\n');
                else if (next == '\\') result.append('\\');
                else result.append('\\').append(next);
            } else result.append(c);
        }
        return result.toString();
    }

    public static String deleteHistory(String text, Entry entry) {
        int start = entry.start;
        // Bash timestamps belong to the following command.
        if (start > 0) {
            int previous = text.lastIndexOf('\n', start - 2) + 1;
            if (text.substring(previous, start).trim().matches("#\\d+")) start = previous;
        }
        return text.substring(0, start) + text.substring(entry.end);
    }

    /** Subsequence matching, with a preference for contiguous and early matches. */
    public static int score(String text, String query) {
        text = text.toLowerCase(java.util.Locale.ROOT);
        query = query.toLowerCase(java.util.Locale.ROOT).trim();
        int position = -1, score = 0;
        for (int i = 0; i < query.length(); i++) {
            int next = text.indexOf(query.charAt(i), position + 1);
            if (next < 0) return -1;
            score += next == position + 1 ? 10 : 1;
            score -= next - position - 1;
            position = next;
        }
        return score + 10000;
    }
}
