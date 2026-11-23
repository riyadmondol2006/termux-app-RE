package com.termux.app.features.terminal.search;

import androidx.annotation.NonNull;

import com.termux.view.TerminalHighlighter;

import java.util.Arrays;

/**
 * The matches of a {@link TerminalSearchQuery} in a {@link TerminalSearchSnapshot}.
 *
 * Rows are absolute rows, i.e. external rows plus {@link com.termux.terminal.TerminalBuffer#getScrolledRowsCount()},
 * so the matches stay valid while more output scrolls the content up. A match on a wrapped line may
 * span several rows, so it is made of one segment per row, which is what is highlighted.
 */
public final class TerminalSearchResult {

    @NonNull final TerminalSearchQuery query;
    /** The number of columns of the searched text, the result is invalid for other widths. */
    final int columns;
    /** Whether more matches were found than were kept. */
    final boolean truncated;

    final int matchCount;
    /** The index of the first segment of each match, with an extra entry for the end. */
    final int[] matchFirstSegment;

    final int segmentCount;
    final long[] segmentRow;
    final int[] segmentStartColumn;
    /** Exclusive. */
    final int[] segmentEndColumn;
    final int[] segmentMatch;

    TerminalSearchResult(@NonNull TerminalSearchQuery query, int columns, boolean truncated, int matchCount,
                         int[] matchFirstSegment, int segmentCount, long[] segmentRow, int[] segmentStartColumn,
                         int[] segmentEndColumn, int[] segmentMatch) {
        this.query = query;
        this.columns = columns;
        this.truncated = truncated;
        this.matchCount = matchCount;
        this.matchFirstSegment = matchFirstSegment;
        this.segmentCount = segmentCount;
        this.segmentRow = segmentRow;
        this.segmentStartColumn = segmentStartColumn;
        this.segmentEndColumn = segmentEndColumn;
        this.segmentMatch = segmentMatch;
    }

    @NonNull
    public TerminalSearchQuery getQuery() {
        return query;
    }

    public int getMatchCount() {
        return matchCount;
    }

    /** Whether there were more matches than the max number of matches kept. */
    public boolean isTruncated() {
        return truncated;
    }

    /** Get the absolute row where match {@code index} starts. */
    public long getMatchRow(int index) {
        return segmentRow[matchFirstSegment[index]];
    }

    /** Get the column where match {@code index} starts. */
    public int getMatchColumn(int index) {
        return segmentStartColumn[matchFirstSegment[index]];
    }

    /** Get the absolute row where match {@code index} ends. */
    public long getMatchEndRow(int index) {
        return segmentRow[matchFirstSegment[index + 1] - 1];
    }

    /** Get the column after the end of match {@code index}. */
    public int getMatchEndColumn(int index) {
        return segmentEndColumn[matchFirstSegment[index + 1] - 1];
    }

    /**
     * Find the match to select as the current one for a position, like the previous current match
     * after searching again or the bottom of the view for a new search.
     *
     * @return The last match that starts at or before the position, or the first match if all start
     * after it, or -1 if there are no matches.
     */
    public int findMatchAtOrBefore(long row, int column) {
        if (matchCount == 0) return -1;
        int low = 0, high = matchCount - 1, found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long matchRow = getMatchRow(mid);
            if (matchRow < row || (matchRow == row && getMatchColumn(mid) <= column)) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return found == -1 ? 0 : found;
    }

    /**
     * Write the highlights of the absolute row {@code row} to {@code out} in the format of
     * {@link TerminalHighlighter#getHighlights}.
     *
     * @return The number of highlights written.
     */
    int getHighlights(long row, int currentMatch, @NonNull int[] out, int matchBackColor, int matchTextColor,
                      int currentBackColor, int currentTextColor) {
        int index = Arrays.binarySearch(segmentRow, 0, segmentCount, row);
        if (index < 0) return 0;
        // Several segments may be on the row, find the first one.
        while (index > 0 && segmentRow[index - 1] == row) index--;

        int count = 0;
        final int max = Math.min(TerminalHighlighter.MAX_HIGHLIGHTS_PER_ROW, out.length / TerminalHighlighter.VALUES_PER_HIGHLIGHT);
        for (; index < segmentCount && segmentRow[index] == row && count < max; index++) {
            boolean current = segmentMatch[index] == currentMatch;
            int i = count * TerminalHighlighter.VALUES_PER_HIGHLIGHT;
            out[i] = segmentStartColumn[index];
            out[i + 1] = segmentEndColumn[index];
            out[i + 2] = current ? currentBackColor : matchBackColor;
            out[i + 3] = current ? currentTextColor : matchTextColor;
            count++;
        }
        return count;
    }

}
