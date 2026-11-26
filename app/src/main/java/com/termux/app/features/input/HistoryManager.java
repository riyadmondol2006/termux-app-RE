package com.termux.app.features.input;

import android.widget.*;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.*;

public final class HistoryManager {
    private final TermuxActivity activity;
    private final List<Item> entries = new ArrayList<>(), shown = new ArrayList<>();
    private ListView list;
    private EditText search;
    private CheckBox favourites;
    private TextView status;
    private Set<String> bookmarks;

    private static final class Item {
        final File path;
        final String snapshot;
        final InputFiles.Entry entry;

        Item(File p, String s, InputFiles.Entry e) {
            path = p;
            snapshot = s;
            entry = e;
        }

        @Override
        public String toString() {
            return entry.command;
        }
    }

    private HistoryManager(TermuxActivity a) {
        activity = a;
        bookmarks =
                new HashSet<>(
                        InputActions.prefs(a).getStringSet("bookmarks", Collections.emptySet()));
    }

    public static void open(TermuxActivity a) {
        new HistoryManager(a).show();
    }

    private void show() {
        LinearLayout content = InputUi.column(activity);
        search =
                InputUi.field(
                        activity,
                        content,
                        activity.getString(com.termux.R.string.feature_input_text_58),
                        "");
        search.setSingleLine(true);
        favourites = new CheckBox(activity);
        favourites.setText(activity.getString(com.termux.R.string.feature_input_text_59));
        content.addView(favourites);
        status = new TextView(activity);
        status.setText(activity.getString(com.termux.R.string.feature_input_text_60));
        content.addView(status);
        list = new ListView(activity);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        InputUi.watch(search, this::filter);
        favourites.setOnCheckedChangeListener((v, c) -> filter());
        list.setOnItemClickListener((p, v, pos, id) -> options(shown.get(pos)));
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(com.termux.R.string.feature_input_text_61))
                        .setView(content)
                        .setNegativeButton(
                                activity.getString(com.termux.R.string.feature_input_text_62), null)
                        .setNeutralButton(
                                activity.getString(com.termux.R.string.feature_input_text_63), null)
                        .create();
        dialog.show();
        dialog.getWindow()
                .setLayout(
                        -1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .8));
        dialog.getButton(-3).setOnClickListener(v -> load());
        load();
    }

    private void load() {
        list.setEnabled(false);
        new Thread(
                        () -> {
                            List<Item> loaded = new ArrayList<>();
                            String error = null;
                            String[] names = {
                                ".bash_history", ".zsh_history", ".local/share/fish/fish_history"
                            };
                            String[] shells = {"bash", "zsh", "fish"};
                            for (int i = 0; i < names.length; i++)
                                try {
                                    File p = new File(TermuxConstants.TERMUX_HOME_DIR_PATH, names[i]);
                                    if (p.isFile()) {
                                        if (p.length() > 8 * 1024 * 1024)
                                            throw new java.io.IOException(
                                                    names[i] + " exceeds the 8 MB history limit");
                                        String text = InputFiles.read(p);
                                        for (InputFiles.Entry e :
                                                InputFiles.history(text, shells[i]))
                                            loaded.add(new Item(p, text, e));
                                    }
                                } catch (Exception e) {
                                    error = e.getMessage();
                                }
                            Collections.reverse(loaded);
                            final String message = error;
                            activity.runOnUiThread(
                                    () -> {
                                        if (activity.isFinishing()) return;
                                        entries.clear();
                                        entries.addAll(loaded);
                                        list.setEnabled(true);
                                        filter();
                                        if (message != null) status.setText(message);
                                    });
                        },
                        "input-history-load")
                .start();
    }

    private void filter() {
        shown.clear();
        String q = search.getText().toString().toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        for (Item item : entries)
            if (item.entry.command.toLowerCase(Locale.ROOT).contains(q)
                    && (!favourites.isChecked() || bookmarks.contains(item.entry.command))
                    && seen.add(item.entry.command)) shown.add(item);
        // Bookmarks remain usable even after the shell rotates its history.
        for (String command : bookmarks)
            if (command.toLowerCase(Locale.ROOT).contains(q) && seen.add(command))
                shown.add(new Item(null, "", new InputFiles.Entry(command, 0, 0)));
        list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, shown));
        status.setText(
                shown.isEmpty()
                        ? activity.getString(com.termux.R.string.feature_input_text_64)
                        : shown.size() + " commands · tap to use or bookmark");
    }

    private void options(Item item) {
        String cmd = item.entry.command;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(com.termux.R.string.feature_input_text_65))
                .setMessage(cmd)
                .setPositiveButton(
                        activity.getString(com.termux.R.string.feature_input_text_66),
                        (d, w) -> InputActions.commandOptions(activity, cmd))
                .setNeutralButton(
                        bookmarks.contains(cmd)
                                ? activity.getString(com.termux.R.string.feature_input_text_67)
                                : activity.getString(com.termux.R.string.feature_input_text_68),
                        (d, w) -> {
                            if (!bookmarks.remove(cmd)) bookmarks.add(cmd);
                            InputActions.prefs(activity)
                                    .edit()
                                    .putStringSet("bookmarks", new HashSet<>(bookmarks))
                                    .apply();
                            filter();
                        })
                .setNegativeButton(
                        activity.getString(com.termux.R.string.feature_input_text_69),
                        (d, w) ->
                                new MaterialAlertDialogBuilder(activity)
                                        .setItems(
                                                new String[] {
                                                    activity.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_70),
                                                    activity.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_71)
                                                },
                                                (x, choice) -> {
                                                    if (choice == 0)
                                                        InputActions.saveCommand(
                                                                activity, cmd, cmd);
                                                    else delete(item);
                                                })
                                        .show())
                .show();
    }

    private void delete(Item item) {
        if (item.path == null) {
            activity.showToast(
                    activity.getString(com.termux.R.string.feature_input_text_72), false);
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(com.termux.R.string.feature_input_text_73))
                .setMessage(
                        "Delete this occurrence from "
                                + item.path.getName()
                                + "? A .bak copy is saved first. A running shell may write it"
                                + " again.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        activity.getString(com.termux.R.string.feature_input_text_74),
                        (d, w) ->
                                new Thread(
                                                () -> {
                                                    try {
                                                        String current = InputFiles.read(item.path);
                                                        if (!current.equals(item.snapshot))
                                                            throw new java.io.IOException(
                                                                    activity.getString(
                                                                            com.termux.R.string
                                                                                    .feature_input_text_75));
                                                        InputFiles.replace(
                                                                item.path,
                                                                InputFiles.deleteHistory(
                                                                        current, item.entry),
                                                                true);
                                                        activity.runOnUiThread(this::load);
                                                    } catch (Exception e) {
                                                        activity.runOnUiThread(
                                                                () -> InputUi.error(activity, e));
                                                    }
                                                },
                                                "input-history-delete")
                                        .start())
                .show();
    }
}
