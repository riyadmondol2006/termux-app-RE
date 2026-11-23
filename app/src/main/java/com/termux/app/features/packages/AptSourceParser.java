package com.termux.app.features.packages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class AptSourceParser {

    private AptSourceParser() {}

    @Nullable
    public static ParsedSource parse(@NonNull String line) {
        String trimmed = line.trim();
        boolean enabled = true;
        if (trimmed.startsWith("#")) {
            enabled = false;
            trimmed = trimmed.substring(1).trim();
        }
        if (!trimmed.startsWith("deb ") && !trimmed.startsWith("deb-src ")) return null;
        String[] fields = trimmed.split("\\s+");
        if (fields.length < 4) return null;
        return new ParsedSource(enabled, trimmed);
    }

    public static void setEnabled(@NonNull List<String> lines, int index, boolean enabled) {
        String line = lines.get(index);
        ParsedSource source = parse(line);
        if (source == null || source.enabled == enabled) return;
        lines.set(index, enabled ? source.declaration : "# " + source.declaration);
    }

    public static final class ParsedSource {
        public final boolean enabled;
        @NonNull public final String declaration;

        ParsedSource(boolean enabled, @NonNull String declaration) {
            this.enabled = enabled;
            this.declaration = declaration;
        }
    }

}
