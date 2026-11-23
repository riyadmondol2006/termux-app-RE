package com.termux.app.features.system.floating;

import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalViewClient;

/**
 * The {@link TerminalViewClient} of the floating terminal. It is much simpler than the client of
 * the activity: no extra keys, shortcuts or URL handling, only focus handling, pinch to zoom, the
 * Enter key to close a finished session, and the sticky Ctrl and Alt of the keys row.
 */
final class FloatingTerminalViewClient implements TerminalViewClient {

    interface Callbacks {
        /** The user tapped the terminal, which should give the window the keyboard focus. */
        void onTerminalTapped();
        void onFontSizeChangeRequested(boolean increase);
        /** Enter was pressed with a session that has finished. */
        void onFinishedSessionEnter();
    }

    private static final String LOG_TAG = "FloatingTerminalViewClient";

    private final Callbacks mCallbacks;
    private boolean mCtrl, mAlt;

    FloatingTerminalViewClient(@NonNull Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    boolean isCtrlActive() { return mCtrl; }
    boolean isAltActive() { return mAlt; }
    void setCtrlActive(boolean active) { mCtrl = active; }
    void setAltActive(boolean active) { mAlt = active; }

    @Override
    public float onScale(float scale) {
        if (scale < 0.9f || scale > 1.1f) {
            mCallbacks.onFontSizeChangeRequested(scale > 1f);
            return 1f;
        }
        return scale;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        mCallbacks.onTerminalTapped();
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning()) {
            mCallbacks.onFinishedSessionEnter();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    // The sticky keys are used for the next key only.

    @Override
    public boolean readControlKey() {
        boolean value = mCtrl;
        mCtrl = false;
        return value;
    }

    @Override
    public boolean readAltKey() {
        boolean value = mAlt;
        mAlt = false;
        return value;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false;
    }

    @Override
    public void onEmulatorSet() {
    }

    @Override
    public void logError(String tag, String message) {
        Logger.logError(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Logger.logWarn(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Logger.logInfo(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Logger.logDebug(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Logger.logVerbose(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        Logger.logStackTraceWithMessage(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        Logger.logStackTrace(tag, e);
    }

}
