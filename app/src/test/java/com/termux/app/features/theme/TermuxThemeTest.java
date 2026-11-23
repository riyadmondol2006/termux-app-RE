package com.termux.app.features.theme;

import org.junit.Test;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.*;

public class TermuxThemeTest {

    @Test public void hexRoundTrip() {
        assertEquals("#FFCC66", TermuxTheme.toHex(0xFFFFCC66));
        assertEquals(Integer.valueOf(0xFFFFCC66), TermuxTheme.parseHex("#FFCC66"));
        assertEquals(Integer.valueOf(0xFFFFCC66), TermuxTheme.parseHex("ffcc66"));
        assertNull(TermuxTheme.parseHex("#xyz"));
        assertNull(TermuxTheme.parseHex(null));
    }

    @Test public void propertiesRoundTrip() throws Exception {
        TermuxTheme t = BundledThemes.all().get(1);
        Properties p = new Properties();
        p.load(new java.io.StringReader(t.toColorsProperties().replace("# Termux RE theme:", "#")));
        // Properties.load skips comment lines; reload properly:
        p.clear();
        p.load(new java.io.StringReader(t.toColorsProperties()));
        TermuxTheme back = TermuxTheme.fromProperties(t.id, t.name, p, BundledThemes.defaultTheme());
        assertEquals(t.foreground, back.foreground);
        assertEquals(t.background, back.background);
        assertEquals(t.cursor, back.cursor);
        assertArrayEquals(t.ansi, back.ansi);
    }

    @Test public void jsonRoundTrip() throws Exception {
        TermuxTheme t = BundledThemes.all().get(2);
        TermuxTheme back = TermuxTheme.fromJson(TermuxThemeManager.themeJson(t));
        assertEquals(t, back);
    }

    @Test public void bundledIdsUniqueAndComplete() {
        Set<String> ids = new HashSet<>();
        for (TermuxTheme t : BundledThemes.all()) {
            assertTrue(ids.add(t.id));
            assertEquals(16, t.ansi.length);
        }
        assertTrue(BundledThemes.all().size() >= 4);
    }

    @Test public void slugifyWorks() {
        assertEquals("my-theme", TermuxTheme.slugify("My Theme!"));
        assertEquals("custom", TermuxTheme.slugify("!!!"));
    }
}
