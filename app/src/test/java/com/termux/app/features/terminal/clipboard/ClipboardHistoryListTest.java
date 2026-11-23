package com.termux.app.features.terminal.clipboard;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClipboardHistoryListTest {

    @Test
    public void testAddMostRecentFirstWithoutDuplicates() {
        ClipboardHistoryList list = new ClipboardHistoryList();
        assertTrue(list.add("a"));
        assertTrue(list.add("b"));
        assertFalse(list.add("b")); // Already the most recent.
        assertTrue(list.add("a")); // Moved to the top.
        assertEquals(Arrays.asList("a", "b"), list.getItems());
    }

    @Test
    public void testIgnoresEmptyAndTooLongText() {
        ClipboardHistoryList list = new ClipboardHistoryList();
        assertFalse(list.add(null));
        assertFalse(list.add(""));
        assertFalse(list.add(" \n\t"));
        assertFalse(list.add(repeat('x', ClipboardHistoryList.MAX_ITEM_LENGTH + 1)));
        assertTrue(list.add(repeat('x', ClipboardHistoryList.MAX_ITEM_LENGTH)));
        assertEquals(1, list.size());
    }

    @Test
    public void testMaxItems() {
        ClipboardHistoryList list = new ClipboardHistoryList();
        for (int i = 0; i < ClipboardHistoryList.MAX_ITEMS + 5; i++) list.add("item " + i);
        assertEquals(ClipboardHistoryList.MAX_ITEMS, list.size());
        assertEquals("item " + (ClipboardHistoryList.MAX_ITEMS + 4), list.getItems().get(0));
        assertEquals("item 5", list.getItems().get(ClipboardHistoryList.MAX_ITEMS - 1));
    }

    @Test
    public void testMaxTotalLength() {
        ClipboardHistoryList list = new ClipboardHistoryList();
        int length = ClipboardHistoryList.MAX_ITEM_LENGTH;
        int count = ClipboardHistoryList.MAX_TOTAL_LENGTH / length;
        for (int i = 0; i < count + 2; i++) list.add(repeat((char) ('a' + i), length));
        assertEquals(count, list.size());
        assertEquals('a' + count + 1, list.getItems().get(0).charAt(0));
    }

    @Test
    public void testRemoveAndClear() {
        ClipboardHistoryList list = new ClipboardHistoryList();
        list.addAll(Arrays.asList("newest", "middle", "oldest"));
        assertEquals(Arrays.asList("newest", "middle", "oldest"), list.getItems());
        assertTrue(list.remove("middle"));
        assertFalse(list.remove("missing"));
        assertEquals(Arrays.asList("newest", "oldest"), list.getItems());
        list.clear();
        assertEquals(Collections.emptyList(), list.getItems());
    }

    @Test
    public void testPreviewAndLines() {
        assertEquals("ls -l", ClipboardHistoryDialog.getPreview("\n  ls -l \n"));
        assertEquals(1, ClipboardHistoryDialog.countLines("ls\n"));
        assertEquals(3, ClipboardHistoryDialog.countLines("a\nb\nc"));
        assertEquals(0, ClipboardHistoryDialog.countLines(" "));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

}
