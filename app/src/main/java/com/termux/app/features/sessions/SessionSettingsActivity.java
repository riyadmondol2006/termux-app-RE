package com.termux.app.features.sessions;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

/**
 * The settings of the session features: startup automation, session persistence and terminal
 * notifications. Stored in {@link SessionsPreferences}.
 */
public class SessionSettingsActivity extends AppCompatActivity {

    /** The key of the preference or category to scroll to. */
    public static final String EXTRA_SCROLL_TO = "scroll_to";

    public static void start(@NonNull Context context, @Nullable String scrollToKey) {
        context.startActivity(new Intent(context, SessionSettingsActivity.class).putExtra(EXTRA_SCROLL_TO, scrollToKey));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_sessions_activity_settings);
        if (savedInstanceState == null) {
            SettingsFragment fragment = new SettingsFragment();
            Bundle arguments = new Bundle();
            arguments.putString(EXTRA_SCROLL_TO, getIntent().getStringExtra(EXTRA_SCROLL_TO));
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction().replace(R.id.feature_sessions_settings, fragment).commit();
        }
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setToolbarTitle(this, com.termux.shared.R.id.toolbar, getString(R.string.feature_sessions_title_settings), 0);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName(SessionsPreferences.PREFERENCES_NAME);
            setPreferencesFromResource(R.xml.feature_sessions_preferences, rootKey);
            Context context = requireContext();

            EditTextPreference startupCommands = findPreference(SessionsPreferences.KEY_STARTUP_COMMANDS);
            if (startupCommands != null) {
                startupCommands.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    editText.setSingleLine(false);
                    editText.setMinLines(3);
                    editText.setTypeface(Typeface.MONOSPACE);
                });
                startupCommands.setSummaryProvider(preference -> {
                    String text = ((EditTextPreference) preference).getText();
                    return text == null || text.trim().isEmpty() ? context.getString(R.string.feature_sessions_startup_commands_none) : text.trim();
                });
                startupCommands.setOnPreferenceChangeListener((preference, newValue) -> {
                    // Write the file once the new value is stored.
                    preference.getPreferenceManager().getSharedPreferences().edit()
                        .putString(SessionsPreferences.KEY_STARTUP_COMMANDS, (String) newValue).commit();
                    SessionStartup.writeGlobalStartupFile(context);
                    return true;
                });
            }

            Preference manageProfiles = findPreference(SessionsPreferences.KEY_MANAGE_PROFILES);
            if (manageProfiles != null) {
                manageProfiles.setOnPreferenceClickListener(preference -> {
                    ProfilesActivity.start(context);
                    return true;
                });
            }

            Preference clearSavedHistory = findPreference(SessionsPreferences.KEY_CLEAR_SAVED_SESSIONS);
            if (clearSavedHistory != null) {
                clearSavedHistory.setOnPreferenceClickListener(preference -> {
                    SessionPersistence.deleteSavedTranscripts(context, null);
                    Toast.makeText(context, R.string.feature_sessions_msg_saved_history_cleared, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            EditTextPreference notifySeconds = findPreference(SessionsPreferences.KEY_NOTIFY_COMMANDS_SECONDS);
            if (notifySeconds != null) {
                notifySeconds.setOnBindEditTextListener(editText -> {
                    editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                    editText.setSingleLine(true);
                });
                notifySeconds.setSummaryProvider(preference -> {
                    int seconds = SessionsPreferences.getNotifyCommandsSeconds(context);
                    return context.getResources().getQuantityString(R.plurals.feature_sessions_notify_long_commands_seconds_summary, seconds, seconds);
                });
            }

            Preference notificationSettings = findPreference(SessionsPreferences.KEY_NOTIFICATION_SETTINGS);
            if (notificationSettings != null) {
                notificationSettings.setOnPreferenceClickListener(preference -> {
                    openNotificationSettings(context);
                    return true;
                });
            }

            String scrollTo = getArguments() != null ? getArguments().getString(EXTRA_SCROLL_TO) : null;
            if (scrollTo != null && savedInstanceState == null) scrollToPreference(scrollTo);
        }

        @Override
        public void onResume() {
            super.onResume();
            // The user may have changed the notification permission in the system settings.
            Preference notificationSettings = findPreference(SessionsPreferences.KEY_NOTIFICATION_SETTINGS);
            if (notificationSettings != null) {
                notificationSettings.setSummary(SessionNotifications.canShowNotifications(requireContext()) ?
                    R.string.feature_sessions_notification_settings_allowed : R.string.feature_sessions_notification_settings_blocked);
            }
        }

        private static void openNotificationSettings(@NonNull Context context) {
            SessionNotifications.setupNotificationChannel(context);
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
                intent = new Intent(appNotificationsEnabled ? Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS : Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, SessionNotifications.CHANNEL_ID);
            } else {
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.getPackageName(), null));
            }
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, R.string.feature_sessions_msg_open_settings_failed, Toast.LENGTH_SHORT).show();
            }
        }

    }

}
