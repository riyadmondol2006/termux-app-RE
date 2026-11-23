package com.termux.app.features.registry;

import androidx.annotation.NonNull;

import com.termux.app.features.TermuxFeature;

import java.util.List;

/** Features for: File manager, backup and restore. */
public final class FileFeatures {

    private FileFeatures() {}

    /** Add the features of this area to {@code features}, in the order they should be listed. */
    public static void register(@NonNull List<TermuxFeature> features) {
        features.add(new com.termux.app.features.files.FileManagerFeature());
        features.add(new com.termux.app.features.files.BackupFeature());
    }

}
