package com.termux.app.features.terminal.search;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.KeyboardUtils;
import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The search bar shown over the terminal, that searches the output and scrollback of the session of
 * the terminal view of a {@link TermuxActivity} and highlights the matches.
 *
 * The text of the terminal is copied on the main thread, which is fast, and searched on a background
 * thread, so that searching a large scrollback does not block the UI. While the bar is open, the
 * search is repeated when the terminal output changes, and the matches stay on their rows while new
 * output scrolls them up.
 */
public final class TerminalSearchController implements TerminalSearchHighlighter.OnBufferChangedListener {

    private static final String LOG_TAG = "TerminalSearchController";

    private static final String PREFERENCES_NAME = "feature_terminal_search";
    private static final String KEY_CASE_SENSITIVE = "case_sensitive";
    private static final String KEY_REGEX = "regex";

    /** The delay before searching after the query changed. */
    private static final long TYPING_SEARCH_DELAY = 150;
    /** The delay before searching again after the output of the terminal changed. */
    private static final long OUTPUT_SEARCH_DELAY = 300;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "TerminalSearch");
        thread.setDaemon(true);
        return thread;
    });

    private final TermuxActivity mActivity;
    private final ViewGroup mContainer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SharedPreferences mPreferences;
    private final TerminalSearchHighlighter mHighlighter = new TerminalSearchHighlighter(this);

    private View mBar;
    private EditText mInput;
    private TextView mCountView;
    private MaterialButton mCaseSensitiveButton;
    private MaterialButton mRegexButton;

    private boolean mOpen;
    /** Whether the soft keyboard was disabled by the user and has been enabled while the bar is open. */
    private boolean mSoftKeyboardDisableFlagsCleared;

    /** The terminal view the highlighter is set on. */
    @Nullable private TerminalView mHighlightedView;

    @Nullable private TerminalBuffer mResultBuffer;
    @Nullable private TerminalSearchResult mResult;
    private int mCurrentMatch = -1;
    @Nullable private String mError;

    /** Incremented for each search, to ignore results of older searches. */
    private int mSearchGeneration;
    @Nullable private AtomicBoolean mSearchCancelled;
    private boolean mSearching;
    /** Whether to search again when the running search finishes, since the output changed. */
    private boolean mSearchAgainWhenFinished;

    /** Whether {@link #mNewSearchRunnable} or {@link #mRepeatSearchRunnable} is posted. */
    private boolean mSearchScheduled;
    private final Runnable mNewSearchRunnable = () -> startSearch(false);
    private final Runnable mRepeatSearchRunnable = () -> startSearch(true);

    public TerminalSearchController(@NonNull TermuxActivity activity, @NonNull ViewGroup container) {
        mActivity = activity;
        mContainer = container;
        mPreferences = activity.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOpen() {
        return mOpen;
    }

    /**
     * Show the search bar.
     *
     * @param text The text to search for, or {@code null} to keep the last query.
     */
    @MainThread
    public void open(@Nullable String text) {
        if (mActivity.getTerminalView() == null) return;
        ensureBar();

        mOpen = true;
        mBar.setVisibility(View.VISIBLE);
        if (text != null) {
            // Search for the text as is, not as a regex.
            if (mRegexButton.isChecked()) mRegexButton.setChecked(false);
            mInput.setText(text);
        }
        mInput.selectAll();

        attachHighlighter();

        // The soft keyboard can be disabled for the terminal, but is needed to type the query.
        if (KeyboardUtils.areDisableSoftKeyboardFlagsSet(mActivity)) {
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
            mSoftKeyboardDisableFlagsCleared = true;
        }
        mInput.requestFocus();
        // The terminal view hides the soft keyboard when it loses focus, so show it after that.
        mInput.postDelayed(() -> {
            if (mOpen && mInput.hasFocus()) KeyboardUtils.showSoftKeyboard(mActivity, mInput);
        }, 300);

        startSearch(false);
    }

    /** Hide the search bar and the highlights. */
    @MainThread
    public void close() {
        if (!mOpen) return;
        mOpen = false;

        cancelSearch();
        mResult = null;
        mResultBuffer = null;
        mCurrentMatch = -1;
        mHighlighter.setResult(null, null, -1);
        detachHighlighter();

        if (mBar != null) mBar.setVisibility(View.GONE);

        if (mSoftKeyboardDisableFlagsCleared) {
            mSoftKeyboardDisableFlagsCleared = false;
            KeyboardUtils.setDisableSoftKeyboardFlags(mActivity);
        }

        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView != null) terminalView.requestFocus();
    }

    /** Show the search bar, or focus it if already shown. */
    public void toggleOrFocus() {
        if (mOpen && mInput != null && !mInput.hasFocus()) {
            mInput.requestFocus();
            KeyboardUtils.showSoftKeyboard(mActivity, mInput);
        } else if (mOpen) {
            close();
        } else {
            open(null);
        }
    }

    /** Should be called when the text of {@code session} changed. */
    public void onTextChanged(@NonNull TerminalSession session) {
        if (!mOpen || mInput == null || mInput.length() == 0) return;
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null || terminalView.getCurrentSession() != session) return;
        scheduleRepeatSearch();
    }

    /** Should be called when the terminal view the search applies to may have changed, like in split screen. */
    public void onTerminalViewChanged() {
        if (!mOpen) return;
        attachHighlighter();
        scheduleRepeatSearch();
    }

    @Override
    public void onHighlightedBufferChanged() {
        // Called while drawing, so search later.
        if (mOpen) scheduleRepeatSearch();
    }

    private void scheduleRepeatSearch() {
        if (mSearching) {
            mSearchAgainWhenFinished = true;
            return;
        }
        if (!mSearchScheduled) {
            mSearchScheduled = true;
            mHandler.postDelayed(mRepeatSearchRunnable, OUTPUT_SEARCH_DELAY);
        }
    }

    private void attachHighlighter() {
        TerminalView terminalView = mActivity.getTerminalView();
        if (mHighlightedView != null && mHighlightedView != terminalView) detachHighlighter();
        mHighlightedView = terminalView;
        if (terminalView != null) {
            terminalView.setHighlighter(mHighlighter);
            // Do not jump to the bottom on new output while looking at a match in the scrollback.
            terminalView.setKeepScrollPositionOnOutput(true);
        }
    }

    private void detachHighlighter() {
        if (mHighlightedView == null) return;
        if (mHighlightedView.getHighlighter() == mHighlighter) mHighlightedView.setHighlighter(null);
        mHighlightedView.setKeepScrollPositionOnOutput(false);
        mHighlightedView = null;
    }

    private void ensureBar() {
        if (mBar != null) return;

        mBar = LayoutInflater.from(mActivity).inflate(R.layout.feature_terminal_search_bar, mContainer, false);
        mContainer.addView(mBar);

        mInput = mBar.findViewById(R.id.feature_terminal_search_input);
        mCountView = mBar.findViewById(R.id.feature_terminal_search_count);
        mCaseSensitiveButton = mBar.findViewById(R.id.feature_terminal_search_case);
        mRegexButton = mBar.findViewById(R.id.feature_terminal_search_regex);

        mCaseSensitiveButton.setChecked(mPreferences.getBoolean(KEY_CASE_SENSITIVE, false));
        mRegexButton.setChecked(mPreferences.getBoolean(KEY_REGEX, false));
        mCaseSensitiveButton.addOnCheckedChangeListener((button, isChecked) -> {
            mPreferences.edit().putBoolean(KEY_CASE_SENSITIVE, isChecked).apply();
            if (mOpen) startSearch(false);
        });
        mRegexButton.addOnCheckedChangeListener((button, isChecked) -> {
            mPreferences.edit().putBoolean(KEY_REGEX, isChecked).apply();
            if (mOpen) startSearch(false);
        });

        mBar.findViewById(R.id.feature_terminal_search_previous).setOnClickListener(v -> moveToMatch(-1));
        mBar.findViewById(R.id.feature_terminal_search_next).setOnClickListener(v -> moveToMatch(1));
        mBar.findViewById(R.id.feature_terminal_search_close).setOnClickListener(v -> close());
        // Do not let touches fall through to the terminal below.
        mBar.setOnTouchListener((v, event) -> true);

        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!mOpen) return;
                mHandler.removeCallbacks(mRepeatSearchRunnable);
                mHandler.removeCallbacks(mNewSearchRunnable);
                mSearchScheduled = true;
                mHandler.postDelayed(mNewSearchRunnable, TYPING_SEARCH_DELAY);
            }
        });
        mInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                moveToMatch(-1);
                return true;
            }
            return false;
        });
        mInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_F3;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_F3:
                    // Like in most terminals, search towards older output first.
                    moveToMatch(event.isShiftPressed() ? 1 : -1);
                    return true;
                case KeyEvent.KEYCODE_ESCAPE:
                    close();
                    return true;
                default:
                    return false;
            }
        });
    }

    @NonNull
    private TerminalSearchQuery getQuery() {
        String text = mInput != null ? mInput.getText().toString() : "";
        return new TerminalSearchQuery(text, mCaseSensitiveButton != null && mCaseSensitiveButton.isChecked(),
            mRegexButton != null && mRegexButton.isChecked());
    }

    private void cancelSearch() {
        mHandler.removeCallbacks(mNewSearchRunnable);
        mHandler.removeCallbacks(mRepeatSearchRunnable);
        mSearchScheduled = false;
        mSearchGeneration++;
        if (mSearchCancelled != null) mSearchCancelled.set(true);
        mSearchCancelled = null;
        mSearching = false;
        mSearchAgainWhenFinished = false;
    }

    /**
     * Search the terminal again.
     *
     * @param repeat Whether the search repeats the last one because the output changed, in which
     *               case the current match is kept and the view is not scrolled.
     */
    private void startSearch(boolean repeat) {
        if (!mOpen) return;
        cancelSearch();
        attachHighlighter();

        TerminalView terminalView = mActivity.getTerminalView();
        TerminalEmulator emulator = terminalView != null ? terminalView.mEmulator : null;
        TerminalSearchQuery query = getQuery();
        mError = query.getError();

        if (emulator == null || query.isEmpty() || mError != null) {
            mResult = null;
            mResultBuffer = null;
            mCurrentMatch = -1;
            mHighlighter.setResult(null, null, -1);
            if (terminalView != null) terminalView.invalidate();
            updateCountView();
            return;
        }

        final TerminalBuffer buffer = emulator.getScreen();
        final TerminalSearchSnapshot snapshot;
        long startTime = System.currentTimeMillis();
        try {
            snapshot = TerminalSearchSnapshot.capture(buffer, emulator.mColumns);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to copy the terminal text", e);
            return;
        }
        Logger.logVerbose(LOG_TAG, "Copied " + snapshot.getRowCount() + " rows in " + (System.currentTimeMillis() - startTime) + " ms");

        // Keep the current match when repeating the search, otherwise start at the bottom of the view.
        final long anchorRow;
        final int anchorColumn;
        if (repeat && mResult != null && mResultBuffer == buffer && mCurrentMatch >= 0 && mCurrentMatch < mResult.matchCount
            && mResult.columns == emulator.mColumns) {
            anchorRow = mResult.getMatchRow(mCurrentMatch);
            anchorColumn = mResult.getMatchColumn(mCurrentMatch);
        } else {
            anchorRow = terminalView.getTopRow() + emulator.mRows - 1 + buffer.getScrolledRowsCount();
            anchorColumn = Integer.MAX_VALUE;
        }

        final int generation = mSearchGeneration;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        mSearchCancelled = cancelled;
        mSearching = true;
        if (!repeat) updateCountView();

        EXECUTOR.execute(() -> {
            TerminalSearchResult result = null;
            long searchStartTime = System.currentTimeMillis();
            try {
                result = TerminalSearcher.search(snapshot, query, TerminalSearcher.DEFAULT_MAX_MATCHES, cancelled);
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to search the terminal", e);
            }
            if (result != null)
                Logger.logVerbose(LOG_TAG, "Searched " + snapshot.getRowCount() + " rows in " + (System.currentTimeMillis() - searchStartTime) + " ms");
            final TerminalSearchResult finalResult = result;
            mHandler.post(() -> {
                if (generation != mSearchGeneration || cancelled.get()) return;
                onSearchFinished(buffer, finalResult, anchorRow, anchorColumn, !repeat);
            });
        });
    }

    private void onSearchFinished(@NonNull TerminalBuffer buffer, @Nullable TerminalSearchResult result,
                                  long anchorRow, int anchorColumn, boolean scrollToMatch) {
        mSearching = false;
        mSearchCancelled = null;
        mResult = result;
        mResultBuffer = result != null ? buffer : null;
        mCurrentMatch = result != null ? result.findMatchAtOrBefore(anchorRow, anchorColumn) : -1;
        mHighlighter.setResult(mResultBuffer, mResult, mCurrentMatch);

        if (scrollToMatch) scrollToCurrentMatch();
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView != null) terminalView.invalidate();
        updateCountView();

        if (mSearchAgainWhenFinished) {
            mSearchAgainWhenFinished = false;
            scheduleRepeatSearch();
        }
    }

    /** Select the match {@code delta} matches after the current one, going around at the ends. */
    private void moveToMatch(int delta) {
        if (mResult == null || mResult.matchCount == 0) return;
        int count = mResult.matchCount;
        mCurrentMatch = mCurrentMatch < 0 ? (delta < 0 ? count - 1 : 0) : ((mCurrentMatch + delta) % count + count) % count;
        mHighlighter.setCurrentMatch(mCurrentMatch);
        scrollToCurrentMatch();
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView != null) terminalView.invalidate();
        updateCountView();
    }

    private void scrollToCurrentMatch() {
        TerminalView terminalView = mActivity.getTerminalView();
        if (mResult == null || mResultBuffer == null || mCurrentMatch < 0 || terminalView == null) return;
        if (terminalView.mEmulator == null || terminalView.mEmulator.getScreen() != mResultBuffer) return;
        long row = mResult.getMatchRow(mCurrentMatch) - mResultBuffer.getScrolledRowsCount();
        if (row < -mResultBuffer.getActiveTranscriptRows()) return; // Not in the scrollback anymore.
        terminalView.scrollRowIntoView((int) row);
    }

    private void updateCountView() {
        if (mCountView == null) return;
        String text;
        String description = null;
        int color = MaterialColors.getColor(mCountView, com.google.android.material.R.attr.colorOnSurfaceVariant);
        TerminalSearchQuery query = getQuery();
        if (query.isEmpty()) {
            text = "";
        } else if (mError != null) {
            text = mActivity.getString(R.string.feature_terminal_search_invalid_regex);
            description = mActivity.getString(R.string.feature_terminal_search_invalid_regex_message, mError);
            color = MaterialColors.getColor(mCountView, androidx.appcompat.R.attr.colorError);
        } else if (mSearching && mResult == null) {
            text = mActivity.getString(R.string.feature_terminal_search_searching);
        } else if (mResult == null || mResult.matchCount == 0) {
            text = mActivity.getString(R.string.feature_terminal_search_no_matches);
            color = MaterialColors.getColor(mCountView, androidx.appcompat.R.attr.colorError);
        } else {
            int current = Math.max(mCurrentMatch, 0) + 1;
            text = mActivity.getString(mResult.truncated ? R.string.feature_terminal_search_count_truncated :
                R.string.feature_terminal_search_count, current, mResult.matchCount);
            description = mActivity.getString(R.string.feature_terminal_search_count_description, current, mResult.matchCount);
        }
        mCountView.setText(text);
        mCountView.setTextColor(color);
        mCountView.setContentDescription(description);
        TooltipCompat.setTooltipText(mCountView, mError != null ? description : null);
    }

}
