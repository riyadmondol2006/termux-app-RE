package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.Locale;

public final class RepositorySafety {

    private RepositorySafety() {}

    public static boolean isOfficialTermuxRepository(@NonNull String value) {
        String host;
        try {
            host = URI.create(value.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("packages.termux.dev") || host.equals("packages-cf.termux.dev") ||
            host.equals("termux.dev") || host.endsWith(".termux.dev") ||
            host.equals("termux.org") || host.endsWith(".termux.org") ||
            host.equals("termux.net") || host.endsWith(".termux.net") ||
            host.equals("grimler.se") || host.endsWith(".grimler.se") ||
            host.equals("termux.librehat.com") || host.equals("mirrors.termux.dev");
    }

}
