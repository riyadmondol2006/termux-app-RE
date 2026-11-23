package com.termux.app.features;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.features.registry.DevToolsFeatures;
import com.termux.app.features.registry.FileFeatures;
import com.termux.app.features.registry.InputFeatures;
import com.termux.app.features.registry.PackageFeatures;
import com.termux.app.features.registry.SessionFeatures;
import com.termux.app.features.registry.SystemFeatures;
import com.termux.app.features.registry.TerminalViewFeatures;
import com.termux.app.features.registry.ThemeFeatures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All {@link TermuxFeature}s of the app.
 *
 * To add a feature, add it in the {@code register()} method of the registry class for its area.
 * Do not edit this class for that, so features of different areas don't conflict.
 */
public final class FeatureRegistry {

    private static List<TermuxFeature> sFeatures;

    private FeatureRegistry() {}

    /** Get all features, in the order they are listed. */
    @NonNull
    public static synchronized List<TermuxFeature> getFeatures() {
        if (sFeatures == null) {
            List<TermuxFeature> features = new ArrayList<>();
            ThemeFeatures.register(features);
            SessionFeatures.register(features);
            TerminalViewFeatures.register(features);
            InputFeatures.register(features);
            PackageFeatures.register(features);
            FileFeatures.register(features);
            DevToolsFeatures.register(features);
            SystemFeatures.register(features);
            sFeatures = Collections.unmodifiableList(features);
        }
        return sFeatures;
    }

    /** Get the feature with the {@code id}, or {@code null}. */
    @Nullable
    public static TermuxFeature getFeature(@Nullable String id) {
        if (id == null) return null;
        for (TermuxFeature feature : getFeatures()) {
            if (feature.getId().equals(id)) return feature;
        }
        return null;
    }

}
