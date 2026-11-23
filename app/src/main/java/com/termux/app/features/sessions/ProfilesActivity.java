package com.termux.app.features.sessions;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.List;

/** The terminal profiles manager: create, edit, duplicate, delete and start sessions from profiles. */
public class ProfilesActivity extends AppCompatActivity {

    /** The id of the profile to start a session with when TermuxActivity is shown again. */
    @Nullable private static String sPendingSessionProfileId;

    private ArrayAdapter<TerminalProfile> mAdapter;
    private final List<TerminalProfile> mProfiles = new ArrayList<>();

    public static void start(@NonNull Context context) {
        context.startActivity(new Intent(context, ProfilesActivity.class));
    }

    /** Get and clear the id of the profile the user asked to start a session with. */
    @Nullable
    public static synchronized String takePendingSessionProfileId() {
        String id = sPendingSessionProfileId;
        sPendingSessionProfileId = null;
        return id;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_sessions_activity_profiles);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setToolbarTitle(this, com.termux.shared.R.id.toolbar, getString(R.string.feature_sessions_profiles_title), 0);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);

        ListView listView = findViewById(R.id.feature_sessions_profiles_list);
        mAdapter = new ArrayAdapter<TerminalProfile>(this, R.layout.item_feature, mProfiles) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.item_feature, parent, false);
                TerminalProfile profile = mProfiles.get(position);
                ((TextView) view.findViewById(R.id.feature_title)).setText(profile.name);
                ((TextView) view.findViewById(R.id.feature_summary)).setText(ProfileEditor.getSummary(ProfilesActivity.this, profile));
                Drawable icon = AppCompatResources.getDrawable(getContext(), R.drawable.feature_sessions_ic_profiles);
                ((ImageView) view.findViewById(R.id.feature_icon)).setImageDrawable(icon);
                return view;
            }
        };
        listView.setAdapter(mAdapter);
        listView.setEmptyView(findViewById(R.id.feature_sessions_profiles_empty));
        listView.setOnItemClickListener((parent, view, position, id) -> showProfileMenu(mProfiles.get(position)));

        findViewById(R.id.feature_sessions_profiles_add).setOnClickListener(v -> ProfileEditor.show(this, null, this::update));
        update();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void update() {
        mProfiles.clear();
        mProfiles.addAll(ProfileStore.getProfiles(this));
        mAdapter.notifyDataSetChanged();
    }

    private void showProfileMenu(@NonNull TerminalProfile profile) {
        CharSequence[] items = {
            getString(R.string.feature_sessions_action_start_session),
            getString(R.string.feature_sessions_action_edit),
            getString(R.string.feature_sessions_action_duplicate),
            getString(R.string.feature_sessions_action_delete)};
        new MaterialAlertDialogBuilder(this)
            .setTitle(profile.name)
            .setItems(items, (dialog, which) -> {
                switch (which) {
                    case 0:
                        synchronized (ProfilesActivity.class) {
                            sPendingSessionProfileId = profile.id;
                        }
                        TermuxActivity.startTermuxActivity(this);
                        finish();
                        break;
                    case 1:
                        ProfileEditor.show(this, profile, this::update);
                        break;
                    case 2:
                        duplicateProfile(profile);
                        break;
                    default:
                        confirmDeleteProfile(profile);
                        break;
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void duplicateProfile(@NonNull TerminalProfile profile) {
        TerminalProfile copy = ProfileStore.newProfile();
        copy.name = getString(R.string.feature_sessions_profile_copy_name, profile.name);
        copy.shell = profile.shell;
        copy.workingDirectory = profile.workingDirectory;
        copy.startupCommands = profile.startupCommands;
        copy.colorSchemeId = profile.colorSchemeId;
        copy.fontSize = profile.fontSize;
        ProfileStore.saveProfile(this, copy);
        update();
    }

    private void confirmDeleteProfile(@NonNull TerminalProfile profile) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.feature_sessions_title_delete_profile, profile.name))
            .setPositiveButton(R.string.feature_sessions_action_delete, (dialog, which) -> {
                ProfileStore.deleteProfile(this, profile.id);
                update();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

}
