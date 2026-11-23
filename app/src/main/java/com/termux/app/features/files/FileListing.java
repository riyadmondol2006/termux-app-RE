package com.termux.app.features.files;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure sorting and filtering of the folder content shown by the file manager. */
final class FileListing {
    private FileListing() {}

    enum SortOrder { NAME, NEWEST, LARGEST }

    /** Returns the entries whose name contains {@code query}, ignoring case. An empty query keeps everything. */
    static List<FileEntry> filter(List<FileEntry> entries, String query) {
        List<FileEntry> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (FileEntry entry : entries)
            if (needle.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
        return result;
    }

    /** Sorts in place. Folders come first, then the chosen order, then the name as the tie breaker. */
    static void sort(List<FileEntry> entries, SortOrder order) {
        Comparator<FileEntry> byName = (a, b) -> a.name.compareToIgnoreCase(b.name);
        Comparator<FileEntry> chosen;
        switch (order) {
            case NEWEST: chosen = (a, b) -> Long.compare(b.modified, a.modified); break;
            case LARGEST: chosen = (a, b) -> Long.compare(b.size, a.size); break;
            default: chosen = byName; break;
        }
        entries.sort((a, b) -> {
            if (a.directory != b.directory) return a.directory ? -1 : 1;
            int result = order == SortOrder.LARGEST && a.directory ? 0 : chosen.compare(a, b);
            return result != 0 ? result : byName.compare(a, b);
        });
    }
}
