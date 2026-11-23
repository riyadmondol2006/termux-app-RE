package com.termux.app.features.terminal.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.terminal.WcWidth;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches a {@link TerminalSearchSnapshot}. Lines that are wrapped over several rows are searched
 * as one line, so matches can span rows. Wide characters and combining characters are mapped to the
 * terminal columns they are shown in.
 */
public final class TerminalSearcher {

    /** The default max number of matches kept, to limit memory use for very common queries. */
    public static final int DEFAULT_MAX_MATCHES = 10000;

    private final TerminalSearchSnapshot mSnapshot;
    private final TerminalSearchQuery mQuery;
    private final int mMaxMatches;

    private int mMatchCount;
    private int[] mMatchFirstSegment = new int[64];
    private int mSegmentCount;
    private long[] mSegmentRow = new long[64];
    private int[] mSegmentStartColumn = new int[64];
    private int[] mSegmentEndColumn = new int[64];
    private int[] mSegmentMatch = new int[64];
    private boolean mTruncated;

    private final int[] mCell = new int[2];

    private TerminalSearcher(@NonNull TerminalSearchSnapshot snapshot, @NonNull TerminalSearchQuery query, int maxMatches) {
        mSnapshot = snapshot;
        mQuery = query;
        mMaxMatches = maxMatches;
    }

    /**
     * Search {@code snapshot} for {@code query}.
     *
     * @param cancelled Checked while searching, the search stops if it is set.
     * @return The result, or {@code null} if cancelled.
     * @throws java.util.regex.PatternSyntaxException If the query is an invalid regex.
     */
    @WorkerThread
    @Nullable
    public static TerminalSearchResult search(@NonNull TerminalSearchSnapshot snapshot, @NonNull TerminalSearchQuery query,
                                              int maxMatches, @Nullable AtomicBoolean cancelled) {
        return new TerminalSearcher(snapshot, query, maxMatches).run(cancelled);
    }

    @Nullable
    private TerminalSearchResult run(@Nullable AtomicBoolean cancelled) {
        if (!mQuery.isEmpty()) {
            Pattern pattern = mQuery.compilePattern();
            char[] needle = pattern == null ? prepareNeedle() : null;
            Matcher matcher = pattern != null ? pattern.matcher("") : null;

            final TerminalSearchSnapshot s = mSnapshot;
            int row = 0;
            while (row < s.rowCount && !mTruncated) {
                if (cancelled != null && cancelled.get()) return null;

                // A line is a row and the rows it wraps to.
                int lastRow = row;
                while (lastRow < s.rowCount - 1 && s.rowWrapped[lastRow]) lastRow++;
                int lineStart = s.rowStarts[row];
                int lineEnd = s.rowStarts[lastRow + 1];
                if (lineEnd > lineStart) {
                    if (matcher != null) searchLineWithRegex(matcher, row, lineStart, lineEnd);
                    else searchLineForText(needle, row, lineStart, lineEnd);
                }
                row = lastRow + 1;
            }
        }

        if (mMatchFirstSegment.length < mMatchCount + 1)
            mMatchFirstSegment = Arrays.copyOf(mMatchFirstSegment, mMatchCount + 1);
        mMatchFirstSegment[mMatchCount] = mSegmentCount;
        return new TerminalSearchResult(mQuery, mSnapshot.columns, mTruncated, mMatchCount,
            mMatchFirstSegment, mSegmentCount, mSegmentRow, mSegmentStartColumn, mSegmentEndColumn, mSegmentMatch);
    }

    @NonNull
    private char[] prepareNeedle() {
        char[] needle = mQuery.text.toCharArray();
        if (!mQuery.caseSensitive) {
            for (int i = 0; i < needle.length; i++) needle[i] = foldCase(needle[i]);
        }
        return needle;
    }

    private static char foldCase(char c) {
        if (c < 128) return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
        return Character.toLowerCase(Character.toUpperCase(c));
    }

    private void searchLineForText(@NonNull char[] needle, int firstRow, int lineStart, int lineEnd) {
        final char[] text = mSnapshot.text;
        final int needleLength = needle.length;
        final boolean caseSensitive = mQuery.caseSensitive;
        final char first = needle[0];
        final int last = lineEnd - needleLength;
        int i = lineStart;
        while (i <= last) {
            char c = caseSensitive ? text[i] : foldCase(text[i]);
            if (c == first) {
                int j = 1;
                if (caseSensitive) {
                    while (j < needleLength && text[i + j] == needle[j]) j++;
                } else {
                    while (j < needleLength && foldCase(text[i + j]) == needle[j]) j++;
                }
                if (j == needleLength) {
                    if (!addMatch(firstRow, i, i + needleLength)) return;
                    i += needleLength;
                    continue;
                }
            }
            i++;
        }
    }

