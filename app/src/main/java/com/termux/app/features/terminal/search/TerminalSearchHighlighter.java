package com.termux.app.features.terminal.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalHighlighter;

/** Highlights the matches of a {@link TerminalSearchResult} in a terminal view, the current match in another color. */
final class TerminalSearchHighlighter implements TerminalHighlighter {

    /** Called when the highlighted buffer is not the one shown anymore, like after switching sessions. */
    interface OnBufferChangedListener {
        void onHighlightedBufferChanged();
    }

    static final int MATCH_BACK_COLOR = 0xFFFFE082;
    static final int MATCH_TEXT_COLOR = 0xFF000000;
    static final int CURRENT_MATCH_BACK_COLOR = 0xFFFF7043;
    static final int CURRENT_MATCH_TEXT_COLOR = 0xFF000000;

    @Nullable private final OnBufferChangedListener mListener;

    @Nullable private TerminalBuffer mBuffer;
    @Nullable private TerminalSearchResult mResult;
    private int mCurrentMatch = -1;

    /** The row offset of the frame being drawn, from absolute rows of the result to external rows. */
    private long mRowOffset;

    TerminalSearchHighlighter(@Nullable OnBufferChangedListener listener) {
        mListener = listener;
    }

    void setResult(@Nullable TerminalBuffer buffer, @Nullable TerminalSearchResult result, int currentMatch) {
        mBuffer = buffer;
        mResult = result;
        mCurrentMatch = currentMatch;
    }

    void setCurrentMatch(int currentMatch) {
        mCurrentMatch = currentMatch;
    }

    @Override
    public boolean onPrepareRender(@NonNull TerminalEmulator emulator) {
        if (mResult == null || mBuffer == null) return false;
        if (emulator.getScreen() != mBuffer || emulator.mColumns != mResult.columns) {
            // The rows have changed completely, like after a resize or a session switch.
            if (mListener != null) mListener.onHighlightedBufferChanged();
            return false;
        }
        if (mResult.matchCount == 0) return false;
        mRowOffset = mBuffer.getScrolledRowsCount();
        return true;
    }

    @Override
    public int getHighlights(@NonNull TerminalEmulator emulator, int row, @NonNull int[] out) {
        if (mResult == null) return 0;
        return mResult.getHighlights(row + mRowOffset, mCurrentMatch, out, MATCH_BACK_COLOR, MATCH_TEXT_COLOR,
            CURRENT_MATCH_BACK_COLOR, CURRENT_MATCH_TEXT_COLOR);
    }

}
