package com.termux.app.features.terminal.search;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
public class TerminalSearchSnapshotTest {

    private static final class NullOutput extends TerminalOutput {
        @Override public void write(byte[] data, int offset, int count) {}
        @Override public void titleChanged(String oldTitle, String newTitle) {}
        @Override public void onCopyTextToClipboard(String text) {}
        @Override public void onPasteTextFromClipboard() {}
        @Override public void onBell() {}
        @Override public void onColorsChanged() {}
    }

    private static void append(TerminalEmulator emulator, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    @Test
    public void testCaptureAndSearchScrollback() {
        TerminalEmulator emulator = new TerminalEmulator(new NullOutput(), 10, 5, 10, 20, 1000, null);
        for (int i = 0; i < 20; i++) append(emulator, "line " + i + "\r\n");
        // A line wrapped over two rows.
        append(emulator, "0123456789wrapped");

        TerminalBuffer buffer = emulator.getScreen();
        TerminalSearchSnapshot snapshot = TerminalSearchSnapshot.capture(buffer, emulator.mColumns);
        assertEquals(buffer.getActiveRows(), snapshot.getRowCount());
        assertEquals("line 0", snapshot.getRowText(0));
        // Wrapped rows keep all their text.
        assertEquals("0123456789", snapshot.getRowText(snapshot.getRowCount() - 2));

        TerminalSearchResult result = TerminalSearcher.search(snapshot, new TerminalSearchQuery("line 1", true, false), 100, null);
        assertNotNull(result);
        // "line 1" and "line 10" to "line 19".
        assertEquals(11, result.getMatchCount());

        // The match "line 15" stays at the same absolute row when more output scrolls the rows up.
        result = TerminalSearcher.search(snapshot, new TerminalSearchQuery("line 15", true, false), 100, null);
        assertNotNull(result);
        long matchRow = result.getMatchRow(0);
        assertEquals("line 15", buffer.getSelectedText(0, (int) (matchRow - buffer.getScrolledRowsCount()), 9,
            (int) (matchRow - buffer.getScrolledRowsCount())).trim());
        append(emulator, "\r\nmore\r\nmore\r\nmore\r\n");
        int row = (int) (matchRow - buffer.getScrolledRowsCount());
        assertEquals("line 15", buffer.getSelectedText(0, row, 9, row).trim());

        // A match spanning the wrapped rows.
        result = TerminalSearcher.search(snapshot, new TerminalSearchQuery("89wr", true, false), 100, null);
        assertNotNull(result);
        assertEquals(1, result.getMatchCount());
        assertEquals(8, result.getMatchColumn(0));
        assertEquals(result.getMatchRow(0) + 1, result.getMatchEndRow(0));
        assertEquals(2, result.getMatchEndColumn(0));
    }

}
