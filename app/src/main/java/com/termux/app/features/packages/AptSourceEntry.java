package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.io.File;

public final class AptSourceEntry {

    @NonNull public final File file;
    public final int line;
    public final boolean enabled;
    @NonNull public final String declaration;

    AptSourceEntry(@NonNull File file, int line, boolean enabled, @NonNull String declaration) {
        this.file = file;
        this.line = line;
        this.enabled = enabled;
        this.declaration = declaration;
    }

}
