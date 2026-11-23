package com.termux.app.features.sessions;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.interact.TextInputDialogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialog shown when the New session button in the drawer is long pressed: start a session from
 * a profile, without profile, with a name or a failsafe session, or manage the profiles.
 */
public final class NewSessionDialog {

    private NewSessionDialog() {}

    private static final class Item {
        final String title;
        final String summary;
        @DrawableRes final int icon;
        final Runnable action;

        Item(String title, String summary, @DrawableRes int icon, Runnable action) {
            this.title = title;
            this.summary = summary;
            this.icon = icon;
            this.action = action;
        }
    }

    public static void show(@NonNull TermuxActivity activity) {
        List<Item> items = new ArrayList<>();
        for (TerminalProfile profile : ProfileStore.getProfiles(activity)) {
            items.add(new Item(profile.name, ProfileEditor.getSummary(activity, profile), R.drawable.feature_sessions_ic_profiles,
                () -> activity.getTermuxTerminalSessionClient().addNewSession(false, null, profile)));
        }
        items.add(new Item(activity.getString(R.string.feature_sessions_new_session_plain),
            activity.getString(R.string.feature_sessions_new_session_plain_summary), R.drawable.feature_sessions_ic_terminal,
            () -> activity.getTermuxTerminalSessionClient().addNewSession(false, null, null)));
        items.add(new Item(activity.getString(R.string.feature_sessions_new_session_named),
            activity.getString(R.string.feature_sessions_new_session_named_summary), R.drawable.feature_sessions_ic_label,
            () -> showNamedSession(activity)));
        items.add(new Item(activity.getString(R.string.feature_sessions_new_session_failsafe),
            activity.getString(R.string.feature_sessions_new_session_failsafe_summary), R.drawable.feature_sessions_ic_failsafe,
            () -> activity.getTermuxTerminalSessionClient().addNewSession(true, null)));
        items.add(new Item(activity.getString(R.string.feature_sessions_new_session_manage_profiles),
            activity.getString(R.string.feature_sessions_new_session_manage_profiles_summary), R.drawable.ic_settings,
            () -> ProfilesActivity.start(activity)));

        ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(activity, R.layout.item_feature, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.item_feature, parent, false);
                Item item = items.get(position);
                ((TextView) view.findViewById(R.id.feature_title)).setText(item.title);
                ((TextView) view.findViewById(R.id.feature_summary)).setText(item.summary);
                Drawable icon = AppCompatResources.getDrawable(getContext(), item.icon);
                ((ImageView) view.findViewById(R.id.feature_icon)).setImageDrawable(icon);
                return view;
            }
        };

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_sessions_title_new_session)
            .setAdapter(adapter, (dialog, which) -> {
                dialog.dismiss();
                items.get(which).action.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** The dialog to start a named session, which was shown by long pressing New session before. */
    public static void showNamedSession(@NonNull TermuxActivity activity) {
        TextInputDialogUtils.textInput(activity, R.string.title_create_named_session, null,
            R.string.action_create_named_session_confirm, text -> activity.getTermuxTerminalSessionClient().addNewSession(false, text, null),
            R.string.action_new_session_failsafe, text -> activity.getTermuxTerminalSessionClient().addNewSession(true, text),
            -1, null, null);
    }

}
