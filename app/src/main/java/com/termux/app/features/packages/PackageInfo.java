package com.termux.app.features.packages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class PackageInfo {

    @NonNull public final String name;
    @Nullable public final String version;
    @Nullable public final String architecture;
    @Nullable public final String description;
    public final boolean installed;
    public final boolean upgradable;

    public PackageInfo(@NonNull String name, @Nullable String version, @Nullable String architecture,
                       @Nullable String description, boolean installed, boolean upgradable) {
        this.name = name;
        this.version = version;
        this.architecture = architecture;
        this.description = description;
        this.installed = installed;
        this.upgradable = upgradable;
    }

    @NonNull
    public PackageInfo withStates(boolean isInstalled, boolean isUpgradable) {
        return new PackageInfo(name, version, architecture, description, isInstalled, isUpgradable);
    }

}
