package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class CompatibilityParser {

    private CompatibilityParser() {}

    @NonNull
    public static List<CompatibilityIssue> parseInstalledScan(@NonNull String output) {
        List<CompatibilityIssue> issues = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            String path = line.substring(tab + 1).trim();
            if (PackageCompatibility.hasForeignPrefix(path))
                issues.add(new CompatibilityIssue(line.substring(0, tab), path));
        }
        return issues;
    }

    @NonNull
    public static List<CompatibilityIssue> parseDebContents(@NonNull String output, @NonNull String packageName) {
        List<CompatibilityIssue> issues = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (PackageCompatibility.hasForeignPrefix(line))
                issues.add(new CompatibilityIssue(packageName, PackageCompatibility.extractPath(line)));
        }
        return issues;
    }

}
