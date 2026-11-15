package com.termux.app.api.file;

import com.termux.app.api.file.FileReceiverActivity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class FileReceiverActivityTest {

    @Test
    public void testIsSharedTextAnUrl() {
        List<String> validUrls = new ArrayList<>();
        validUrls.add("http://example.com");
        validUrls.add("https://example.com");
        validUrls.add("https://example.com/path/parameter=foo");
        validUrls.add("magnet:?xt=urn:btih:d540fc48eb12f2833163eed6421d449dd8f1ce1f&dn=Ubuntu+desktop+19.04+%2864bit%29&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80&tr=udp%3A%2F%2Ftracker.ccc.de%3A80");
        for (String url : validUrls) {
            Assert.assertTrue(FileReceiverActivity.isSharedTextAnUrl(url));
        }

        List<String> invalidUrls = new ArrayList<>();
        invalidUrls.add("a test with example.com");
        invalidUrls.add("");
        invalidUrls.add(null);
        for (String url : invalidUrls) {
            Assert.assertFalse(FileReceiverActivity.isSharedTextAnUrl(url));
        }
    }

    @Test
    public void testTruncateFileName() {
        Assert.assertNull(FileReceiverActivity.truncateFileName(null));
        Assert.assertEquals("", FileReceiverActivity.truncateFileName(""));
        Assert.assertEquals("subject.txt", FileReceiverActivity.truncateFileName("subject.txt"));

        String maxLengthName = repeat("a", FileReceiverActivity.MAX_FILE_NAME_BYTES - 4) + ".txt";
        Assert.assertEquals(maxLengthName, FileReceiverActivity.truncateFileName(maxLengthName));

        // A long subject of a shared text, the extension is kept.
        String truncated = FileReceiverActivity.truncateFileName(repeat("a", 6000) + ".txt");
        Assert.assertEquals(maxLengthName, truncated);

        // Multi-byte characters are not split and the name fits in NAME_MAX bytes.
        truncated = FileReceiverActivity.truncateFileName(repeat("\u00e9", 3000) + ".txt");
        Assert.assertTrue(truncated.endsWith(".txt"));
        Assert.assertTrue(utf8Length(truncated) <= FileReceiverActivity.MAX_FILE_NAME_BYTES);
        Assert.assertEquals(repeat("\u00e9", 125) + ".txt", truncated);

        truncated = FileReceiverActivity.truncateFileName(repeat("\uD83D\uDE00", 100) + ".txt");
        Assert.assertEquals(repeat("\uD83D\uDE00", 62) + ".txt", truncated);

        // A long part after the last dot is not an extension and is truncated with the rest.
        truncated = FileReceiverActivity.truncateFileName("name." + repeat("b", 300));
        Assert.assertEquals(FileReceiverActivity.MAX_FILE_NAME_BYTES, utf8Length(truncated));
        Assert.assertTrue(truncated.startsWith("name.bbb"));

        // A name without an extension.
        truncated = FileReceiverActivity.truncateFileName(repeat("c", 300));
        Assert.assertEquals(repeat("c", FileReceiverActivity.MAX_FILE_NAME_BYTES), truncated);
    }

    private static String repeat(String string, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++)
            builder.append(string);
        return builder.toString();
    }

    private static int utf8Length(String string) {
        return string.getBytes(StandardCharsets.UTF_8).length;
    }

}
