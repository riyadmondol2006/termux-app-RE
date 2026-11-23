package com.termux.app.features.files;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

public class FileRulesTest {
    @Test public void traversalRejected() {
        for (String path : new String[]{"/home/x", "home/../x", "home//x", "usr/./bin", "settings/", "unknown/x", "home/a/../../x"}) {
            try { FileRules.archivePath(path); fail(path); } catch (IOException expected) {}
        }
    }
    @Test public void validPaths() throws Exception {
        assertEquals("home/.termux/font.ttf", FileRules.archivePath("home/.termux/font.ttf"));
        assertEquals("usr/bin/sh", FileRules.archivePath("usr/bin/sh"));
    }
    @Test public void cacheExclusionUsesComponents() {
        assertTrue(FileRules.excluded("home/.cache/data", true));
        assertFalse(FileRules.excluded("home/cache-notes", true));
        assertFalse(FileRules.excluded("home/.cache/data", false));
    }
    @Test public void recursiveCopyRejected() throws Exception {
        try { FileRules.destination(new File("/tmp/source"), new File("/tmp/source/child")); fail(); }
        catch (IOException expected) {}
        FileRules.destination(new File("/tmp/source"), new File("/tmp/source-other"));
    }
}
