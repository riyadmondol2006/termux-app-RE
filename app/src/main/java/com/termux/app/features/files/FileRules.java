package com.termux.app.features.files;

import java.io.File;
import java.io.IOException;

/** Pure path rules shared by archive validation and the file browser. */
public final class FileRules {
    private FileRules() {}

    public static String name(String value) throws IOException {
        if (value.isEmpty() || value.equals(".") || value.equals("..") ||
            value.contains("/") || value.indexOf('\0') >= 0)
            throw new IOException("Enter a single file name");
        return value;
    }

    public static String archivePath(String path) throws IOException {
        String[] parts = path.split("/", -1);
        if (parts.length < 2 || !(parts[0].equals("home") || parts[0].equals("usr") ||
            parts[0].equals("settings"))) throw new IOException("Unknown archive root: " + path);
        for (String part : parts) name(part);
        return path;
    }

    public static boolean excluded(String path, boolean caches) {
        if (!caches) return false;
        for (String part : path.split("/"))
            if (part.equals(".cache") || part.equals("cache") || part.equals("tmp")) return true;
        return false;
    }

    public static void destination(File source, File target) throws IOException {
        String src = source.getCanonicalPath(), dst = target.getCanonicalPath();
        if (dst.equals(src) || dst.startsWith(src + "/"))
            throw new IOException("Cannot copy a directory into itself");
    }
}
