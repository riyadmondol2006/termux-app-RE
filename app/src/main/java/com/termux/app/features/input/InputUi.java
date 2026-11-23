package com.termux.app.features.input;

import android.content.ClipData;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.View;
import android.widget.*;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;

import java.util.ArrayList;
import java.util.List;

public final class InputUi {
    private InputUi() {}

    static LinearLayout column(TermuxActivity a) {
        LinearLayout layout = new LinearLayout(a);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        return layout;
    }

    static EditText field(TermuxActivity a, LinearLayout parent, String hint, String value) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(value);
        e.setSelectAllOnFocus(true);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    static MaterialButton button(
            TermuxActivity a, LinearLayout parent, String text, Runnable click) {
        MaterialButton b = new MaterialButton(a);
        b.setText(text);
        b.setAllCaps(false);
        parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> click.run());
        return b;
    }

    static void watch(EditText e, Runnable update) {
        e.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        update.run();
                    }

                    public void afterTextChanged(Editable s) {}
                });
    }

    static void error(TermuxActivity a, Exception e) {
        if (!a.isFinishing())
            new MaterialAlertDialogBuilder(a)
                    .setTitle(a.getString(com.termux.R.string.feature_input_text_19))
                    .setMessage(e.getMessage())
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
    }

    public static void palette(TermuxActivity a) {
        LinearLayout content = column(a);
        EditText search =
                field(a, content, a.getString(com.termux.R.string.feature_input_text_20), "");
        search.setSingleLine(true);
        ListView view = new ListView(a);
        content.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
        button(
                a,
                content,
                a.getString(com.termux.R.string.feature_input_text_21),
                () -> commands(a));
        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(a)
                        .setTitle(a.getString(com.termux.R.string.feature_input_text_22))
                        .setView(content)
                        .setNegativeButton(android.R.string.cancel, null)
                        .create();
        List<InputActions.Action> source = InputActions.all(a), shown = new ArrayList<>();
        Runnable filter =
                () -> {
                    String q = search.getText().toString();
                    shown.clear();
                    for (InputActions.Action action : source)
                        if (InputFiles.score(
                                        action.title
                                                + " "
                                                + (action.command == null ? "" : action.command),
                                        q)
                                >= 0) shown.add(action);
                    shown.sort(
                            (x, y) ->
                                    Integer.compare(
                                            InputFiles.score(y.title, q),
                                            InputFiles.score(x.title, q)));
                    view.setAdapter(
                            new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, shown));
                };
        watch(search, filter);
        filter.run();
        view.setOnItemClickListener(
                (p, v, pos, id) -> {
                    dialog.dismiss();
                    InputActions.execute(a, shown.get(pos));
                });
        search.setOnEditorActionListener(
                (v, id, event) -> {
                    if (!shown.isEmpty()) {
                        dialog.dismiss();
                        InputActions.execute(a, shown.get(0));
                    }
                    return true;
                });
        dialog.show();
        dialog.getWindow()
                .setLayout(-1, (int) (a.getResources().getDisplayMetrics().heightPixels * .78));
    }

    public static void quick(TermuxActivity a) {
        BottomSheetDialog dialog = new BottomSheetDialog(a);
        LinearLayout content = column(a);
        TextView title = new TextView(a);
        title.setText(a.getString(com.termux.R.string.feature_input_text_23));
        title.setTextSize(22);
        content.addView(title);
        List<InputActions.Action> actions = InputActions.all(a);
        actions.removeIf(x -> x.id.equals("feature.input_quick"));
        actions.sort(
                (x, y) ->
                        Long.compare(
                                InputActions.prefs(a).getLong("use." + y.id, 0),
                                InputActions.prefs(a).getLong("use." + x.id, 0)));
        List<InputActions.Action> shown = new ArrayList<>();
        for (InputActions.Action action : actions) if (action.command != null) shown.add(action);
        for (InputActions.Action action : actions)
            if (action.command == null && shown.size() < 12) shown.add(action);
        for (InputActions.Action action : shown)
            button(
                    a,
                    content,
                    action.title,
                    () -> {
                        dialog.dismiss();
                        InputActions.execute(a, action);
                    });
        button(
                a,
                content,
                a.getString(com.termux.R.string.feature_input_text_21),
                () -> {
                    dialog.dismiss();
                    commands(a);
                });
        ScrollView scroll = new ScrollView(a);
        scroll.addView(content);
        dialog.setContentView(scroll);
        dialog.show();
    }

    static void commands(TermuxActivity a) {
        org.json.JSONArray saved = InputActions.saved(a);
        List<String> titles = new ArrayList<>();
        titles.add(a.getString(com.termux.R.string.feature_input_text_24));
        for (int i = 0; i < saved.length(); i++)
            titles.add(saved.optJSONObject(i).optString("title"));
        new MaterialAlertDialogBuilder(a)
                .setTitle(a.getString(com.termux.R.string.feature_input_text_25))
                .setItems(
                        titles.toArray(new String[0]),
                        (d, w) -> {
                            if (w == 0) {
                                LinearLayout form = column(a);
                                EditText title =
                                        field(
                                                a,
                                                form,
                                                a.getString(
                                                        com.termux.R.string.feature_input_text_26),
                                                "");
                                EditText cmd =
                                        field(
                                                a,
                                                form,
                                                a.getString(
                                                        com.termux.R.string.feature_input_text_27),
                                                "");
                                androidx.appcompat.app.AlertDialog editor =
                                        new MaterialAlertDialogBuilder(a)
                                                .setTitle(
                                                        a.getString(
                                                                com.termux.R.string
                                                                        .feature_input_text_28))
                                                .setView(form)
                                                .setNegativeButton(android.R.string.cancel, null)
                                                .setPositiveButton(
                                                        a.getString(
                                                                com.termux.R.string
                                                                        .feature_input_text_29),
                                                        null)
                                                .create();
                                editor.setOnShowListener(
                                        v ->
                                                editor.getButton(-1)
                                                        .setOnClickListener(
                                                                b -> {
                                                                    if (title.length() == 0
                                                                            || cmd.length() == 0) {
                                                                        cmd.setError(
                                                                                a.getString(
                                                                                        com.termux.R
                                                                                                .string
                                                                                                .feature_input_text_30));
                                                                        return;
                                                                    }
                                                                    InputActions.saveCommand(
                                                                            a,
                                                                            title.getText()
                                                                                    .toString(),
                                                                            cmd.getText()
                                                                                    .toString());
                                                                    editor.dismiss();
                                                                }));
                                editor.show();
                            } else
                                new MaterialAlertDialogBuilder(a)
                                        .setTitle(titles.get(w))
                                        .setItems(
                                                new String[] {
                                                    a.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_14),
                                                    a.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_31)
                                                },
                                                (x, choice) -> {
                                                    if (choice == 0)
                                                        InputActions.commandOptions(
                                                                a,
                                                                saved.optJSONObject(w - 1)
                                                                        .optString("command"));
                                                    else {
                                                        saved.remove(w - 1);
                                                        InputActions.prefs(a)
                                                                .edit()
                                                                .putString(
                                                                        "commands",
                                                                        saved.toString())
                                                                .apply();
                                                    }
                                                })
                                        .show();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static View toolbar(TermuxActivity a) {
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setTag("input_toolbar");
        LinearLayout row = new LinearLayout(a);
        // The buttons fill the toolbar height, like the extra keys.
        scroll.setFillViewport(true);
        scroll.addView(row, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        Runnable refresh =
                () -> {
                    row.removeAllViews();
                    for (String id : InputActions.toolbar(a))
                        for (InputActions.Action action : InputActions.all(a))
                            if (action.id.equals(id)) {
                                MaterialButton b = new MaterialButton(a);
                                b.setText(action.title.split(" · ")[0]);
                                b.setAllCaps(false);
                                styleToolbarButton(a, b);
                                row.addView(b, toolbarButtonParams(a));
                                b.setOnClickListener(v -> InputActions.execute(a, action));
                            }
                    MaterialButton edit = new MaterialButton(a);
                    edit.setText(a.getString(com.termux.R.string.feature_input_text_32));
                    edit.setAllCaps(false);
                    styleToolbarButton(a, edit);
                    row.addView(edit, toolbarButtonParams(a));
                    edit.setOnClickListener(v -> toolbarEditor(a));
                };
        row.setTag(refresh);
        scroll.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) {
                        refresh.run();
                    }

                    public void onViewDetachedFromWindow(View v) {}
                });
        scroll.setOnFocusChangeListener(
                (v, focus) -> {
                    if (focus) refresh.run();
                });
        refresh.run();
        return scroll;
    }

    /** Make a toolbar action button look like the extra keys, which share the toolbar. */
    private static void styleToolbarButton(TermuxActivity a, MaterialButton button) {
        if (a.getExtraKeysView() != null) a.getExtraKeysView().styleKey(button);
        int[] colors = a.getTerminalToolbarColors();
        if (colors != null) button.setTextColor(colors[1]);
        if (a.getTerminalToolbarTypeface() != null) button.setTypeface(a.getTerminalToolbarTypeface());
        int padding = Math.round(12 * a.getResources().getDisplayMetrics().density);
        button.setPadding(padding, 0, padding, 0);
    }

    private static LinearLayout.LayoutParams toolbarButtonParams(TermuxActivity a) {
        int margin = Math.round(2 * a.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    public static void toolbarEditor(TermuxActivity a) {
        List<String> ids = InputActions.toolbar(a);
        List<InputActions.Action> catalog = InputActions.all(a);
        LinearLayout rows = column(a);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(rows);
        Runnable[] render = new Runnable[1];
        render[0] =
                () -> {
                    rows.removeAllViews();
                    TextView help = new TextView(a);
                    help.setText(a.getString(com.termux.R.string.feature_input_text_33));
                    rows.addView(help);
                    for (int i = 0; i < ids.size(); i++) {
                        final int index = i;
                        String label = ids.get(i);
                        for (InputActions.Action action : catalog)
                            if (action.id.equals(label)) {
                                label = action.title;
                                break;
                            }
                        MaterialButton b =
                                button(
                                        a,
                                        rows,
                                        label,
                                        () -> {
                                            ids.remove(index);
                                            render[0].run();
                                        });
                        b.setOnLongClickListener(
                                v -> {
                                    v.startDragAndDrop(
                                            ClipData.newPlainText(
                                                    "toolbar", Integer.toString(index)),
                                            new View.DragShadowBuilder(v),
                                            ids,
                                            0);
                                    return true;
                                });
                        b.setOnDragListener(
                                (v, event) -> {
                                    if (event.getAction() == DragEvent.ACTION_DRAG_STARTED)
                                        return event.getLocalState() == ids;
                                    if (event.getAction() == DragEvent.ACTION_DROP
                                            && event.getLocalState() == ids) {
                                        int from =
                                                Integer.parseInt(
                                                        event.getClipData()
                                                                .getItemAt(0)
                                                                .getText()
                                                                .toString());
                                        ids.add(index, ids.remove(from));
                                        render[0].run();
                                        return true;
                                    }
                                    return true;
                                });
                    }
                    button(
                            a,
                            rows,
                            a.getString(com.termux.R.string.feature_input_text_34),
                            () ->
                                    new MaterialAlertDialogBuilder(a)
                                            .setTitle(
                                                    a.getString(
                                                            com.termux.R.string
                                                                    .feature_input_text_35))
                                            .setItems(
                                                    catalog.stream()
                                                            .map(x -> x.title)
                                                            .toArray(String[]::new),
                                                    (d, w) -> {
                                                        ids.add(catalog.get(w).id);
                                                        render[0].run();
                                                    })
                                            .show());
                };
        render[0].run();
        new MaterialAlertDialogBuilder(a)
                .setTitle(a.getString(com.termux.R.string.feature_input_text_36))
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(
                        a.getString(com.termux.R.string.feature_input_text_29),
                        (d, w) -> {
                            InputActions.saveToolbar(a, ids);
                            View toolbar =
                                    a.getTerminalToolbarViewPager()
                                            .findViewWithTag("input_toolbar");
                            if (toolbar instanceof HorizontalScrollView) {
                                Object refresh =
                                        ((HorizontalScrollView) toolbar).getChildAt(0).getTag();
                                if (refresh instanceof Runnable) ((Runnable) refresh).run();
                            }
                            a.getTerminalToolbarViewPager().setCurrentItem(2);
                        })
                .show();
    }
}
