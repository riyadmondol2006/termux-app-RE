package com.termux.app.features.sessions;

import androidx.annotation.NonNull;

/** A named group of terminal sessions in the drawer. */
public final class Workspace {

    /** The id of the default workspace, which sessions belong to unless moved to another one. */
    public static final String DEFAULT_ID = "default";

    @NonNull public final String id;
    @NonNull public final String name;

    public Workspace(@NonNull String id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }

    public boolean isDefault() {
        return DEFAULT_ID.equals(id);
    }

}
