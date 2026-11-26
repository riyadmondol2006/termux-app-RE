package com.termux.app.features.input;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.StringReader;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;

public class InputFilesTest {
    @Test
    public void fuzzyMatchingAllowsGapsAndPrefersContiguousMatches() {
        assertTrue(InputFiles.score("New session", "ns") >= 0);
        assertTrue(
                InputFiles.score("next session", "next")
                        > InputFiles.score("new extra terminal", "next"));
        assertEquals(-1, InputFiles.score("Settings", "xyz"));
        assertEquals(InputFiles.score("SETTINGS", "SET"), InputFiles.score("settings", "set"));
    }

    @Test
    public void propertyUpdatePreservesCommentsAndContinuationOfOtherProperties() throws Exception {
        String original =
                "# Keep this\r\n"
                    + "extra-keys = [['ESC'],\\\r\n"
                    + " ['TAB']]\r\n"
                    + "# extra-keys = disabled\r\n"
                    + "other=hello\\\n"
                    + " world\n"
                    + "extra-keys-style: all\n";
        String json = "[[{\"key\":\"a\\nb\\\\c\"}]]";
        String result = InputFiles.keyboardProperties(original, json, "none");
        assertTrue(result.contains("# Keep this\r\n"));
        assertTrue(result.contains("other=hello\\\n world\n"));
        assertTrue(result.contains("# extra-keys = disabled"));
        assertFalse(result.contains(" ['TAB']]"));
        Properties properties = new Properties();
        properties.load(new StringReader(result));
        assertEquals(json, properties.getProperty("extra-keys"));
        assertEquals("none", properties.getProperty("extra-keys-style"));
    }

    @Test
    public void bashDeletionIncludesTimestampAndOnlySelectedDuplicate() {
        String history = "#123\necho hello\n#124\necho hello\n";
        List<InputFiles.Entry> entries = InputFiles.history(history, "bash");
        assertEquals(2, entries.size());
        assertEquals("#124\necho hello\n", InputFiles.deleteHistory(history, entries.get(0)));
    }

    @Test
    public void zshExtendedHistoryAndFishMetadata() {
        assertEquals(
                "echo hello", InputFiles.history(": 123:0;echo hello\n", "zsh").get(0).command);
        String fish =
                "- cmd: echo one\\n"
                    + "echo two\n"
                    + "  when: 123\n"
                    + "  paths:\n"
                    + "    - /tmp\n"
                    + "- cmd: pwd\n"
                    + "  when: 124\n";
        List<InputFiles.Entry> entries = InputFiles.history(fish, "fish");
        assertEquals(2, entries.size());
        assertEquals("echo one\necho two", entries.get(0).command);
        assertEquals("- cmd: pwd\n  when: 124\n", InputFiles.deleteHistory(fish, entries.get(0)));
    }

    @Test
    public void multilineHistoryStaysOneCommand() {
        String bash = "#123\nfor i in a b; do\n echo $i\ndone\n#124\npwd\n";
        List<InputFiles.Entry> entries = InputFiles.history(bash, "bash");
        assertEquals(2, entries.size());
        assertEquals("for i in a b; do\n echo $i\ndone", entries.get(0).command);
        assertEquals("#124\npwd\n", InputFiles.deleteHistory(bash, entries.get(0)));
        assertEquals(
                "echo one\necho two",
                InputFiles.history(": 123:0;echo one\\\necho two\n", "zsh").get(0).command);
    }

    @Test
    public void fishLiteralBackslashNIsNotDecodedAsNewline() {
        assertEquals(
                "echo \\n",
                InputFiles.history("- cmd: echo \\\\n\n  when: 1\n", "fish").get(0).command);
    }

    @Test
    public void atomicReplaceMakesBackup() throws Exception {
        Path dir = Files.createTempDirectory("input-test");
        java.io.File file = dir.resolve("termux.properties").toFile();
        try {
            InputFiles.replace(file, "original", true);
            InputFiles.replace(file, "updated", true);
            assertEquals("original", InputFiles.read(dir.resolve("termux.properties.bak").toFile()));
            assertEquals("updated", InputFiles.read(file));
            assertEquals("", InputFiles.read(dir.resolve("missing").toFile()));
            // Only the file and its backup are left, no temporary files.
            assertEquals(2, dir.toFile().list().length);
        } finally {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(dir.resolve("termux.properties.bak"));
            Files.deleteIfExists(dir);
        }
    }
}
