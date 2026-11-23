package com.termux.app.features.packages;

import androidx.annotation.NonNull;

public final class CompatibilityIssue {

    @NonNull public final String packageName;
    @NonNull public final String path;

    CompatibilityIssue(@NonNull String packageName, @NonNull String path) {
        this.packageName = packageName;
        this.path = path;
    }

}
