package com.termux.app.features.registry;

import androidx.annotation.NonNull;

import com.termux.app.features.TermuxFeature;
import com.termux.app.features.sessions.SessionFeatureEntries;
import com.termux.app.features.sessions.WorkspacesFeature;

import java.util.List;

/** Features for: Session workspaces, session persistence, terminal profiles, startup automation, command completion notifications. */
public final class SessionFeatures {

    private SessionFeatures() {}

    /** Add the features of this area to {@code features}, in the order they should be listed. */
    public static void register(@NonNull List<TermuxFeature> features) {
        features.add(new WorkspacesFeature());
        features.add(new SessionFeatureEntries.Profiles());
        features.add(new SessionFeatureEntries.StartupAutomation());
    }

}
