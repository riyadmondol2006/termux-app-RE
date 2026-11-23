package com.termux.app.features.files;

import java.util.Locale;

/** Pure text formatting for file sizes. */
final class FileFormat {
    private FileFormat() {}

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** Formats a size in bytes like "812 B", "1.5 KB" or "20 MB", using 1024 as the unit step. */
    static String size(long bytes, Locale locale) {
        if (bytes < 0) bytes = 0;
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) { value /= 1024; unit++; }
        if (unit == 0) return bytes + " " + UNITS[0];
        // One decimal below 10 ("1.5 KB"), none above ("20 MB"), and no ".0".
        String text = value < 10 ? String.format(locale, "%.1f", value) : String.format(locale, "%.0f", value);
        if (value < 10 && text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + " " + UNITS[unit];
    }
}
