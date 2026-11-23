package com.termux.view;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalEmulator;

/**
 * Highlights of ranges of text in a {@link TerminalView}, like the matches of a search, that the
 * {@link TerminalRenderer} draws with their own background and text colors.
 *
 * Set with {@link TerminalView#setHighlighter(TerminalHighlighter)}. Methods are called on the main
 * thread while drawing, so they must be fast.
 */
public interface TerminalHighlighter {

    /** The number of values of a highlight in the array filled by {@link #getHighlights}. */
    int VALUES_PER_HIGHLIGHT = 4;

    /** The max number of highlights the renderer gets for a row. */
    int MAX_HIGHLIGHTS_PER_ROW = 64;

    /**
     * Called before the rows of a frame are drawn.
     *
     * @param emulator The emulator being drawn.
     * @return Whether there may be highlights in the frame. If {@code false},
     * {@link #getHighlights(TerminalEmulator, int, int[])} is not called for the frame.
     */
    boolean onPrepareRender(@NonNull TerminalEmulator emulator);

    /**
     * Get the highlights of a row.
     *
     * Each highlight is written to {@code out} as {@link #VALUES_PER_HIGHLIGHT} values: the start
     * column (inclusive), the end column (exclusive), the background color and the text color.
     * Highlights must be sorted by start column and must not overlap.
     *
     * @param emulator The emulator being drawn.
     * @param row The external row of {@link TerminalEmulator#getScreen()}.
     * @param out The array to write the highlights to, with space for {@link #MAX_HIGHLIGHTS_PER_ROW}.
     * @return The number of highlights written.
     */
    int getHighlights(@NonNull TerminalEmulator emulator, int row, @NonNull int[] out);

}
