package com.termux.app.features.sessions;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.terminal.TerminalSession;

/** Shows the saved transcript of a restored session, read-only, with actions to copy and share it. */
public final class RestoredHistoryDialog {

    private RestoredHistoryDialog() {}

    public static void show(@NonNull TermuxActivity activity, @NonNull TerminalSession session) {
        SessionInfo info = SessionRegistry.get(session);
        String transcript = info.restoredTranscript;
        if (transcript == null) return;

        TextView textView = new TextView(activity);
        textView.setText(transcript);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        textView.setTextIsSelectable(true);
        int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, activity.getResources().getDisplayMetrics()));
        textView.setPadding(padding, padding / 3, padding, 0);

        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(activity);
        horizontalScrollView.addView(textView);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(horizontalScrollView);
        // Show the end of the transcript, like the terminal.
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));

        String name = TextUtils.isEmpty(session.mSessionName) ? activity.getString(R.string.feature_sessions_title_session) : session.mSessionName;
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.feature_sessions_title_restored_history, name))
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.feature_sessions_action_share, (dialog, which) ->
                ShareUtils.shareText(activity, activity.getString(R.string.feature_sessions_title_restored_history, name), transcript))
            .setNegativeButton(R.string.feature_sessions_action_copy, (dialog, which) ->
                ShareUtils.copyTextToClipboard(activity, transcript))
            .show();
    }

}
