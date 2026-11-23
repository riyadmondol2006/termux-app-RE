package com.termux.app.features.terminal.clipboard;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick paste picker: lists the {@link ClipboardHistory} and pastes the tapped item into the
 * current session. A long press shows actions for the item.
 */
public final class ClipboardHistoryDialog {

    private static final int ITEM_MENU_PASTE = 1;
    private static final int ITEM_MENU_COPY = 2;
    private static final int ITEM_MENU_DELETE = 3;

    private ClipboardHistoryDialog() {}

    public static void show(@NonNull TermuxActivity activity) {
        final ClipboardHistory history = ClipboardHistory.getInstance(activity);
        final List<String> items = new ArrayList<>(history.getItems());

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.feature_terminal_clipboard_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView :
                    LayoutInflater.from(getContext()).inflate(R.layout.feature_terminal_clipboard_item, parent, false);
                String text = getItem(position);
                if (text == null) text = "";
                ((TextView) view.findViewById(R.id.feature_terminal_clipboard_item_text)).setText(getPreview(text));
                TextView infoView = view.findViewById(R.id.feature_terminal_clipboard_item_info);
                int lines = countLines(text);
                infoView.setVisibility(lines > 1 ? View.VISIBLE : View.GONE);
                if (lines > 1)
                    infoView.setText(activity.getResources().getQuantityString(R.plurals.feature_terminal_clipboard_item_lines, lines, lines));
                return view;
            }
        };

        View footer = LayoutInflater.from(activity).inflate(R.layout.feature_terminal_clipboard_footer, null, false);
        final TextView emptyView = footer.findViewById(R.id.feature_terminal_clipboard_empty);
        final MaterialSwitch enabledSwitch = footer.findViewById(R.id.feature_terminal_clipboard_enabled);
        enabledSwitch.setChecked(history.isEnabled());

        final AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_terminal_clipboard_title)
            .setAdapter(adapter, null)
            .setView(footer)
            .setNeutralButton(R.string.feature_terminal_clipboard_clear, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        final Runnable update = () -> {
            items.clear();
            items.addAll(history.getItems());
            adapter.notifyDataSetChanged();
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            emptyView.setText(history.isEnabled() ? R.string.feature_terminal_clipboard_empty : R.string.feature_terminal_clipboard_disabled);
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null)
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!items.isEmpty());
        };

        enabledSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            history.setEnabled(isChecked);
            update.run();
        });

        dialog.setOnShowListener(d -> {
            update.run();

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                new MaterialAlertDialogBuilder(activity)
                    .setMessage(R.string.feature_terminal_clipboard_clear_confirm)
                    .setPositiveButton(R.string.feature_terminal_clipboard_clear, (confirmDialog, which) -> {
                        history.clear();
                        update.run();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());

            ListView listView = dialog.getListView();
            listView.setOnItemClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                paste(activity, items.get(position));
            });
            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                final String text = items.get(position);
                PopupMenu popupMenu = new PopupMenu(activity, view);
                popupMenu.getMenu().add(Menu.NONE, ITEM_MENU_PASTE, Menu.NONE, R.string.feature_terminal_clipboard_item_paste);
                popupMenu.getMenu().add(Menu.NONE, ITEM_MENU_COPY, Menu.NONE, R.string.feature_terminal_clipboard_item_copy);
                popupMenu.getMenu().add(Menu.NONE, ITEM_MENU_DELETE, Menu.NONE, R.string.feature_terminal_clipboard_item_delete);
                popupMenu.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case ITEM_MENU_PASTE:
                            dialog.dismiss();
                            paste(activity, text);
                            return true;
                        case ITEM_MENU_COPY:
                            ShareUtils.copyTextToClipboard(activity, text, activity.getString(R.string.feature_terminal_clipboard_copied));
                            update.run();
                            return true;
                        case ITEM_MENU_DELETE:
                            history.remove(text);
                            update.run();
                            return true;
                        default:
                            return false;
                    }
                });
                popupMenu.show();
                return true;
            });
        });

        dialog.show();
    }

    /** Paste {@code text} into the current session, like pasting from the clipboard. */
    public static void paste(@NonNull TermuxActivity activity, @NonNull String text) {
        TerminalSession session = activity.getCurrentSession();
        if (session == null || !session.isRunning() || session.getEmulator() == null) {
            activity.showToast(activity.getString(R.string.feature_terminal_clipboard_no_session), false);
            return;
        }
        session.getEmulator().paste(text);
        // The pasted item is now the most recent one.
        ClipboardHistory.getInstance(activity).add(text);
        if (activity.getTerminalView() != null) activity.getTerminalView().requestFocus();
    }

    /** Get the text shown for an item, with leading blank lines and indentation removed. */
    @NonNull
    static String getPreview(@NonNull String text) {
        String preview = text.trim();
        if (preview.length() > 500) preview = preview.substring(0, 500);
        return preview;
    }

    static int countLines(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '\n') lines++;
        }
        return lines;
    }

}
