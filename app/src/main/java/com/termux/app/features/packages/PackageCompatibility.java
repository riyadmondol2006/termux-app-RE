package com.termux.app.features.packages;

import androidx.annotation.NonNull;

public final class PackageCompatibility {

    public static final String OFFICIAL_PREFIX = "/data/data/com.termux/";
    public static final String RE_PREFIX = "/data/data/com.termux.re/";

    private PackageCompatibility() {}

    public static boolean hasForeignPrefix(@NonNull String path) {
        return path.contains(OFFICIAL_PREFIX) && !path.contains(RE_PREFIX);
    }

    @NonNull
    public static String extractPath(@NonNull String dpkgContentsLine) {
        String trimmed = dpkgContentsLine.trim();
        int official = trimmed.indexOf(OFFICIAL_PREFIX);
        if (official > 0 && trimmed.charAt(official - 1) == '.') official--;
        if (official >= 0) return trimmed.substring(official);
        int lastSpace = trimmed.lastIndexOf(' ');
        return lastSpace >= 0 ? trimmed.substring(lastSpace + 1) : trimmed;
    }

}
