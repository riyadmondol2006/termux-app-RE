package com.termux.app.features.registry;

import androidx.annotation.NonNull;

import com.termux.app.features.TermuxFeature;
import com.termux.app.features.packages.CompatibilityToolsFeature;
import com.termux.app.features.packages.PackageManagerFeature;
import com.termux.app.features.packages.RepositoryManagerFeature;

import java.util.List;

/** Features for: Visual package manager, repository manager, package compatibility tools. */
public final class PackageFeatures {

    private PackageFeatures() {}

    /** Add the features of this area to {@code features}, in the order they should be listed. */
    public static void register(@NonNull List<TermuxFeature> features) {
        features.add(new PackageManagerFeature());
        features.add(new RepositoryManagerFeature());
        features.add(new CompatibilityToolsFeature());
    }

}
