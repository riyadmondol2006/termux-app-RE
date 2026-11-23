package com.termux.app.features.theme;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.theme.NightMode;
import com.termux.terminal.TerminalEmulator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Theme Studio: a gallery of bundled and saved themes, an editor for the foreground, background,
 * cursor and 16 ANSI colors, the font, the cursor style, a background image, a live preview and
 * import/export of themes.
 */
public class ThemeStudioActivity extends AppCompatActivity {

    /** Theme files are small, larger files are not themes. */
    private static final int MAX_THEME_FILE_BYTES = 256 * 1024;

    private static final int KIND_FOREGROUND = 0;
    private static final int KIND_BACKGROUND = 1;
    private static final int KIND_CURSOR = 2;
    /** ANSI color {@code i} has the kind {@code KIND_ANSI + i}. */
    private static final int KIND_ANSI = 3;

    private TermuxTheme mEditing;
    private int mFontSize = 14;
    @Nullable private File mPendingFont;
    @Nullable private String mPendingFontName;
    /** Set while views are updated from {@link #mEditing}, so their listeners don't change it. */
    private boolean mBinding;

    private ThemePreviewView mPreview;
    private ChipGroup mGallery;
    private TextInputEditText mNameField;
    private LinearLayout mBaseColors;
    private LinearLayout mNormalColors;
    private LinearLayout mBrightColors;
    private TextView mFontSizeLabel;
    private TextView mFontStatus;
    private MaterialButtonToggleGroup mCursorStyle;
    private MaterialSwitch mCursorBlink;
    private TextView mBackgroundStatus;
    private Button mDeleteButton;

