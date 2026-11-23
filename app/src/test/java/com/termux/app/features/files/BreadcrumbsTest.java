package com.termux.app.features.files;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BreadcrumbsTest {
    private static final String HOME = "/data/data/com.termux.re/files/home";
    private static final String PREFIX = "/data/data/com.termux.re/files/usr";

    private static Breadcrumbs.Crumb crumb(String label, String path) { return new Breadcrumbs.Crumb(label, path); }

    @Test public void homeFolderStartsWithTilde() {
        assertEquals(Arrays.asList(crumb("~", HOME)), Breadcrumbs.parse(HOME, HOME, PREFIX));
        assertEquals(Arrays.asList(crumb("~", HOME), crumb("projects", HOME + "/projects"), crumb("app", HOME + "/projects/app")),
            Breadcrumbs.parse(HOME + "/projects/app", HOME, PREFIX));
    }

    @Test public void prefixFolderStartsWithPrefix() {
        assertEquals(Arrays.asList(crumb("$PREFIX", PREFIX), crumb("bin", PREFIX + "/bin")), Breadcrumbs.parse(PREFIX + "/bin", HOME, PREFIX));
    }

    @Test public void otherPathsStartWithSlash() {
        assertEquals(Arrays.asList(crumb("/", "/"), crumb("storage", "/storage"), crumb("emulated", "/storage/emulated"), crumb("0", "/storage/emulated/0")),
            Breadcrumbs.parse("/storage/emulated/0", HOME, PREFIX));
        assertEquals(Arrays.asList(crumb("/", "/")), Breadcrumbs.parse("/", HOME, PREFIX));
    }

    @Test public void parentOfHomeIsNotHome() {
        List<Breadcrumbs.Crumb> crumbs = Breadcrumbs.parse("/data/data/com.termux.re/files", HOME, PREFIX);
        assertEquals("/", crumbs.get(0).label);
        assertEquals("files", crumbs.get(crumbs.size() - 1).label);
        // A folder that only starts with the same letters is not inside home.
        assertEquals("/", Breadcrumbs.parse(HOME + "-old/x", HOME, PREFIX).get(0).label);
    }

    @Test public void trailingSlashIsIgnored() {
        assertEquals(Breadcrumbs.parse(HOME + "/a", HOME, PREFIX), Breadcrumbs.parse(HOME + "/a/", HOME + "/", PREFIX));
    }

    @Test public void backLeavesAtTheTopOfALocation() {
        List<String> roots = Arrays.asList(HOME, PREFIX);
        assertTrue(Breadcrumbs.isTop(HOME, roots));
        assertTrue(Breadcrumbs.isTop(PREFIX + "/", roots));
        assertTrue(Breadcrumbs.isTop("/", roots));
        assertFalse(Breadcrumbs.isTop(HOME + "/projects", roots));
    }
}
