package com.termux.terminal;

/** Tests for {@link TerminalBuffer#getScrolledRowsCount()}. */
public class ScrolledRowsCountTest extends TerminalTestCase {

    /** Get the text of the row that was at the external row {@code row} when the count was {@code count}. */
    private String rowAt(long absoluteRow) {
        TerminalBuffer buffer = mTerminal.getScreen();
        int row = (int) (absoluteRow - buffer.getScrolledRowsCount());
        return buffer.getSelectedText(0, row, mTerminal.mColumns - 1, row).trim();
    }

    public void testCountFollowsScrolling() {
        withTerminalSized(5, 3);
        TerminalBuffer buffer = mTerminal.getScreen();
        assertEquals(0, buffer.getScrolledRowsCount());

        enterString("a\r\nb\r\nc");
        assertEquals(0, buffer.getScrolledRowsCount());
        long rowOfA = buffer.getScrolledRowsCount();

        enterString("\r\nd\r\ne");
        assertEquals(2, buffer.getScrolledRowsCount());
        assertEquals("a", rowAt(rowOfA));
        assertEquals("b", rowAt(rowOfA + 1));
    }

    public void testCountFollowsShrinkingScreen() {
        withTerminalSized(5, 4);
        enterString("a\r\nb\r\nc\r\nd");
        long rowOfB = mTerminal.getScreen().getScrolledRowsCount() + 1;
        assertEquals("b", rowAt(rowOfB));

        // Shrinking the rows moves the top rows into the transcript.
        mTerminal.resize(5, 2, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertEquals("b", rowAt(rowOfB));

        mTerminal.resize(5, 4, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertEquals("b", rowAt(rowOfB));
    }

    public void testGetRowIfAllocated() {
        withTerminalSized(5, 3);
        enterString("abc");
        TerminalRow row = mTerminal.getScreen().getRowIfAllocated(0);
        assertNotNull(row);
        assertEquals('a', row.mText[0]);
    }

}
