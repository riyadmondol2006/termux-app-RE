package com.termux.app.features.packages;

import androidx.annotation.NonNull;

import java.io.File;

final class MirrorOption {

    @NonNull final String name;
    @NonNull final File target;

    MirrorOption(@NonNull String name, @NonNull File target) {
        this.name = name;
        this.target = target;
    }

}
