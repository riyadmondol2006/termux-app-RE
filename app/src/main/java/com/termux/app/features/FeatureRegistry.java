package com.termux.app.features;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
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
    private static List<FeatureToolsModel.Section<TermuxFeature>> sSections;

    private FeatureRegistry() {}

    /** Get all features, in the order they are listed. */
    @NonNull
    public static synchronized List<TermuxFeature> getFeatures() {
        if (sFeatures == null) {
            List<TermuxFeature> features = new ArrayList<>();
            List<FeatureToolsModel.Section<TermuxFeature>> sections = new ArrayList<>();
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_appearance, features, ThemeFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_sessions, features, SessionFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_terminal, features, TerminalViewFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_input, features, InputFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_packages, features, PackageFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_files, features, FileFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_developer, features, DevToolsFeatures::register));
            sections.add(FeatureToolsModel.registerSection(R.string.tools_section_system, features, SystemFeatures::register));
            sSections = Collections.unmodifiableList(sections);
            sFeatures = Collections.unmodifiableList(features);
        }
        return sFeatures;
    }

    /** Areas in registration order, including their original feature instances. */
    @NonNull
    public static synchronized List<FeatureToolsModel.Section<TermuxFeature>> getSections() {
        getFeatures();
        return sSections;
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