    private void searchLineWithRegex(@NonNull Matcher matcher, int firstRow, int lineStart, int lineEnd) {
        matcher.reset(new String(mSnapshot.text, lineStart, lineEnd - lineStart));
        while (matcher.find()) {
            int start = matcher.start(), end = matcher.end();
            // Empty matches, like for "^" or "a*", can't be highlighted.
            if (end == start) continue;
            if (!addMatch(firstRow, lineStart + start, lineStart + end)) return;
        }
    }

    /**
     * Add the match from {@code start} to {@code end} (exclusive) of {@link TerminalSearchSnapshot#text},
     * that is on the line starting at row {@code firstRow}.
     *
     * @return Whether more matches can be added.
     */
    private boolean addMatch(int firstRow, int start, int end) {
        if (mMatchCount >= mMaxMatches) {
            mTruncated = true;
            return false;
        }

        final TerminalSearchSnapshot s = mSnapshot;
        int startRow = findRow(firstRow, start);
        int endRow = findRow(startRow, end - 1);

        int firstSegment = mSegmentCount;
        for (int row = startRow; row <= endRow; row++) {
            int startColumn, endColumn;
            if (row == startRow) {
                getCell(row, start);
                startColumn = mCell[0];
            } else {
                startColumn = 0;
            }
            if (row == endRow) {
                getCell(row, end - 1);
                endColumn = mCell[1];
            } else {
                endColumn = s.columns;
            }
            addSegment(s.getAbsoluteRow(row), startColumn, endColumn);
        }

        if (mSegmentCount == firstSegment) return true; // Nothing to show.

        if (mMatchFirstSegment.length < mMatchCount + 2)
            mMatchFirstSegment = Arrays.copyOf(mMatchFirstSegment, mMatchFirstSegment.length * 2);
        mMatchFirstSegment[mMatchCount] = firstSegment;
        mMatchCount++;
        return true;
    }

    private void addSegment(long row, int startColumn, int endColumn) {
        // Matches don't overlap, but two of them can end and start in the same cell, like with a
        // combining character, while highlights must not overlap.
        if (mSegmentCount > 0 && mSegmentRow[mSegmentCount - 1] == row)
            startColumn = Math.max(startColumn, mSegmentEndColumn[mSegmentCount - 1]);
        if (endColumn <= startColumn) return;

        if (mSegmentCount == mSegmentRow.length) {
            int newLength = mSegmentRow.length * 2;
            mSegmentRow = Arrays.copyOf(mSegmentRow, newLength);
            mSegmentStartColumn = Arrays.copyOf(mSegmentStartColumn, newLength);
            mSegmentEndColumn = Arrays.copyOf(mSegmentEndColumn, newLength);
            mSegmentMatch = Arrays.copyOf(mSegmentMatch, newLength);
        }
        mSegmentRow[mSegmentCount] = row;
        mSegmentStartColumn[mSegmentCount] = startColumn;
        mSegmentEndColumn[mSegmentCount] = endColumn;
        mSegmentMatch[mSegmentCount] = mMatchCount;
        mSegmentCount++;
    }

    /** Find the row, starting at {@code fromRow}, that contains the char at {@code index}. */
    private int findRow(int fromRow, int index) {
        final int[] rowStarts = mSnapshot.rowStarts;
        int row = fromRow;
        // Empty rows have the same start as the next row, so take the last row starting at or before index.
        while (row + 1 < mSnapshot.rowCount && rowStarts[row + 1] <= index) row++;
        return row;
    }

    /**
     * Set {@link #mCell} to the start column and the end column (exclusive) of the cell of the
     * char at {@code index}, which is in {@code row}. A combining character is in the cell of the
     * character it combines with, and a wide character takes two columns.
     */
    private void getCell(int row, int index) {
        final char[] text = mSnapshot.text;
        final int rowEnd = mSnapshot.rowStarts[row + 1];
        int i = mSnapshot.rowStarts[row];
        int column = 0;
        int cellStart = 0, cellEnd = 1;
        while (i < rowEnd) {
            char c = text[i];
            int codePoint, charCount;
            if (Character.isHighSurrogate(c) && i + 1 < rowEnd && Character.isLowSurrogate(text[i + 1])) {
                codePoint = Character.toCodePoint(c, text[i + 1]);
                charCount = 2;
            } else {
                codePoint = c;
                charCount = 1;
            }
            int width = WcWidth.width(codePoint);
            if (width > 0) {
                cellStart = column;
                cellEnd = column + width;
                column += width;
            }
            if (index < i + charCount) break;
            i += charCount;
        }
        mCell[0] = cellStart;
        mCell[1] = Math.max(cellEnd, cellStart + 1);
    }

}
