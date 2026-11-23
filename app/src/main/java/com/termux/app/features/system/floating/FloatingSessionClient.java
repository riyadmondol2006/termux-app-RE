package com.termux.app.features.system.floating;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxService;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

/**
 * The {@link TerminalSessionClient} of a session shown in the floating terminal. It forwards every
 * call to the client the session would have otherwise, which is the client of
 * {@link com.termux.app.TermuxActivity} while it is bound and the service client when not, so the
 * normal session handling (titles, exit, the session list) keeps working. It is applied with
 * {@link TermuxService#setSessionClientWrapper}, which keeps it when the activity binds or unbinds.
 */
final class FloatingSessionClient implements TermuxService.SessionClientWrapper, TerminalSessionClient {

    /** What the window does with the callbacks of its session. */
    interface Listener {
        void onTextChanged();
        void onTitleChanged();
        void onSessionFinished();
        void onColorsChanged();
    }

    private final Context mContext;
    private final Listener mListener;
    @Nullable private TermuxTerminalSessionClientBase mDelegate;

    FloatingSessionClient(@NonNull Context context, @NonNull Listener listener) {
        mContext = context.getApplicationContext();
        mListener = listener;
    }

    @NonNull
    @Override
    public TerminalSessionClient wrap(@NonNull TermuxTerminalSessionClientBase base) {
        mDelegate = base;
        return this;
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
        if (mDelegate != null) mDelegate.onTextChanged(changedSession);
        mListener.onTextChanged();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (mDelegate != null) mDelegate.onTitleChanged(updatedSession);
        mListener.onTitleChanged();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        if (mDelegate != null) mDelegate.onSessionFinished(finishedSession);
        mListener.onSessionFinished();
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        // Not forwarded, since the activity client ignores it while the activity is not visible.
        ShareUtils.copyTextToClipboard(mContext, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (session == null || session.getEmulator() == null) return;
        String text = ShareUtils.getTextStringFromClipboardIfSet(mContext, true);
        if (text != null) session.getEmulator().paste(text);
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
        if (mDelegate != null) mDelegate.onBell(session);
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {
        if (mDelegate != null) mDelegate.onColorsChanged(session);
        mListener.onColorsChanged();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        if (mDelegate != null) mDelegate.onTerminalCursorStateChange(state);
    }

    @Override
    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
        if (mDelegate != null) mDelegate.setTerminalShellPid(session, pid);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return mDelegate != null ? mDelegate.getTerminalCursorStyle() : null;
    }

    @Override
    public void logError(String tag, String message) {
        if (mDelegate != null) mDelegate.logError(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        if (mDelegate != null) mDelegate.logWarn(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        if (mDelegate != null) mDelegate.logInfo(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        if (mDelegate != null) mDelegate.logDebug(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        if (mDelegate != null) mDelegate.logVerbose(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        if (mDelegate != null) mDelegate.logStackTraceWithMessage(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        if (mDelegate != null) mDelegate.logStackTrace(tag, e);
    }

}
