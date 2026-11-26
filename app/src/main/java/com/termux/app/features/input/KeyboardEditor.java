package com.termux.app.features.input;

import android.content.Intent;
import android.widget.*;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.extrakeys.*;

import org.json.*;

import java.io.StringReader;
import java.util.Properties;

/** Visual matrix editor. A preview uses the exact runtime parser and key view. */
public final class KeyboardEditor {
    private final TermuxActivity activity;
    private JSONArray matrix;
    private String original;
    private final java.io.File path =
            new java.io.File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".termux/termux.properties");
    private String style;
    private LinearLayout rows;
    private ExtraKeysView preview;

    private KeyboardEditor(TermuxActivity a) {
        activity = a;
    }

    public static void open(TermuxActivity a) {
        new KeyboardEditor(a).load();
    }

    private void load() {
        new Thread(
                        () -> {
                            try {
                                original = InputFiles.read(path);
                                Properties properties = new Properties();
                                properties.load(new StringReader(original));
                                matrix =
                                        new JSONArray(
                                                properties.getProperty(
                                                        "extra-keys",
                                                        "[['ESC','TAB','CTRL','ALT','LEFT','DOWN','UP','RIGHT']]"));
                                style = properties.getProperty("extra-keys-style", "default");
                                activity.runOnUiThread(
                                        () -> {
                                            if (!activity.isFinishing()) show();
                                        });
                            } catch (Exception e) {
                                activity.runOnUiThread(() -> InputUi.error(activity, e));
                            }
                        },
                        "input-keyboard-load")
                .start();
    }

    private void show() {
        LinearLayout content = InputUi.column(activity);
        Spinner styles = new Spinner(activity);
        String[] options = {"default", "arrows-only", "arrows-all", "all", "none"};
        styles.setAdapter(
                new ArrayAdapter<>(
                        activity, android.R.layout.simple_spinner_dropdown_item, options));
        for (int i = 0; i < options.length; i++)
            if (options[i].equals(style)) styles.setSelection(i);
        content.addView(styles);
        styles.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(android.widget.AdapterView<?> p) {}

                    public void onItemSelected(
                            android.widget.AdapterView<?> p,
                            android.view.View v,
                            int pos,
                            long id) {
                        style = options[pos];
                        render();
                    }
                });
        preview = new ExtraKeysView(activity, null);
        preview.setExtraKeysViewClient(
                new ExtraKeysView.IExtraKeysView() {
                    @Override
                    public void onExtraKeyButtonClick(
                            android.view.View view,
                            ExtraKeyButton info,
                            com.google.android.material.button.MaterialButton button) {}

                    @Override
                    public boolean performExtraKeyButtonHapticFeedback(
                            android.view.View view,
                            ExtraKeyButton info,
                            com.google.android.material.button.MaterialButton button) {
                        return false;
                    }
                });
        content.addView(preview);
        TextView help = new TextView(activity);
        help.setText(activity.getString(com.termux.R.string.feature_input_text_37));
        content.addView(help);
        rows = InputUi.column(activity);
        content.addView(rows);
        InputUi.button(
                activity,
                content,
                activity.getString(com.termux.R.string.feature_input_text_38),
                () -> {
                    matrix.put(new JSONArray());
                    render();
                });
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(com.termux.R.string.feature_input_text_39))
                        .setView(scroll)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(
                                activity.getString(com.termux.R.string.feature_input_text_40), null)
                        .create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> save(dialog)));
        dialog.show();
        render();
    }

    private void render() {
        if (rows == null || preview == null) return;
        rows.removeAllViews();
        try {
            ExtraKeysInfo info =
                    new ExtraKeysInfo(
                            matrix.toString(), style, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            preview.reload(info, activity.getTerminalToolbarDefaultHeight());
            preview.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            -1,
                            (int)
                                    (activity.getTerminalToolbarDefaultHeight()
                                            * Math.max(1, matrix.length()))));
            for (int r = 0; r < matrix.length(); r++) {
                final int rowIndex = r;
                JSONArray row = matrix.getJSONArray(r);
                TextView label = new TextView(activity);
                label.setText("Row " + (r + 1));
                rows.addView(label);
                for (int k = 0; k < row.length(); k++) {
                    final int keyIndex = k;
                    Object key = row.get(k);
                    String title =
                            key instanceof JSONObject
                                    ? ((JSONObject) key)
                                            .optString(
                                                    "display",
                                                    ((JSONObject) key)
                                                            .optString(
                                                                    "key",
                                                                    ((JSONObject) key)
                                                                            .optString("macro")))
                                    : key.toString();
                    InputUi.button(activity, rows, title, () -> edit(row, keyIndex));
                }
                InputUi.button(
                        activity,
                        rows,
                        activity.getString(com.termux.R.string.feature_input_text_41),
                        () -> edit(row, row.length()));
                InputUi.button(
                        activity,
                        rows,
                        activity.getString(com.termux.R.string.feature_input_text_42),
                        () -> {
                            if (rowIndex > 0) {
                                Object prev = matrix.opt(rowIndex - 1);
                                try {
                                    matrix.put(rowIndex - 1, row);
                                    matrix.put(rowIndex, prev);
                                } catch (JSONException e) {
                                    InputUi.error(activity, e);
                                }
                                render();
                            }
                        });
                InputUi.button(
                        activity,
                        rows,
                        activity.getString(com.termux.R.string.feature_input_text_43),
                        () -> {
                            matrix.remove(rowIndex);
                            render();
                        });
            }
        } catch (Exception e) {
            InputUi.error(activity, e);
        }
    }

    private void edit(JSONArray row, int initialIndex) {
        final int[] position = {initialIndex};
        Object old = row.opt(position[0]);
        JSONObject object = old instanceof JSONObject ? (JSONObject) old : new JSONObject();
        LinearLayout form = InputUi.column(activity);
        Spinner type = new Spinner(activity);
        type.setAdapter(
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {
                            activity.getString(com.termux.R.string.feature_input_text_44),
                            activity.getString(com.termux.R.string.feature_input_text_45)
                        }));
        type.setSelection(object.has("macro") ? 1 : 0);
        form.addView(type);
        EditText value =
                InputUi.field(
                        activity,
                        form,
                        activity.getString(com.termux.R.string.feature_input_text_46),
                        old instanceof String
                                ? (String) old
                                : object.optString("key", object.optString("macro")));
        EditText display =
                InputUi.field(
                        activity,
                        form,
                        activity.getString(com.termux.R.string.feature_input_text_47),
                        object.optString("display"));
        EditText popup =
                InputUi.field(
                        activity,
                        form,
                        activity.getString(com.termux.R.string.feature_input_text_48),
                        "");
        Spinner popupType = new Spinner(activity);
        popupType.setAdapter(
                new ArrayAdapter<>(
                        activity,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[] {
                            activity.getString(com.termux.R.string.feature_input_text_49),
                            activity.getString(com.termux.R.string.feature_input_text_50)
                        }));
        form.addView(popupType);
        Object p = object.opt("popup");
        if (p instanceof JSONObject) {
            JSONObject po = (JSONObject) p;
            popup.setText(po.optString("key", po.optString("macro")));
            popupType.setSelection(po.has("macro") ? 1 : 0);
        } else if (p != null) popup.setText(p.toString());
        InputUi.button(
                activity,
                form,
                activity.getString(com.termux.R.string.feature_input_text_51),
                () -> {
                    if (position[0] > 0 && position[0] < row.length()) {
                        try {
                            Object prev = row.get(position[0] - 1);
                            row.put(position[0] - 1, row.get(position[0]));
                            row.put(position[0], prev);
                            position[0]--;
                            render();
                        } catch (Exception e) {
                            InputUi.error(activity, e);
                        }
                    }
                });
        InputUi.button(
                activity,
                form,
                activity.getString(com.termux.R.string.feature_input_text_52),
                () -> {
                    if (position[0] + 1 < row.length()) {
                        try {
                            Object next = row.get(position[0] + 1);
                            row.put(position[0] + 1, row.get(position[0]));
                            row.put(position[0], next);
                            position[0]++;
                            render();
                        } catch (Exception e) {
                            InputUi.error(activity, e);
                        }
                    }
                });
        ScrollView keyScroll = new ScrollView(activity);
        keyScroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(activity.getString(com.termux.R.string.feature_input_text_53))
                        .setView(keyScroll)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(
                                activity.getString(com.termux.R.string.feature_input_text_54),
                                (d, w) -> {
                                    row.remove(position[0]);
                                    render();
                                })
                        .setPositiveButton(
                                activity.getString(com.termux.R.string.feature_input_text_55), null)
                        .create();
        dialog.setOnShowListener(
                v ->
                        dialog.getButton(-1)
                                .setOnClickListener(
                                        b -> {
                                            try {
                                                if (value.length() == 0) {
                                                    value.setError(
                                                            activity.getString(
                                                                    com.termux.R.string
                                                                            .feature_input_text_56));
                                                    return;
                                                }
                                                JSONObject key =
                                                        new JSONObject()
                                                                .put(
                                                                        type
                                                                                                .getSelectedItemPosition()
                                                                                        == 1
                                                                                ? "macro"
                                                                                : "key",
                                                                        value.getText().toString());
                                                if (display.length() > 0)
                                                    key.put(
                                                            "display",
                                                            display.getText().toString());
                                                if (popup.length() > 0)
                                                    key.put(
                                                            "popup",
                                                            new JSONObject()
                                                                    .put(
                                                                            popupType
                                                                                                    .getSelectedItemPosition()
                                                                                            == 1
                                                                                    ? "macro"
                                                                                    : "key",
                                                                            popup.getText()
                                                                                    .toString()));
                                                new ExtraKeysInfo(
                                                        new JSONArray()
                                                                .put(new JSONArray().put(key))
                                                                .toString(),
                                                        style,
                                                        ExtraKeysConstants.CONTROL_CHARS_ALIASES);
                                                row.put(position[0], key);
                                                render();
                                                dialog.dismiss();
                                            } catch (Exception e) {
                                                value.setError(e.getMessage());
                                            }
                                        }));
        dialog.show();
    }

    private void save(androidx.appcompat.app.AlertDialog dialog) {
        try {
            new ExtraKeysInfo(matrix.toString(), style, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (Exception e) {
            InputUi.error(activity, e);
            return;
        }
        final String json = matrix.toString(), selectedStyle = style;
        dialog.getButton(-1).setEnabled(false);
        new Thread(
                        () -> {
                            try {
                                if (!InputFiles.read(path).equals(original))
                                    throw new java.io.IOException(
                                            "termux.properties changed while editing. Reopen the"
                                                + " editor to load the latest file.");
                                InputFiles.replace(
                                        path,
                                        InputFiles.keyboardProperties(
                                                original, json, selectedStyle),
                                        true);
                                activity.runOnUiThread(
                                        () -> {
                                            dialog.dismiss();
                                            activity.sendBroadcast(
                                                    new Intent(
                                                                    TermuxConstants.TERMUX_APP
                                                                            .TERMUX_ACTIVITY
                                                                            .ACTION_RELOAD_STYLE)
                                                            .setPackage(activity.getPackageName())
                                                            .putExtra(
                                                                    TermuxConstants.TERMUX_APP
                                                                            .TERMUX_ACTIVITY
                                                                            .EXTRA_RECREATE_ACTIVITY,
                                                                    false));
                                            activity.showToast(
                                                    activity.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_57),
                                                    true);
                                        });
                            } catch (Exception e) {
                                activity.runOnUiThread(
                                        () -> {
                                            dialog.getButton(-1).setEnabled(true);
                                            InputUi.error(activity, e);
                                        });
                            }
                        },
                        "input-keyboard-save")
                .start();
    }
}