    private final ActivityResultLauncher<String[]> mImportLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open " + uri);
                TermuxTheme theme = TermuxThemeManager.importTheme(this, readText(in, MAX_THEME_FILE_BYTES));
                mEditing = theme;
                bindGallery();
                bindTheme();
                toast(getString(R.string.feature_theme_imported, theme.name));
            } catch (Exception e) {
                toast(getString(R.string.feature_theme_import_failed));
            }
        });

    private final ActivityResultLauncher<String> mExportLauncher = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("Cannot open " + uri);
                out.write(TermuxThemeManager.themeJson(mEditing).getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                toast(getString(R.string.feature_theme_export_failed));
            }
        });

    private final ActivityResultLauncher<String[]> mFontLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open " + uri);
                setPendingFont(in, "import-font.ttf", getString(R.string.feature_theme_font_imported_name));
            } catch (Exception e) {
                toast(getString(R.string.feature_theme_font_invalid));
            }
        });

    private final ActivityResultLauncher<String[]> mBackgroundLauncher = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) showBackgroundDialog(uri);
        });

    public static void start(@NonNull Activity from) {
        from.startActivity(new Intent(from, ThemeStudioActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_theme_studio);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.feature_theme_studio_title);

        mPreview = findViewById(R.id.feature_theme_preview);
        mGallery = findViewById(R.id.feature_theme_gallery);
        mNameField = findViewById(R.id.feature_theme_name);
        mBaseColors = findViewById(R.id.feature_theme_colors_base);
        mNormalColors = findViewById(R.id.feature_theme_colors_normal);
        mBrightColors = findViewById(R.id.feature_theme_colors_bright);
        mFontSizeLabel = findViewById(R.id.feature_theme_font_size_label);
        mFontStatus = findViewById(R.id.feature_theme_font_status);
        mCursorStyle = findViewById(R.id.feature_theme_cursor_style);
        mCursorBlink = findViewById(R.id.feature_theme_cursor_blink);
        mBackgroundStatus = findViewById(R.id.feature_theme_background_status);
        mDeleteButton = findViewById(R.id.feature_theme_delete);

        mEditing = TermuxThemeManager.getCurrentTheme(this);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(this, false);
        if (preferences != null) mFontSize = Math.max(8, Math.min(32, preferences.getFontSize()));

        setupName();
        setupFont();
        setupCursor();
        findViewById(R.id.feature_theme_pick_background).setOnClickListener(v -> mBackgroundLauncher.launch(new String[]{"image/*"}));
        findViewById(R.id.feature_theme_clear_background).setOnClickListener(v -> clearBackground());
        findViewById(R.id.feature_theme_import).setOnClickListener(v -> mImportLauncher.launch(new String[]{"application/json", "text/*", "*/*"}));
        findViewById(R.id.feature_theme_export).setOnClickListener(v -> mExportLauncher.launch(mEditing.id + ".json"));
        mDeleteButton.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.feature_theme_save).setOnClickListener(v -> saveTheme());
        findViewById(R.id.feature_theme_apply).setOnClickListener(v -> applyTheme());

        bindGallery();
        bindTheme();
        bindBackgroundStatus();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }


    private void setupName() {
        mNameField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (mBinding) return;
                String name = s.toString().trim();
                // The id stays: saving a bundled theme creates a copy, saving a saved theme updates it.
                if (!name.isEmpty() && !name.equals(mEditing.name)) mEditing = mEditing.copy(mEditing.id, name);
            }
        });
    }

    private void setupFont() {
        Slider slider = findViewById(R.id.feature_theme_font_size);
        slider.setValue(mFontSize);
        slider.addOnChangeListener((s, value, fromUser) -> {
            mFontSize = Math.round(value);
            refreshPreview();
        });
        findViewById(R.id.feature_theme_bundled_font).setOnClickListener(v -> {
            try (InputStream in = getAssets().open("theme/fonts/JetBrainsMono-Regular.ttf")) {
                setPendingFont(in, "bundled-font.ttf", "JetBrains Mono");
            } catch (Exception e) {
                toast(getString(R.string.feature_theme_font_invalid));
            }
        });
        findViewById(R.id.feature_theme_import_font).setOnClickListener(v ->
            mFontLauncher.launch(new String[]{"font/*", "application/x-font-ttf", "application/font-sfnt", "*/*"}));
        refreshPreview();
    }

    private void setupCursor() {
        int style = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE;
        boolean blink = false;
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.getProperties();
        if (properties != null) {
            style = properties.getTerminalCursorStyle();
            blink = properties.getTerminalCursorBlinkRate() > 0;
        }
        int buttonId;
        if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE) buttonId = R.id.feature_theme_cursor_underline;
        else if (style == TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR) buttonId = R.id.feature_theme_cursor_bar;
        else buttonId = R.id.feature_theme_cursor_block;
        mCursorStyle.check(buttonId);
        mCursorBlink.setChecked(blink);
    }

    @NonNull
    private String getCursorStyleValue() {
        int checked = mCursorStyle.getCheckedButtonId();
        if (checked == R.id.feature_theme_cursor_underline) return TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_UNDERLINE;
        if (checked == R.id.feature_theme_cursor_bar) return TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BAR;
        return TermuxPropertyConstants.VALUE_TERMINAL_CURSOR_STYLE_BLOCK;
    }


    /** Show all themes as chips, with the one being edited checked. */
    private void bindGallery() {
        mGallery.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (TermuxTheme theme : TermuxThemeManager.getThemes(this)) {
            Chip chip = (Chip) inflater.inflate(R.layout.feature_theme_chip, mGallery, false);
            chip.setId(View.generateViewId());
            chip.setText(theme.name);
            chip.setChipIcon(themeIcon(theme));
            chip.setChecked(theme.id.equals(mEditing.id));
            chip.setOnClickListener(v -> {
                mEditing = theme.copy(theme.id, theme.name);
                checkGalleryChip();
                bindTheme();
            });
            chip.setTag(theme.id);
            mGallery.addView(chip);
        }
    }

    private void checkGalleryChip() {
        for (int i = 0; i < mGallery.getChildCount(); i++) {
            Chip chip = (Chip) mGallery.getChildAt(i);
            chip.setChecked(mEditing.id.equals(chip.getTag()));
        }
    }

    /** Update the editor views from {@link #mEditing}. */
    private void bindTheme() {
        mBinding = true;
        try {
            mNameField.setText(mEditing.name);
        } finally {
            mBinding = false;
        }

        mBaseColors.removeAllViews();
        addBaseColorRow(R.string.feature_theme_foreground, KIND_FOREGROUND);
        addBaseColorRow(R.string.feature_theme_background_color, KIND_BACKGROUND);
        addBaseColorRow(R.string.feature_theme_cursor, KIND_CURSOR);

        mNormalColors.removeAllViews();
        mBrightColors.removeAllViews();
        for (int i = 0; i < TermuxTheme.ANSI_COUNT; i++)
            addAnsiSwatch(i < 8 ? mNormalColors : mBrightColors, KIND_ANSI + i);

        mDeleteButton.setVisibility(TermuxThemeManager.isBundled(mEditing.id) ? View.GONE : View.VISIBLE);
        refreshPreview();
    }

    private void addBaseColorRow(@StringRes int label, int kind) {
        View row = LayoutInflater.from(this).inflate(R.layout.feature_theme_item_color, mBaseColors, false);
        View swatch = row.findViewById(R.id.feature_theme_color_swatch);
        TextView name = row.findViewById(R.id.feature_theme_color_name);
        TextView hex = row.findViewById(R.id.feature_theme_color_hex);
        int color = getThemeColor(kind);
        swatch.setBackground(swatchDrawable(color, 10));
        name.setText(label);
        hex.setText(TermuxTheme.toHex(color));
        row.setOnClickListener(v -> showColorDialog(kind));
        mBaseColors.addView(row);
    }

    private void addAnsiSwatch(@NonNull LinearLayout parent, int kind) {
        float density = getResources().getDisplayMetrics().density;
        View swatch = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int) (40 * density), 1f);
        int margin = (int) (3 * density);
        params.setMargins(margin, 0, margin, 0);
        swatch.setLayoutParams(params);
        int color = getThemeColor(kind);
        swatch.setBackground(swatchDrawable(color, 8));
        swatch.setContentDescription(getString(R.string.feature_theme_color_description, getColorName(kind), TermuxTheme.toHex(color)));
        swatch.setClickable(true);
        swatch.setFocusable(true);
        swatch.setOnClickListener(v -> showColorDialog(kind));
        parent.addView(swatch);
    }

    private void showColorDialog(int kind) {
        View view = LayoutInflater.from(this).inflate(R.layout.feature_theme_dialog_color, null, false);
        View swatch = view.findViewById(R.id.feature_theme_color_dialog_swatch);
        TextInputLayout layout = view.findViewById(R.id.feature_theme_color_dialog_layout);
        TextInputEditText input = view.findViewById(R.id.feature_theme_color_dialog_input);
        int color = getThemeColor(kind);
        swatch.setBackground(swatchDrawable(color, 12));
        input.setText(TermuxTheme.toHex(color));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                Integer parsed = TermuxTheme.parseHex(s.toString());
                layout.setError(parsed == null ? getString(R.string.feature_theme_bad_hex) : null);
                if (parsed != null) swatch.setBackground(swatchDrawable(parsed, 12));
            }
        });

        new MaterialAlertDialogBuilder(this)
            .setTitle(getColorName(kind))
            .setView(view)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                Integer parsed = TermuxTheme.parseHex(String.valueOf(input.getText()));
                if (parsed == null) {
                    toast(getString(R.string.feature_theme_bad_hex));
                    return;
                }
                setThemeColor(kind, parsed);
                bindTheme();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private String getColorName(int kind) {
        if (kind == KIND_FOREGROUND) return getString(R.string.feature_theme_foreground);
        if (kind == KIND_BACKGROUND) return getString(R.string.feature_theme_background_color);
        if (kind == KIND_CURSOR) return getString(R.string.feature_theme_cursor);
        int index = kind - KIND_ANSI;
        return index + " · " + TermuxTheme.ANSI_NAMES[index];
    }

    private int getThemeColor(int kind) {
        if (kind == KIND_FOREGROUND) return mEditing.foreground;
        if (kind == KIND_BACKGROUND) return mEditing.background;
        if (kind == KIND_CURSOR) return mEditing.cursor;
        return mEditing.ansi[kind - KIND_ANSI];
    }

    private void setThemeColor(int kind, int color) {
        if (kind == KIND_FOREGROUND) mEditing.foreground = color;
        else if (kind == KIND_BACKGROUND) mEditing.background = color;
        else if (kind == KIND_CURSOR) mEditing.cursor = color;
        else mEditing.ansi[kind - KIND_ANSI] = color;
    }

    /** A rounded swatch of {@code color} with an outline, so dark colors show on a dark surface. */
    @NonNull
    private GradientDrawable swatchDrawable(int color, int radiusDp) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp * density);
        drawable.setStroke(Math.max(1, (int) density),
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x33000000));
        return drawable;
    }

    /** A chip icon: the theme's background with a ring of its foreground. */
    @NonNull
    private GradientDrawable themeIcon(@NonNull TermuxTheme theme) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(ColorStateList.valueOf(theme.background));
        drawable.setStroke((int) (3 * density), theme.foreground);
        return drawable;
    }


    private void setPendingFont(@NonNull InputStream in, @NonNull String cacheName, @NonNull String displayName) throws IOException {
        File file = new File(getCacheDir(), cacheName);
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        }
        // Throws if the file is not a font.
        Typeface.createFromFile(file);
        mPendingFont = file;
        mPendingFontName = displayName;
        refreshPreview();
        toast(getString(R.string.feature_theme_font_ready));
    }

    private void refreshPreview() {
        Typeface typeface = Typeface.MONOSPACE;
        if (mPendingFont != null) {
            try {
                typeface = Typeface.createFromFile(mPendingFont);
            } catch (Exception ignored) {
                // Keep the default font in the preview.
            }
        }
        mPreview.setTheme(mEditing, typeface, mFontSize);
        mFontSizeLabel.setText(getString(R.string.feature_theme_font_size, mFontSize));
        mFontStatus.setText(mPendingFontName != null
            ? getString(R.string.feature_theme_font_pending, mPendingFontName)
            : getString(R.string.feature_theme_font_current));
    }


    private void showBackgroundDialog(@NonNull Uri uri) {
        float density = getResources().getDisplayMetrics().density;
        Slider dim = new Slider(this);
        dim.setValueFrom(0);
        dim.setValueTo(90);
        dim.setStepSize(5);
        dim.setValue(50);
        dim.setContentDescription(getString(R.string.feature_theme_background_dim));
        LinearLayout container = new LinearLayout(this);
        int padding = (int) (16 * density);
        container.setPadding(padding, padding, padding, 0);
        container.addView(dim, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.feature_theme_background_dim)
            .setMessage(R.string.feature_theme_background_dim_hint)
            .setView(container)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                try {
                    TermuxThemeManager.setBackground(this, uri, 100 - Math.round(dim.getValue()), 0);
                    toast(getString(R.string.feature_theme_background_set));
                } catch (Exception e) {
                    toast(String.valueOf(e.getMessage()));
                }
                bindBackgroundStatus();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void clearBackground() {
        try {
            TermuxThemeManager.setBackground(this, null, 100, 0);
            toast(getString(R.string.feature_theme_background_cleared));
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
        bindBackgroundStatus();
    }

    private void bindBackgroundStatus() {
        boolean set = TermuxThemeManager.backgroundFile(this).isFile();
        mBackgroundStatus.setText(set
            ? getString(R.string.feature_theme_background_active, 100 - TermuxThemeManager.getBackgroundAlpha(this))
            : getString(R.string.feature_theme_background_none));
        findViewById(R.id.feature_theme_clear_background).setEnabled(set);
    }


    private void saveTheme() {
        TermuxTheme theme = mEditing;
        if (TermuxThemeManager.isBundled(theme.id)) {
            // Bundled themes can't be changed, save a copy.
            String name = theme.name;
            TermuxTheme bundled = TermuxThemeManager.getThemeById(this, theme.id);
            if (bundled != null && bundled.name.equals(name)) name = getString(R.string.feature_theme_copy_name, name);
            theme = theme.copy(getUnusedUserThemeId(name), name);
        }
        try {
            TermuxThemeManager.saveUserTheme(this, theme);
            mEditing = theme;
            bindGallery();
            bindTheme();
            toast(getString(R.string.feature_theme_saved));
        } catch (Exception e) {
            toast(String.valueOf(e.getMessage()));
        }
    }

    @NonNull
    private String getUnusedUserThemeId(@NonNull String name) {
        String base = "user-" + TermuxTheme.slugify(name);
        String id = base;
        for (int i = 2; TermuxThemeManager.getThemeById(this, id) != null; i++) id = base + "-" + i;
        return id;
    }

    private void confirmDelete() {
        if (TermuxThemeManager.isBundled(mEditing.id)) return;
        new MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.feature_theme_delete_confirm, mEditing.name))
            .setPositiveButton(R.string.feature_theme_delete, (dialog, which) -> {
                TermuxThemeManager.deleteUserTheme(this, mEditing.id);
                mEditing = BundledThemes.defaultTheme();
                bindGallery();
                bindTheme();
                toast(getString(R.string.feature_theme_deleted));
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void applyTheme() {
        try {
            TermuxThemeManager.applyTheme(this, mEditing, mPendingFont, mFontSize, getCursorStyleValue(), mCursorBlink.isChecked());
            toast(getString(R.string.feature_theme_applied));
        } catch (Exception e) {
            toast(getString(R.string.feature_theme_apply_failed) + ": " + e.getMessage());
        }
    }


    @NonNull
    private static String readText(@NonNull InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > maxBytes) throw new IOException("File too large");
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void toast(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
