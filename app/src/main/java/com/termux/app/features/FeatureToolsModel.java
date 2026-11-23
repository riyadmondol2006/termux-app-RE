package com.termux.app.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Android-independent section capture and search; labels are opaque resource IDs. */
public final class FeatureToolsModel {
    private FeatureToolsModel() {}

    public static final class Section<T> {
        public final int title;
        public final List<T> features;

        public Section(int title, List<T> features) {
            this.title = title;
            this.features = Collections.unmodifiableList(new ArrayList<>(features));
        }
    }

    /** Capture exactly the features appended by one area's registration call. */
    public static <T> Section<T> registerSection(int title, List<T> all, Consumer<List<T>> register) {
        int start = all.size();
        register.accept(all);
        return new Section<>(title, all.subList(start, all.size()));
    }

    /** Keep source order, omit unavailable tools and remove sections without matches. */
    public static <T> List<Section<T>> filter(List<Section<T>> sections, String query,
            Predicate<T> available, Function<T, String> title, Function<T, String> summary) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Section<T>> result = new ArrayList<>();
        for (Section<T> section : sections) {
            List<T> matches = new ArrayList<>();
            for (T feature : section.features) {
                if (available.test(feature) && (needle.isEmpty()
                        || title.apply(feature).toLowerCase(Locale.ROOT).contains(needle)
                        || summary.apply(feature).toLowerCase(Locale.ROOT).contains(needle))) {
                    matches.add(feature);
                }
            }
            if (!matches.isEmpty()) result.add(new Section<>(section.title, matches));
        }
        return Collections.unmodifiableList(result);
    }
}
