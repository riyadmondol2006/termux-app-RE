package com.termux.app.features;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static org.junit.Assert.*;

public class FeatureToolsModelTest {
    private final List<FeatureToolsModel.Section<String>> sections = Arrays.asList(
        new FeatureToolsModel.Section<>(1, Arrays.asList("Theme", "Font")),
        new FeatureToolsModel.Section<>(2, Collections.emptyList()),
        new FeatureToolsModel.Section<>(3, Arrays.asList("Files", "Git")));

    @Test public void captureKeepsFlatOrderAndSnapshotsEachRegistration() {
        List<String> all = new ArrayList<>();
        FeatureToolsModel.Section<String> first = FeatureToolsModel.registerSection(1, all,
            items -> items.addAll(Arrays.asList("Theme", "Font")));
        FeatureToolsModel.Section<String> second = FeatureToolsModel.registerSection(3, all,
            items -> items.add("Files"));
        assertEquals(Arrays.asList("Theme", "Font", "Files"), all);
        assertEquals(Arrays.asList("Theme", "Font"), first.features);
        assertEquals(Collections.singletonList("Files"), second.features);
        assertEquals(3, second.title);
        try { first.features.add("Changed"); fail("Section must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void blankQueryPreservesOrderAndHidesUnavailableAndEmptySections() {
        List<FeatureToolsModel.Section<String>> result = FeatureToolsModel.filter(sections, "  ",
            value -> !value.equals("Theme"), value -> value, value -> "Summary");
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).title);
        assertEquals(Collections.singletonList("Font"), result.get(0).features);
        assertEquals(Arrays.asList("Files", "Git"), result.get(1).features);
        assertEquals(2, sections.get(0).features.size());
    }

    @Test public void searchesTitleAndSummaryIgnoringCaseAndDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            List<FeatureToolsModel.Section<String>> title = FeatureToolsModel.filter(sections, " FI ",
                value -> true, value -> value, value -> "");
            assertEquals(1, title.size());
            assertEquals(Collections.singletonList("Files"), title.get(0).features);
            List<FeatureToolsModel.Section<String>> summary = FeatureToolsModel.filter(sections, "BRANCH",
                value -> true, value -> value, value -> value.equals("Git") ? "Manage branches" : "");
            assertEquals(Collections.singletonList("Git"), summary.get(0).features);
        } finally { Locale.setDefault(previous); }
    }

    @Test public void noMatchesOrNoAvailableToolsProducesEmptyResult() {
        assertTrue(FeatureToolsModel.filter(sections, "missing", value -> true,
            value -> value, value -> "").isEmpty());
        assertTrue(FeatureToolsModel.filter(sections, "", value -> false,
            value -> value, value -> "").isEmpty());
    }
}
