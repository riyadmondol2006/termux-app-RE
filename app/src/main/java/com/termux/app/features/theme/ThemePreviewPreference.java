package com.termux.app.features.theme;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/** Non-clickable live preview of current terminal colors/font size, shown under Appearance in Settings. */
public class ThemePreviewPreference extends Preference {

    public ThemePreviewPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.feature_theme_preference_preview);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView sample = (TextView) holder.findViewById(R.id.feature_theme_preview_sample);
        if (sample == null) return;
        TermuxTheme theme = TermuxThemeManager.getCurrentTheme(getContext());
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(getContext(), false);
        int fontSize = prefs != null ? prefs.getFontSize() : 14;
        sample.setText("user@termux:~$ ls --color\nsrc/  build.sh*  notes.txt");
        sample.setTextSize(fontSize);
        sample.setTextColor(theme.foreground);
        sample.setBackgroundColor(theme.background);
    }
}
