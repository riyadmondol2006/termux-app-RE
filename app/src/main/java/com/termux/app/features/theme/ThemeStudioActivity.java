package com.termux.app.features.theme;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Theme Studio: gallery, editor (16 ANSI + fg/bg/cursor), fonts, cursor style, background image, live preview, import/export. */
public class ThemeStudioActivity extends AppCompatActivity {

    private TermuxTheme mEditing;
    private ThemePreviewView mPreview;
    private LinearLayout mRoot;
    private TextView mFontSizeLabel;
    private int mFontSize = 14;
    @Nullable private File mPendingFont;
    private Spinner mCursorSpinner;
    private Switch mBlinkSwitch;
    private final List<EditText> mHexFields = new ArrayList<>();

    private final ActivityResultLauncher<String[]> mImportLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                byte[] buf = new byte[65536]; int n = in.read(buf);
                TermuxTheme t = TermuxThemeManager.importTheme(this, new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8));
                mEditing = t; rebuild(); toast(getString(R.string.feature_theme_imported, t.name));
            } catch (Exception e) { toast(getString(R.string.feature_theme_import_failed)); }
        });

    private final ActivityResultLauncher<String> mExportLauncher = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(TermuxThemeManager.themeJson(mEditing).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { toast(getString(R.string.feature_theme_export_failed)); }
        });

    private final ActivityResultLauncher<String[]> mFontLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try {
                File tmp = new File(getCacheDir(), "import-font.ttf");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                Typeface.createFromFile(tmp); // validates the TTF
                mPendingFont = tmp; refreshPreview();
                toast(getString(R.string.feature_theme_font_ready));
            } catch (Exception e) { toast(getString(R.string.feature_theme_font_invalid)); }
        });

    /** Use the JetBrains Mono font bundled in assets/theme/fonts (SIL Open Font License 1.1). */
    private void useBundledFont() {
        try {
            File tmp = new File(getCacheDir(), "bundled-font.ttf");
            try (InputStream in = getAssets().open("theme/fonts/JetBrainsMono-Regular.ttf");
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Typeface.createFromFile(tmp); // validates the TTF
            mPendingFont = tmp; refreshPreview();
            toast(getString(R.string.feature_theme_font_ready));
        } catch (Exception e) { toast(getString(R.string.feature_theme_font_invalid)); }
    }

    private final ActivityResultLauncher<String[]> mBackgroundLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            showBackgroundDialog(uri);
        });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEditing = TermuxThemeManager.getCurrentTheme(this);
        com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences prefs =
            com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.build(this, false);
        if (prefs != null) mFontSize = prefs.getFontSize();
        rebuild();
    }

    private void rebuild() {
        mHexFields.clear();
        ScrollView scroll = new ScrollView(this);
        mRoot = new LinearLayout(this);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        mRoot.setPadding(pad, pad, pad, pad);
        scroll.addView(mRoot);
        setContentView(scroll);
        setTitle(R.string.feature_theme_studio_title);

        mPreview = new ThemePreviewView(this);
        mPreview.setMinimumHeight((int) (150 * getResources().getDisplayMetrics().density));
        mRoot.addView(mPreview);
        refreshPreview();

        addHeader(R.string.feature_theme_gallery);
        for (TermuxTheme t : TermuxThemeManager.getThemes(this)) {
            Button b = new Button(this);
            b.setText((TermuxThemeManager.isBundled(t.id) ? "" : "★ ") + t.name);
            b.setOnClickListener(v -> { mEditing = t.copy(t.id, t.name); rebuild(); });
            mRoot.addView(b);
        }

        addHeader(R.string.feature_theme_editor);
        TextView nameLabel = new TextView(this); nameLabel.setText(R.string.feature_theme_name); mRoot.addView(nameLabel);
        EditText nameField = new EditText(this); nameField.setText(mEditing.name); mRoot.addView(nameField);
        nameField.addTextChangedListener(simpleWatcher(() -> {
            String n = nameField.getText().toString().trim();
            if (!n.isEmpty() && TermuxThemeManager.isBundled(mEditing.id))
                mEditing = mEditing.copy("user-" + TermuxTheme.slugify(n), n);
        }));
        addColorRow(R.string.feature_theme_foreground, 0);
        addColorRow(R.string.feature_theme_background_color, 1);
        addColorRow(R.string.feature_theme_cursor, 2);
        for (int i = 0; i < TermuxTheme.ANSI_COUNT; i++) addColorRow(TermuxTheme.ANSI_NAMES[i], 3 + i);

        addHeader(R.string.feature_theme_font);
        mFontSizeLabel = new TextView(this); mRoot.addView(mFontSizeLabel);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMin(8); sizeBar.setMax(32); sizeBar.setProgress(mFontSize);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { mFontSize = p; refreshPreview(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        mRoot.addView(sizeBar);
        Button fontButton = new Button(this);
        fontButton.setText(R.string.feature_theme_import_font);
        fontButton.setOnClickListener(v -> mFontLauncher.launch(new String[]{"font/*", "application/x-font-ttf", "*/*"}));
        mRoot.addView(fontButton);
        Button bundledFontButton = new Button(this);
        bundledFontButton.setText(R.string.feature_theme_bundled_font);
        bundledFontButton.setOnClickListener(v -> useBundledFont());
        mRoot.addView(bundledFontButton);

        addHeader(R.string.feature_theme_cursor_section);
        mCursorSpinner = new Spinner(this);
        mCursorSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"block", "underline", "bar"}));
        mRoot.addView(mCursorSpinner);
        mBlinkSwitch = new Switch(this);
        mBlinkSwitch.setText(R.string.feature_theme_cursor_blink);
        mBlinkSwitch.setChecked(true);
        mRoot.addView(mBlinkSwitch);

        addHeader(R.string.feature_theme_background_section);
        Button bgButton = new Button(this);
        bgButton.setText(R.string.feature_theme_pick_background);
        bgButton.setOnClickListener(v -> mBackgroundLauncher.launch(new String[]{"image/*"}));
        mRoot.addView(bgButton);
        Button bgClear = new Button(this);
        bgClear.setText(R.string.feature_theme_clear_background);
        bgClear.setOnClickListener(v -> {
            try { TermuxThemeManager.setBackground(this, null, 100, 0); toast(getString(R.string.feature_theme_background_cleared)); }
            catch (Exception e) { toast(e.getMessage()); }
        });
        mRoot.addView(bgClear);

        Button apply = new Button(this);
        apply.setText(R.string.feature_theme_apply);
        apply.setOnClickListener(v -> applyTheme());
        mRoot.addView(apply);
        Button save = new Button(this);
        save.setText(R.string.feature_theme_save_copy);
        save.setOnClickListener(v -> {
            try { TermuxThemeManager.saveUserTheme(this, mEditing); toast(getString(R.string.feature_theme_saved)); rebuild(); }
            catch (Exception e) { toast(e.getMessage()); }
        });
        mRoot.addView(save);
        Button importB = new Button(this);
        importB.setText(R.string.feature_theme_import);
        importB.setOnClickListener(v -> mImportLauncher.launch(new String[]{"*/*"}));
        mRoot.addView(importB);
        Button exportB = new Button(this);
        exportB.setText(R.string.feature_theme_export);
        exportB.setOnClickListener(v -> mExportLauncher.launch(mEditing.id + ".json"));
        mRoot.addView(exportB);
        if (!TermuxThemeManager.isBundled(mEditing.id)) {
            Button del = new Button(this);
            del.setText(R.string.feature_theme_delete);
            del.setOnClickListener(v -> { TermuxThemeManager.deleteUserTheme(this, mEditing.id); mEditing = BundledThemes.defaultTheme(); rebuild(); });
            mRoot.addView(del);
        }
        refreshPreview();
    }

    private void addHeader(int resId) {
        TextView t = new TextView(this);
        t.setText(resId);
        t.setTextSize(18);
        t.setPadding(0, 24, 0, 8);
        mRoot.addView(t);
    }

    /** kind: 0=foreground 1=background 2=cursor 3+i=ansi i. */
    private void addColorRow(Object label, int kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View swatch = new View(this);
        swatch.setMinimumWidth(48); swatch.setMinimumHeight(48);
        swatch.setBackgroundColor(themeColor(kind));
        EditText hex = new EditText(this);
        hex.setText(TermuxTheme.toHex(themeColor(kind)));
        hex.setSingleLine(true);
        mHexFields.add(hex);
        hex.addTextChangedListener(simpleWatcher(() -> {
            Integer c = TermuxTheme.parseHex(hex.getText().toString());
            if (c != null) { setColor(kind, c); swatch.setBackgroundColor(c); refreshPreview(); }
        }));
        swatch.setOnClickListener(v -> showColorDialog(kind, swatch, hex));
        TextView name = new TextView(this);
        name.setText(label instanceof Integer ? getString((Integer) label) : String.valueOf(label));
        row.addView(swatch); row.addView(name); row.addView(hex);
        mRoot.addView(row);
    }

    private void showColorDialog(int kind, View swatch, EditText hex) {
        EditText input = new EditText(this);
        input.setText(TermuxTheme.toHex(themeColor(kind)));
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.feature_theme_edit_color)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                Integer c = TermuxTheme.parseHex(input.getText().toString());
                if (c != null) { setColor(kind, c); hex.setText(TermuxTheme.toHex(c)); swatch.setBackgroundColor(c); refreshPreview(); }
                else toast(getString(R.string.feature_theme_bad_hex));
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showBackgroundDialog(Uri uri) {
        SeekBar dim = new SeekBar(this); dim.setMin(0); dim.setMax(90); dim.setProgress(50);
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.feature_theme_background_dim)
            .setMessage(R.string.feature_theme_background_dim_hint)
            .setView(dim)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                try { TermuxThemeManager.setBackground(this, uri, 100 - dim.getProgress(), 0); toast(getString(R.string.feature_theme_background_set)); }
                catch (Exception e) { toast(e.getMessage()); }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void applyTheme() {
        try {
            String style = (String) mCursorSpinner.getSelectedItem();
            TermuxThemeManager.applyTheme(this, mEditing, mPendingFont, mFontSize, style, mBlinkSwitch.isChecked());
            toast(getString(R.string.feature_theme_applied));
        } catch (Exception e) { toast(getString(R.string.feature_theme_apply_failed) + ": " + e.getMessage()); }
    }

    private int themeColor(int kind) {
        if (kind == 0) return mEditing.foreground;
        if (kind == 1) return mEditing.background;
        if (kind == 2) return mEditing.cursor;
        return mEditing.ansi[kind - 3];
    }
    private void setColor(int kind, int c) {
        if (kind == 0) mEditing.foreground = c;
        else if (kind == 1) mEditing.background = c;
        else if (kind == 2) mEditing.cursor = c;
        else mEditing.ansi[kind - 3] = c;
    }

    private void refreshPreview() {
        if (mPreview == null) return;
        Typeface tf = Typeface.MONOSPACE;
        if (mPendingFont != null) { try { tf = Typeface.createFromFile(mPendingFont); } catch (Exception ignored) {} }
        mPreview.setTheme(mEditing, tf, mFontSize);
        if (mFontSizeLabel != null) mFontSizeLabel.setText(getString(R.string.feature_theme_font_size, mFontSize));
    }

    private android.text.TextWatcher simpleWatcher(Runnable r) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) { r.run(); }
        };
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    public static void start(Activity from) { from.startActivity(new Intent(from, ThemeStudioActivity.class)); }
}
