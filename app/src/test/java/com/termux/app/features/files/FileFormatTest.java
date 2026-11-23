package com.termux.app.features.files;

import static org.junit.Assert.*;

import java.util.Locale;
import org.junit.Test;

public class FileFormatTest {
    @Test public void bytesAreShownAsIs() {
        assertEquals("0 B", FileFormat.size(0, Locale.US));
        assertEquals("1023 B", FileFormat.size(1023, Locale.US));
        assertEquals("0 B", FileFormat.size(-5, Locale.US));
    }

    @Test public void unitsStepByOneKibibyte() {
        assertEquals("1 KB", FileFormat.size(1024, Locale.US));
        assertEquals("1.5 KB", FileFormat.size(1536, Locale.US));
        assertEquals("20 KB", FileFormat.size(20 * 1024, Locale.US));
        assertEquals("5 MB", FileFormat.size(5L * 1024 * 1024, Locale.US));
        assertEquals("2.3 GB", FileFormat.size((long) (2.3 * 1024 * 1024 * 1024), Locale.US));
    }

    @Test public void usesTheDecimalSeparatorOfTheLocale() {
        assertEquals("1,5 KB", FileFormat.size(1536, Locale.GERMANY));
    }

    @Test public void hugeSizesStopAtTerabytes() {
        assertEquals("8192 TB", FileFormat.size(8L * 1024 * 1024 * 1024 * 1024 * 1024, Locale.US));
    }
}
