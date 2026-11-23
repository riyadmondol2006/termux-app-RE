package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AptPackageParser {

    private static final Pattern APT_LIST_LINE = Pattern.compile(
        "^([^/\\s]+)/(\\S+)\\s+(\\S+)\\s+(\\S+)(?:\\s+\\[(.*)])?$");

    private AptPackageParser() {}

    @NonNull
    public static List<PackageInfo> parseAptList(@NonNull String output) {
        Map<String, PackageInfo> packages = new LinkedHashMap<>();
        for (String line : output.split("\\r?\\n")) {
            Matcher matcher = APT_LIST_LINE.matcher(line.trim());
            if (!matcher.matches()) continue;
            String name = matcher.group(1);
            String state = matcher.group(5);
            boolean installed = state != null && state.toLowerCase(Locale.ROOT).contains("installed");
            boolean upgradable = state != null && state.toLowerCase(Locale.ROOT).contains("upgradable");
            packages.put(name, new PackageInfo(name, matcher.group(3), matcher.group(4), null,
                installed, upgradable));
        }
        return new ArrayList<>(packages.values());
    }

    @NonNull
    public static List<PackageInfo> parseSearch(@NonNull String output) {
        Map<String, PackageInfo> packages = new LinkedHashMap<>();
        for (String line : output.split("\\r?\\n")) {
            int separator = line.indexOf(" - ");
            String name = separator >= 0 ? line.substring(0, separator).trim() : line.trim();
            if (name.isEmpty() || name.contains(" ")) continue;
            String description = separator >= 0 ? line.substring(separator + 3).trim() : null;
            packages.put(name, new PackageInfo(name, null, null, description, false, false));
        }
        return new ArrayList<>(packages.values());
    }

    @NonNull
    public static PackageDetails parseShow(@NonNull String output, @NonNull String requestedName) {
        Map<String, String> fields = new LinkedHashMap<>();
        String currentField = null;
        for (String line : output.split("\\r?\\n", -1)) {
            if (line.isEmpty() && !fields.isEmpty()) break;
            if ((line.startsWith(" ") || line.startsWith("\t")) && currentField != null) {
                String continuation = line.trim();
                if (continuation.equals(".")) continuation = "";
                fields.put(currentField, fields.get(currentField) + "\n" + continuation);
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            currentField = line.substring(0, colon);
            fields.put(currentField, line.substring(colon + 1).trim());
        }
        String name = fields.getOrDefault("Package", requestedName);
        return new PackageDetails(name, fields);
    }

}
