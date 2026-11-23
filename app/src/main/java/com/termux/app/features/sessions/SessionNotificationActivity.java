package com.termux.app.features.sessions;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.terminal.TerminalSession;

/**
 * Opened by tapping a terminal notification: shows {@link TermuxActivity} with the session of the
 * notification and finishes.
 */
public class SessionNotificationActivity extends Activity {

    static final String EXTRA_SESSION_HANDLE = "session_handle";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String handle = getIntent().getStringExtra(EXTRA_SESSION_HANDLE);
        if (handle != null) {
            // Shown when the activity is started.
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this);
            if (preferences != null) preferences.setCurrentSession(handle);

            // The activity is not started again if it is visible, like behind the notification shade.
            TermuxActivity activity = SessionNotifications.getVisibleActivity();
            TermuxService service = activity != null ? activity.getTermuxService() : null;
            TerminalSession session = service != null ? service.getTerminalSessionForHandle(handle) : null;
            if (session != null) activity.getTermuxTerminalSessionClient().setCurrentSession(session);
            SessionNotifications.cancel(this, handle);
        }

        TermuxActivity.startTermuxActivity(this);
        finish();
    }

}
