package com.termux.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.BuildConfig;
import com.termux.R;
import com.termux.app.update.AppUpdateChecker;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.file.FileUtils;
import com.termux.shared.models.ReportInfo;
import com.termux.app.models.UserAction;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.android.PackageUtils;
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences;
import com.termux.shared.android.AndroidUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);

        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new RootPreferencesFragment())
                .commit();
        }

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        android.widget.EditText search = findViewById(R.id.settings_search);
        if (search != null) {
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    androidx.fragment.app.Fragment f = getSupportFragmentManager().findFragmentById(R.id.settings);
                    if (f instanceof RootPreferencesFragment)
                        ((RootPreferencesFragment) f).filterSettings(s.toString());
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class RootPreferencesFragment extends PreferenceFragmentCompat {

        private final java.util.Map<String, Boolean> mVisibilitySnapshot = new java.util.HashMap<>();

        /** Root-level search: show only preferences (and categories) whose title/summary matches. */
        public void filterSettings(@NonNull String rawQuery) {
            androidx.preference.PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) return;
            String query = rawQuery.trim().toLowerCase(java.util.Locale.US);
            if (mVisibilitySnapshot.isEmpty()) snapshotVisibility(screen);
            if (query.isEmpty()) {
                restoreVisibility(screen);
                return;
            }
            filterGroup(screen, query);
        }

        private void snapshotVisibility(androidx.preference.PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference p = group.getPreference(i);
                if (p.getKey() != null) mVisibilitySnapshot.put(p.getKey(), p.isVisible());
                if (p instanceof androidx.preference.PreferenceGroup) snapshotVisibility((androidx.preference.PreferenceGroup) p);
            }
        }

        private void restoreVisibility(androidx.preference.PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference p = group.getPreference(i);
                Boolean v = p.getKey() == null ? null : mVisibilitySnapshot.get(p.getKey());
                p.setVisible(v == null || v);
                if (p instanceof androidx.preference.PreferenceGroup) restoreVisibility((androidx.preference.PreferenceGroup) p);
            }
        }

        private boolean filterGroup(androidx.preference.PreferenceGroup group, String query) {
            boolean anyMatch = false;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference p = group.getPreference(i);
                boolean match;
                if (p instanceof androidx.preference.PreferenceGroup) {
                    match = filterGroup((androidx.preference.PreferenceGroup) p, query) || matches(p, query);
                } else {
                    // Never surface preferences hidden for other reasons (e.g. uninstalled plugins).
                    Boolean snap = p.getKey() == null ? null : mVisibilitySnapshot.get(p.getKey());
                    match = (snap == null || snap) && matches(p, query);
                }
                p.setVisible(match);
                anyMatch = anyMatch || match;
            }
            return anyMatch;
        }

        private boolean matches(Preference p, String query) {
            CharSequence title = p.getTitle();
            CharSequence summary = p.getSummary();
            return (title != null && title.toString().toLowerCase(java.util.Locale.US).contains(query))
                || (summary != null && summary.toString().toLowerCase(java.util.Locale.US).contains(query));
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = getContext();
            if (context == null) return;

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            configureThemeStudioPreference(context);
            configureUpdatePreferences(context);

            new Thread() {
                @Override
                public void run() {
                    configureTermuxAPIPreference(context);
                    configureTermuxFloatPreference(context);
                    configureTermuxTaskerPreference(context);
                    configureTermuxWidgetPreference(context);
                    configureAboutPreference(context);
                    configureDonatePreference(context);
                }
            }.start();
        }

        private void configureThemeStudioPreference(@NonNull Context context) {
            Preference themeStudioPreference = findPreference("theme_studio");
            if (themeStudioPreference != null) {
                TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
                int fontSize = prefs != null ? prefs.getFontSize() : 14;
                String themeName = com.termux.app.features.theme.TermuxThemeManager.getCurrentTheme(context).name;
                themeStudioPreference.setSummary(context.getString(R.string.feature_theme_settings_appearance_summary, themeName, fontSize));
                themeStudioPreference.setOnPreferenceClickListener(preference -> {
                    startActivity(new android.content.Intent(context, com.termux.app.features.theme.ThemeStudioActivity.class));
                    return true;
                });
            }
        }

        private void configureTermuxAPIPreference(@NonNull Context context) {
            Preference termuxAPIPreference = findPreference("termux_api");
            if (termuxAPIPreference != null) {
                TermuxAPIAppSharedPreferences preferences = TermuxAPIAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxAPIPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxFloatPreference(@NonNull Context context) {
            Preference termuxFloatPreference = findPreference("termux_float");
            if (termuxFloatPreference != null) {
                TermuxFloatAppSharedPreferences preferences = TermuxFloatAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxFloatPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxTaskerPreference(@NonNull Context context) {
            Preference termuxTaskerPreference = findPreference("termux_tasker");
            if (termuxTaskerPreference != null) {
                TermuxTaskerAppSharedPreferences preferences = TermuxTaskerAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxTaskerPreference.setVisible(preferences != null);
            }
        }

        private void configureTermuxWidgetPreference(@NonNull Context context) {
            Preference termuxWidgetPreference = findPreference("termux_widget");
            if (termuxWidgetPreference != null) {
                TermuxWidgetAppSharedPreferences preferences = TermuxWidgetAppSharedPreferences.build(context, false);
                // If failed to get app preferences, then likely app is not installed, so do not show its preference
                termuxWidgetPreference.setVisible(preferences != null);
            }
        }

        private void configureUpdatePreferences(@NonNull Context context) {
            TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);

            SwitchPreferenceCompat updateCheckPreference = findPreference("update_check_enabled");
            if (updateCheckPreference != null) {
                updateCheckPreference.setVisible(preferences != null);
                if (preferences != null) {
                    updateCheckPreference.setChecked(preferences.isUpdateCheckEnabled());
                    updateCheckPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                        preferences.setUpdateCheckEnabled((Boolean) newValue);
                        return true;
                    });
                }
            }

            Preference updateCheckNowPreference = findPreference("update_check_now");
            if (updateCheckNowPreference != null) {
                updateCheckNowPreference.setSummary(context.getString(R.string.update_check_now_preference_summary, BuildConfig.VERSION_NAME));
                updateCheckNowPreference.setOnPreferenceClickListener(preference -> {
                    if (getActivity() != null)
                        AppUpdateChecker.checkForUpdateNow(getActivity());
                    return true;
                });
            }
        }

        private void configureAboutPreference(@NonNull Context context) {
            Preference aboutPreference = findPreference("about");
            if (aboutPreference != null) {
                aboutPreference.setOnPreferenceClickListener(preference -> {
                    new Thread() {
                        @Override
                        public void run() {
                            String title = "About";

                            StringBuilder aboutString = new StringBuilder();
                            aboutString.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES));
                            aboutString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true));
                            aboutString.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context));

                            String userActionName = UserAction.ABOUT.getName();

                            ReportInfo reportInfo = new ReportInfo(userActionName,
                                TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title);
                            reportInfo.setReportString(aboutString.toString());
                            reportInfo.setReportSaveFileLabelAndPath(userActionName,
                                Environment.getExternalStorageDirectory() + "/" +
                                    FileUtils.sanitizeFileName(TermuxConstants.TERMUX_APP_NAME + "-" + userActionName + ".log", true, true));

                            ReportActivity.startReportActivity(context, reportInfo);
                        }
                    }.start();

                    return true;
                });
            }
        }

        private void configureDonatePreference(@NonNull Context context) {
            Preference donatePreference = findPreference("donate");
            if (donatePreference != null) {
                String signingCertificateSHA256Digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context);
                if (signingCertificateSHA256Digest != null) {
                    // If APK is a Google Playstore release, then do not show the donation link
                    // since Termux isn't exempted from the playstore policy donation links restriction
                    // Check Fund solicitations: https://pay.google.com/intl/en_in/about/policy/
                    String apkRelease = TermuxUtils.getAPKRelease(signingCertificateSHA256Digest);
                    if (apkRelease == null || apkRelease.equals(TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)) {
                        donatePreference.setVisible(false);
                        return;
                    } else {
                        donatePreference.setVisible(true);
                    }
                }

                donatePreference.setOnPreferenceClickListener(preference -> {
                    ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL);
                    return true;
                });
            }
        }
    }

}
