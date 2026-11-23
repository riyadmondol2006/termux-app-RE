package com.termux.app.features.sessions;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/** The dialog to create or edit a {@link TerminalProfile}. */
public final class ProfileEditor {

    private ProfileEditor() {}

    /**
     * Show the editor.
     *
     * @param profile The profile to edit, or {@code null} to create one.
     * @param onSaved Called after the profile was saved.
     */
    public static void show(@NonNull Activity activity, @Nullable TerminalProfile profile, @Nullable Runnable onSaved) {
        final TerminalProfile edited = profile != null ? profile : ProfileStore.newProfile();
        View view = LayoutInflater.from(activity).inflate(R.layout.feature_sessions_dialog_profile, null);

        EditText nameView = view.findViewById(R.id.feature_sessions_profile_name);
        MaterialAutoCompleteTextView shellView = view.findViewById(R.id.feature_sessions_profile_shell);
        EditText workingDirectoryView = view.findViewById(R.id.feature_sessions_profile_working_directory);
        EditText startupCommandsView = view.findViewById(R.id.feature_sessions_profile_startup_commands);
        EditText fontSizeView = view.findViewById(R.id.feature_sessions_profile_font_size);
        EditText colorSchemeView = view.findViewById(R.id.feature_sessions_profile_color_scheme);
        MaterialSwitch defaultView = view.findViewById(R.id.feature_sessions_profile_default);

        // The shells: the default one, the installed ones and the one of the profile even if not installed.
        List<String> shells = new ArrayList<>();
        shells.add(TerminalProfile.SHELL_DEFAULT);
        shells.addAll(SessionStartup.getInstalledShells());
        if (!shells.contains(edited.shell)) shells.add(edited.shell);
        List<String> shellLabels = new ArrayList<>();
        for (String shell : shells) shellLabels.add(getShellLabel(activity, shell));
        // Simple items are not filtered by the text, so all shells are listed.
        shellView.setSimpleItems(shellLabels.toArray(new String[0]));
        final String[] selectedShell = {edited.shell};
        shellView.setText(getShellLabel(activity, edited.shell), false);
        shellView.setOnItemClickListener((parent, itemView, position, id) -> selectedShell[0] = shells.get(position));

        nameView.setText(edited.name);
        workingDirectoryView.setText(edited.workingDirectory);
        startupCommandsView.setText(edited.startupCommands);
        fontSizeView.setText(edited.fontSize > 0 ? String.valueOf(edited.fontSize) : "");
        colorSchemeView.setText(edited.colorSchemeId);
        defaultView.setChecked(edited.id.equals(ProfileStore.getDefaultProfileId(activity)));

        new MaterialAlertDialogBuilder(activity)
            .setTitle(profile == null ? R.string.feature_sessions_title_new_profile : R.string.feature_sessions_title_edit_profile)
            .setView(view)
            .setPositiveButton(R.string.feature_sessions_action_save, (dialog, which) -> {
                edited.name = nameView.getText().toString().trim();
                if (edited.name.isEmpty()) edited.name = activity.getString(R.string.feature_sessions_profile_unnamed);
                edited.shell = selectedShell[0];
                edited.workingDirectory = workingDirectoryView.getText().toString().trim();
                edited.startupCommands = startupCommandsView.getText().toString();
                edited.fontSize = TerminalProfile.clampFontSize(SessionsPreferences.parseInt(fontSizeView.getText().toString(), 0, 0, TerminalProfile.MAX_FONT_SIZE));
                edited.colorSchemeId = colorSchemeView.getText().toString().trim();
                ProfileStore.saveProfile(activity, edited);
                if (defaultView.isChecked())
                    ProfileStore.setDefaultProfileId(activity, edited.id);
                else if (edited.id.equals(ProfileStore.getDefaultProfileId(activity)))
                    ProfileStore.setDefaultProfileId(activity, null);
                if (onSaved != null) onSaved.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    static String getShellLabel(@NonNull Activity activity, @NonNull String shell) {
        if (shell.isEmpty()) return activity.getString(R.string.feature_sessions_profile_shell_default);
        if (!SessionStartup.getInstalledShells().contains(shell))
            return activity.getString(R.string.feature_sessions_profile_shell_not_installed, shell);
        return shell;
    }

    /** Get a summary of {@code profile}, like "zsh · ~/projects · 2 startup commands". */
    @NonNull
    static String getSummary(@NonNull Activity activity, @NonNull TerminalProfile profile) {
        List<String> parts = new ArrayList<>();
        parts.add(getShellLabel(activity, profile.shell));
        if (!profile.workingDirectory.isEmpty()) parts.add(profile.workingDirectory);
        int commands = 0;
        for (String line : profile.startupCommands.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) commands++;
        }
        if (commands > 0)
            parts.add(activity.getResources().getQuantityString(R.plurals.feature_sessions_profile_startup_command_count, commands, commands));
        if (profile.fontSize > 0) parts.add(activity.getString(R.string.feature_sessions_profile_font_size_value, profile.fontSize));
        if (profile.id.equals(ProfileStore.getDefaultProfileId(activity))) parts.add(activity.getString(R.string.feature_sessions_profile_default));
        return String.join(" · ", parts);
    }

}
