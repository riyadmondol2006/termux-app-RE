package com.termux.app.features.terminal.search;

import com.termux.view.TerminalHighlighter;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TerminalSearcherTest {

    private static final int COLUMNS = 10;

    private static TerminalSearchSnapshot snapshot(Object... rows) {
        // Rows are strings, a string followed by Boolean.TRUE is a wrapped row.
        TerminalSearchSnapshot.Builder builder = new TerminalSearchSnapshot.Builder(COLUMNS, -2, 100);
        for (int i = 0; i < rows.length; i++) {
            boolean wrapped = i + 1 < rows.length && rows[i + 1] == Boolean.TRUE;
            builder.addRow((String) rows[i], wrapped);
            if (wrapped) i++;
        }
        return builder.build();
    }

    private static TerminalSearchResult search(TerminalSearchSnapshot snapshot, String text, boolean caseSensitive, boolean regex) {
        TerminalSearchResult result = TerminalSearcher.search(snapshot, new TerminalSearchQuery(text, caseSensitive, regex),
            TerminalSearcher.DEFAULT_MAX_MATCHES, null);
        assertNotNull(result);
        return result;
    }

    /** Assert the match at {@code index} is at the external rows and columns (end exclusive). */
    private static void assertMatch(TerminalSearchResult result, int index, int row, int column, int endRow, int endColumn) {
        // The snapshot rows start at external row -2 with 100 scrolled rows.
        assertEquals("start row", row + 100, result.getMatchRow(index));
        assertEquals("start column", column, result.getMatchColumn(index));
        assertEquals("end row", endRow + 100, result.getMatchEndRow(index));
        assertEquals("end column", endColumn, result.getMatchEndColumn(index));
    }

    @Test
    public void testLiteralCaseInsensitive() {
        TerminalSearchSnapshot snapshot = snapshot("Hello", "", "say hello", "HELLO!");
        TerminalSearchResult result = search(snapshot, "hello", false, false);
        assertEquals(3, result.getMatchCount());
        assertMatch(result, 0, -2, 0, -2, 5);
        assertMatch(result, 1, 0, 4, 0, 9);
        assertMatch(result, 2, 1, 0, 1, 5);
        assertFalse(result.isTruncated());
    }

    @Test
    public void testLiteralCaseSensitive() {
        TerminalSearchSnapshot snapshot = snapshot("Hello", "say hello", "HELLO");
        TerminalSearchResult result = search(snapshot, "hello", true, false);
        assertEquals(1, result.getMatchCount());
        assertMatch(result, 0, -1, 4, -1, 9);
    }

    @Test
    public void testLiteralDoesNotOverlap() {
        TerminalSearchResult result = search(snapshot("aaaa"), "aa", true, false);
        assertEquals(2, result.getMatchCount());
        assertMatch(result, 0, -2, 0, -2, 2);
        assertMatch(result, 1, -2, 2, -2, 4);
    }

    @Test
    public void testRegex() {
        TerminalSearchSnapshot snapshot = snapshot("error: 12", "ok", "Error: 345");
        TerminalSearchResult result = search(snapshot, "error: \\d+", false, true);
        assertEquals(2, result.getMatchCount());
        assertMatch(result, 0, -2, 0, -2, 9);
        assertMatch(result, 1, 0, 0, 0, 10);

        result = search(snapshot, "^e", true, true);
        assertEquals(1, result.getMatchCount());
    }

    @Test
    public void testRegexEmptyMatchesAreIgnored() {
        TerminalSearchResult result = search(snapshot("abc", "def"), "x*", true, true);
        assertEquals(0, result.getMatchCount());
    }

    @Test
    public void testInvalidRegex() {
        TerminalSearchQuery query = new TerminalSearchQuery("(abc", true, true);
        assertNotNull(query.getError());
        assertNull(new TerminalSearchQuery("(abc", true, false).getError());
    }

    @Test
    public void testMatchAcrossWrappedRows() {
        // "0123456789" wraps to "abcdef", the line is "0123456789abcdef".
        TerminalSearchSnapshot snapshot = snapshot("0123456789", Boolean.TRUE, "abcdef", "89abc");
        TerminalSearchResult result = search(snapshot, "89abc", true, false);
        assertEquals(2, result.getMatchCount());
        assertMatch(result, 0, -2, 8, -1, 3);
        assertMatch(result, 1, 0, 0, 0, 5);

        // The match on two rows is highlighted on both.
        int[] out = new int[TerminalHighlighter.MAX_HIGHLIGHTS_PER_ROW * TerminalHighlighter.VALUES_PER_HIGHLIGHT];
        assertEquals(1, result.getHighlights(-2 + 100, 0, out, 1, 2, 3, 4));
        assertEquals(8, out[0]);
        assertEquals(10, out[1]);
        assertEquals(3, out[2]); // Current match colors.
        assertEquals(4, out[3]);
        assertEquals(1, result.getHighlights(-1 + 100, 0, out, 1, 2, 3, 4));
        assertEquals(0, out[0]);
        assertEquals(3, out[1]);
        assertEquals(1, result.getHighlights(100, 0, out, 1, 2, 3, 4));
        assertEquals(1, out[2]); // Not the current match.
        assertEquals(0, result.getHighlights(101, 0, out, 1, 2, 3, 4));
    }

    @Test
    public void testNotWrappedRowsAreNotJoined() {
        TerminalSearchResult result = search(snapshot("abc", "def"), "cd", true, false);
        assertEquals(0, result.getMatchCount());
    }

    @Test
    public void testWideCharacters() {
        // Each of the CJK characters takes two columns.
        TerminalSearchSnapshot snapshot = snapshot("日本語abc");
        TerminalSearchResult result = search(snapshot, "abc", true, false);
        assertEquals(1, result.getMatchCount());
        assertMatch(result, 0, -2, 6, -2, 9);

        result = search(snapshot, "本", true, false);
        assertMatch(result, 0, -2, 2, -2, 4);
    }

    @Test
    public void testSurrogatePairsAndCombiningCharacters() {
        // An emoji (two chars, two columns) and an e with a combining acute accent (two chars, one column).
        TerminalSearchSnapshot snapshot = snapshot("😀éx");
        TerminalSearchResult result = search(snapshot, "x", true, false);
        assertMatch(result, 0, -2, 3, -2, 4);

        result = search(snapshot, "é", true, false);
        assertMatch(result, 0, -2, 2, -2, 3);

        // A match of only the accent highlights the cell it is in.
        result = search(snapshot, "́", true, false);
        assertMatch(result, 0, -2, 2, -2, 3);
    }

    @Test
    public void testMaxMatches() {
        TerminalSearchSnapshot snapshot = snapshot("a a a a a");
        TerminalSearchResult result = TerminalSearcher.search(snapshot, new TerminalSearchQuery("a", true, false), 3, null);
        assertNotNull(result);
        assertEquals(3, result.getMatchCount());
        assertTrue(result.isTruncated());
    }

    @Test
    public void testCancel() {
        assertNull(TerminalSearcher.search(snapshot("abc"), new TerminalSearchQuery("a", true, false), 10, new AtomicBoolean(true)));
    }

    @Test
    public void testFindMatchAtOrBefore() {
        TerminalSearchSnapshot snapshot = snapshot("x", "x  x", "", "x");
        TerminalSearchResult result = search(snapshot, "x", true, false);
        assertEquals(4, result.getMatchCount());
        assertEquals(0, result.findMatchAtOrBefore(98, 0)); // Before all matches.
        assertEquals(0, result.findMatchAtOrBefore(98 + 0, 5));
        assertEquals(1, result.findMatchAtOrBefore(99, 0));
        assertEquals(1, result.findMatchAtOrBefore(99, 2));
        assertEquals(2, result.findMatchAtOrBefore(99, 3));
        assertEquals(2, result.findMatchAtOrBefore(100, Integer.MAX_VALUE));
        assertEquals(3, result.findMatchAtOrBefore(1000, 0));
        assertEquals(-1, search(snapshot, "y", true, false).findMatchAtOrBefore(0, 0));
    }

    @Test
    public void testEmptyQuery() {
        assertEquals(0, search(snapshot("abc"), "", true, false).getMatchCount());
    }

}
