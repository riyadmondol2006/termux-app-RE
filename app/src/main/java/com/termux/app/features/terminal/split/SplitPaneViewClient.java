package com.termux.app.features.terminal.split;

import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.shared.termux.terminal.TermuxTerminalViewClientBase;
import com.termux.terminal.TerminalEmulator;
import com.termux.view.TerminalView;

/**
 * The client of the terminal view of the other pane of the split screen. The view only shows its
 * session and can be scrolled, since input goes to the terminal view of the activity. A long press
 * moves the focus to the pane, like a tap does, see {@link SplitTerminalLayout}.
 */
final class SplitPaneViewClient extends TermuxTerminalViewClientBase {

    private final TermuxActivity mActivity;
    private final SplitScreenController mController;
    private TerminalView mView;

    SplitPaneViewClient(@NonNull TermuxActivity activity, @NonNull SplitScreenController controller) {
        mActivity = activity;
        mController = controller;
    }

    void setView(@NonNull TerminalView view) {
        mView = view;
    }

    @Override
    public float onScale(float scale) {
        // Change the font size of both panes, which share the renderer of the activity's view.
        return mActivity.getTermuxTerminalViewClient() != null ? mActivity.getTermuxTerminalViewClient().onScale(scale) : 1.0f;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        mController.focusSecondaryPane(true);
        return true;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return false;
    }

    @Override
    public void onEmulatorSet() {
        // The cursor of the pane without focus does not blink, since its blinker runs for the
        // activity's view. It may have been blinking while the session was shown in that view.
        TerminalEmulator emulator = mView != null ? mView.mEmulator : null;
        if (emulator != null) {
            emulator.setCursorBlinkingEnabled(false);
            emulator.setCursorBlinkState(true);
        }
    }

}
