package com.termux.app.features.input;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.features.FeatureRegistry;
import com.termux.app.features.TermuxFeature;
import com.termux.app.features.TermuxSessionLauncher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared action catalog for all input entry points. IDs remain stable across app restarts. */
public final class InputActions {
    public static final class Action {
        public final String id, title, command;
        final Runnable run;

        Action(String id, String title, Runnable run) {
            this(id, title, null, run);
        }

        Action(String id, String title, String command, Runnable run) {
            this.id = id;
            this.title = title;
            this.command = command;
            this.run = run;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private InputActions() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("input_actions", Context.MODE_PRIVATE);
    }

    public static List<Action> all(TermuxActivity a) {
        List<Action> list = new ArrayList<>();
        list.add(
                new Action(
                        "session.new",
                        a.getString(com.termux.R.string.feature_input_text_1),
                        () -> a.getTermuxTerminalSessionClient().addNewSession(false, null)));
        list.add(
                new Action(
                        "session.rename",
                        a.getString(com.termux.R.string.feature_input_text_2),
                        () ->
                                a.getTermuxTerminalSessionClient()
                                        .renameSession(a.getCurrentSession())));
        list.add(
                new Action(
                        "session.next",
                        a.getString(com.termux.R.string.feature_input_text_3),
                        () -> a.getTermuxTerminalSessionClient().switchToSession(true)));
        list.add(
                new Action(
                        "session.previous",
                        a.getString(com.termux.R.string.feature_input_text_4),
                        () -> a.getTermuxTerminalSessionClient().switchToSession(false)));
        list.add(
                new Action(
                        "session.kill",
                        a.getString(com.termux.R.string.feature_input_text_5),
                        () ->
                                new MaterialAlertDialogBuilder(a)
                                        .setTitle(
                                                a.getString(
                                                        com.termux.R.string.feature_input_text_6))
                                        .setMessage(
                                                a.getString(
                                                        com.termux.R.string.feature_input_text_7))
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(
                                                a.getString(
                                                        com.termux.R.string.feature_input_text_8),
                                                (d, w) -> {
                                                    if (a.getCurrentSession() != null)
                                                        a.getCurrentSession().finishIfRunning();
                                                })
                                        .show()));
        list.add(
                new Action(
                        "terminal.reset",
                        a.getString(com.termux.R.string.feature_input_text_9),
                        () -> {
                            if (a.getCurrentSession() != null) {
                                a.getCurrentSession().reset();
                                a.getTermuxTerminalSessionClient().onResetTerminalSession();
                            }
                        }));
        list.add(
                new Action(
                        "keyboard.toggle",
                        a.getString(com.termux.R.string.feature_input_text_10),
                        () -> a.getTermuxTerminalViewClient().onToggleSoftKeyboardRequest()));
        list.add(
                new Action(
                        "toolbar.toggle",
                        a.getString(com.termux.R.string.feature_input_text_11),
                        a::toggleTerminalToolbar));
        list.add(
                new Action(
                        "terminal.paste",
                        a.getString(com.termux.R.string.feature_input_text_12),
                        () -> a.getTermuxTerminalViewClient().doPaste()));
        list.add(
                new Action(
                        "settings",
                        a.getString(com.termux.R.string.feature_input_text_13),
                        () -> a.startActivity(new Intent(a, SettingsActivity.class))));
        for (java.util.Map.Entry<String, Integer> shortcut :
                com.termux.shared.termux.settings.properties.TermuxPropertyConstants
                        .MAP_SESSION_SHORTCUTS
                        .entrySet()) {
            Integer codePoint =
                    (Integer) a.getProperties().getInternalPropertyValue(shortcut.getKey(), true);
            if (codePoint == null) continue;
            Runnable run;
            switch (shortcut.getValue()) {
                case 1:
                    run = () -> a.getTermuxTerminalSessionClient().addNewSession(false, null);
                    break;
                case 2:
                    run = () -> a.getTermuxTerminalSessionClient().switchToSession(true);
                    break;
                case 3:
                    run = () -> a.getTermuxTerminalSessionClient().switchToSession(false);
                    break;
                case 4:
                    run =
                            () ->
                                    a.getTermuxTerminalSessionClient()
                                            .renameSession(a.getCurrentSession());
                    break;
                default:
                    continue;
            }
            list.add(
                    new Action(
                            "shortcut." + shortcut.getKey(),
                            shortcut.getKey().replace("shortcut.", "")
                                    + " · Ctrl+"
                                    + new String(Character.toChars(codePoint)),
                            run));
        }
        for (TermuxFeature f : FeatureRegistry.getFeatures())
            if (f.isAvailable(a))
                list.add(
                        new Action(
                                "feature." + f.getId(),
                                a.getString(f.getTitle()),
                                () -> f.open(a)));
        JSONArray saved = saved(a);
        for (int i = 0; i < saved.length(); i++) {
            JSONObject item = saved.optJSONObject(i);
            if (item == null) continue;
            String cmd = item.optString("command");
            list.add(
                    new Action(
                            "command." + item.optString("id"),
                            item.optString("title"),
                            cmd,
                            () -> commandOptions(a, cmd)));
        }
        return list;
    }

    static JSONArray saved(Context a) {
        try {
            return new JSONArray(prefs(a).getString("commands", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void saveCommand(Context a, String title, String command) {
        try {
            JSONArray values = saved(a);
            values.put(
                    new JSONObject()
                            .put("id", java.util.UUID.randomUUID())
                            .put("title", title)
                            .put("command", command));
            prefs(a).edit().putString("commands", values.toString()).apply();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void execute(TermuxActivity a, Action action) {
        String key = "use." + action.id;
        prefs(a).edit().putLong(key, prefs(a).getLong(key, 0) + 1).apply();
        action.run.run();
    }

    static void commandOptions(TermuxActivity a, String command) {
        new MaterialAlertDialogBuilder(a)
                .setTitle(a.getString(com.termux.R.string.feature_input_text_14))
                .setMessage(command)
                .setNegativeButton(
                        a.getString(com.termux.R.string.feature_input_text_15),
                        (d, w) -> send(a, command))
                .setPositiveButton(
                        a.getString(com.termux.R.string.feature_input_text_16),
                        (d, w) -> send(a, command + "\r"))
                .setNeutralButton(
                        a.getString(com.termux.R.string.feature_input_text_17),
                        (d, w) ->
                                TermuxSessionLauncher.runInNewSession(a, null, command, true, null))
                .show();
    }

    static void send(TermuxActivity a, String text) {
        if (!TermuxSessionLauncher.sendTextToCurrentSession(a, text))
            a.showToast(a.getString(com.termux.R.string.feature_input_text_18), false);
    }

    static List<String> toolbar(Context a) {
        String raw = prefs(a).getString("toolbar", null);
        if (raw == null)
            return new ArrayList<>(
                    Arrays.asList(
                            "session.new",
                            "keyboard.toggle",
                            "feature.input_palette",
                            "feature.input_quick"));
        List<String> ids = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) ids.add(array.getString(i));
        } catch (Exception ignored) {
        }
        return ids;
    }

    static void saveToolbar(Context a, List<String> ids) {
        prefs(a).edit().putString("toolbar", new JSONArray(ids).toString()).apply();
    }
}
