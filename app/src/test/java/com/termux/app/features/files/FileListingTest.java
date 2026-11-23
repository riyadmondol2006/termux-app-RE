package com.termux.app.features.files;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class FileListingTest {
    private static FileEntry file(String name, long size, long modified) {
        return new FileEntry(new File("/t/" + name), false, false, size, modified, FileEntry.UNKNOWN);
    }
    private static FileEntry dir(String name, long modified) {
        return new FileEntry(new File("/t/" + name), true, false, 0, modified, 0);
    }
    private static List<String> names(List<FileEntry> entries) {
        List<String> result = new ArrayList<>();
        for (FileEntry e : entries) result.add(e.name);
        return result;
    }

    @Test public void foldersComeFirstThenNamesIgnoringCase() {
        List<FileEntry> entries = new ArrayList<>(Arrays.asList(file("b.txt", 1, 1), dir("Zeta", 1), file("A.txt", 1, 1), dir("alpha", 1)));
        FileListing.sort(entries, FileListing.SortOrder.NAME);
        assertEquals(Arrays.asList("alpha", "Zeta", "A.txt", "b.txt"), names(entries));
    }

    @Test public void newestFirstKeepsFoldersOnTop() {
        List<FileEntry> entries = new ArrayList<>(Arrays.asList(file("old", 1, 10), dir("d", 5), file("new", 1, 30), file("mid", 1, 20)));
        FileListing.sort(entries, FileListing.SortOrder.NEWEST);
        assertEquals(Arrays.asList("d", "new", "mid", "old"), names(entries));
    }

    @Test public void largestFirstUsesNameForEqualSizes() {
        List<FileEntry> entries = new ArrayList<>(Arrays.asList(file("b", 5, 1), file("a", 5, 1), file("big", 500, 1), dir("y", 1), dir("x", 1)));
        FileListing.sort(entries, FileListing.SortOrder.LARGEST);
        assertEquals(Arrays.asList("x", "y", "big", "a", "b"), names(entries));
    }

    @Test public void filterIgnoresCaseAndSurroundingSpaces() {
        List<FileEntry> entries = Arrays.asList(file("README.md", 1, 1), file("notes.txt", 1, 1), dir("Readers", 1));
        assertEquals(Arrays.asList("README.md", "Readers"), names(FileListing.filter(entries, "  read ")));
        assertEquals(3, FileListing.filter(entries, "").size());
        assertEquals(3, FileListing.filter(entries, null).size());
        assertTrue(FileListing.filter(entries, "zzz").isEmpty());
    }
}
