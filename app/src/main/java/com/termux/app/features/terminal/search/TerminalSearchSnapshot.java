package com.termux.app.features.terminal.search;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalRow;

import java.util.Arrays;

/**
 * An immutable copy of the text of a {@link TerminalBuffer} (transcript and screen), so that it can
 * be searched in the background while the terminal keeps changing on the main thread.
 *
 * The text of all rows is stored in one array, row after row. Trailing spaces of rows that are not
 * wrapped are left out, while wrapped rows keep them, since the line continues on the next row. This
 * way the rows of a wrapped line are contiguous in {@link #text} and can be searched as one line.
 */
public final class TerminalSearchSnapshot {

    /** The text of all rows. */
    @NonNull final char[] text;
    /** The start index of each row in {@link #text}, with an extra entry for the end of the last row. */
    @NonNull final int[] rowStarts;
    /** Whether each row is wrapped, i.e. its line continues on the next row. */
    @NonNull final boolean[] rowWrapped;
    /** The number of rows. */
    final int rowCount;
    /** The external row of the first row. */
    final int firstRow;
    /** The number of columns of the rows. */
    final int columns;
    /** {@link TerminalBuffer#getScrolledRowsCount()} when the snapshot was taken. */
    final long scrolledRowsCount;

    private TerminalSearchSnapshot(@NonNull char[] text, @NonNull int[] rowStarts, @NonNull boolean[] rowWrapped,
                                   int rowCount, int firstRow, int columns, long scrolledRowsCount) {
        this.text = text;
        this.rowStarts = rowStarts;
        this.rowWrapped = rowWrapped;
        this.rowCount = rowCount;
        this.firstRow = firstRow;
        this.columns = columns;
        this.scrolledRowsCount = scrolledRowsCount;
    }

    /** Get the row number that does not change while the content scrolls, for the row at {@code index}. */
    long getAbsoluteRow(int index) {
        return firstRow + index + scrolledRowsCount;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColumns() {
        return columns;
    }

    /** Get the text of the row at {@code index}, for tests. */
    @NonNull
    String getRowText(int index) {
        return new String(text, rowStarts[index], rowStarts[index + 1] - rowStarts[index]);
    }

    /**
     * Copy the text of {@code buffer}. Must be called on the thread that changes the buffer, the main
     * thread for a terminal session.
     *
     * @param columns The number of columns of the buffer.
     */
    @MainThread
    @NonNull
    public static TerminalSearchSnapshot capture(@NonNull TerminalBuffer buffer, int columns) {
        final int firstRow = -buffer.getActiveTranscriptRows();
        final int rowCount = buffer.getActiveRows();

        // Find the length of each row first, so the text is copied only once into an array of the right size.
        int[] lengths = new int[rowCount];
        boolean[] wrapped = new boolean[rowCount];
        TerminalRow[] rows = new TerminalRow[rowCount];
        long totalLength = 0;
        for (int i = 0; i < rowCount; i++) {
            int row = firstRow + i;
            TerminalRow line = buffer.getRowIfAllocated(row);
            if (line == null) continue;
            rows[i] = line;
            wrapped[i] = buffer.getLineWrap(row);
            int length = line.getSpaceUsed();
            if (!wrapped[i]) {
                char[] lineText = line.mText;
                while (length > 0 && lineText[length - 1] == ' ') length--;
            }
            lengths[i] = length;
            totalLength += length;
        }

        if (totalLength > Integer.MAX_VALUE - 8) throw new IllegalStateException("Terminal text too large: " + totalLength);
        char[] text = new char[(int) totalLength];
        int[] rowStarts = new int[rowCount + 1];
        int offset = 0;
        for (int i = 0; i < rowCount; i++) {
            rowStarts[i] = offset;
            if (lengths[i] > 0) {
                System.arraycopy(rows[i].mText, 0, text, offset, lengths[i]);
                offset += lengths[i];
            }
        }
        rowStarts[rowCount] = offset;

        return new TerminalSearchSnapshot(text, rowStarts, wrapped, rowCount, firstRow, columns, buffer.getScrolledRowsCount());
    }

    /** Builds a snapshot from rows of text, for tests and for text that is not in a buffer. */
    public static final class Builder {

        private final int mColumns;
        private final int mFirstRow;
        private final long mScrolledRowsCount;
        private final StringBuilder mText = new StringBuilder();
        private int[] mRowStarts = new int[16];
        private boolean[] mRowWrapped = new boolean[16];
        private int mRowCount;

        public Builder(int columns, int firstRow, long scrolledRowsCount) {
            mColumns = columns;
            mFirstRow = firstRow;
            mScrolledRowsCount = scrolledRowsCount;
        }

        /** Add a row with {@code text}, which is wrapped if its line continues on the next row. */
        @NonNull
        public Builder addRow(@Nullable String text, boolean wrapped) {
            if (mRowCount + 1 >= mRowStarts.length) {
                mRowStarts = Arrays.copyOf(mRowStarts, mRowStarts.length * 2);
                mRowWrapped = Arrays.copyOf(mRowWrapped, mRowWrapped.length * 2);
            }
            mRowStarts[mRowCount] = mText.length();
            mRowWrapped[mRowCount] = wrapped;
            if (text != null) mText.append(text);
            mRowCount++;
            return this;
        }

        @NonNull
        public TerminalSearchSnapshot build() {
            int[] rowStarts = Arrays.copyOf(mRowStarts, mRowCount + 1);
            rowStarts[mRowCount] = mText.length();
            // Not StringBuilder.getChars(), which lint flags as new in API 37.
            char[] text = mText.toString().toCharArray();
            return new TerminalSearchSnapshot(text, rowStarts, Arrays.copyOf(mRowWrapped, mRowCount), mRowCount,
                mFirstRow, mColumns, mScrolledRowsCount);
        }

    }

}
