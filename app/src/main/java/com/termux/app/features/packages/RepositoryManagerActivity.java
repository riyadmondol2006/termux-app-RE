package com.termux.app.features.packages;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.features.TermuxCommandRunner;
import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RepositoryManagerActivity extends PackageBaseActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final File aptEtc = new File(TermuxConstants.TERMUX_ETC_PREFIX_DIR_PATH, "apt");
    private final File prefixEtc = TermuxConstants.TERMUX_ETC_PREFIX_DIR;

    private LinearLayout sourcesContainer;
    private LinearLayout keysContainer;
    private TextView mirrorStatus;
    private TextView status;
    private MaterialSwitch officialSwitch;
    private boolean updatingOfficialSwitch;
    private List<MirrorOption> mirrorOptions = new ArrayList<>();
    private String currentMirror;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildContent();
        reload();
    }

    @Override
    protected int getTitleResource() {
        return R.string.feature_packages_repositories_title;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = PackageUi.vertical(this);
        int padding = PackageUi.dp(content, 16);
        content.setPadding(padding, padding, padding, padding);
        scrollView.addView(content);

        MaterialCardView warning = PackageUi.card(this);
        LinearLayout warningContent = PackageUi.cardContent(warning);
        warningContent.addView(PackageUi.heading(this, R.string.feature_packages_repo_warning_title));
        warningContent.addView(PackageUi.text(this, R.string.feature_packages_repo_warning, 15));
        content.addView(warning);

        MaterialCardView official = PackageUi.card(this);
        LinearLayout officialContent = PackageUi.cardContent(official);
        officialSwitch = new MaterialSwitch(this);
        officialSwitch.setText(R.string.feature_packages_official_compat_title);
        officialContent.addView(officialSwitch);
        officialContent.addView(PackageUi.text(this, R.string.feature_packages_official_compat_description, 14));
        officialSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingOfficialSwitch) return;
            officialSwitch.setEnabled(false);
            executor.execute(() -> {
                try {
                    OfficialPackages.stage(this);
                    TermuxCommandRunner.Result result = TermuxCommandRunner.runBlocking(this,
                        new String[]{"bash", TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/termux-re-official",
                            checked ? "on" : "off"}, null, null, 30000);
                    if (!result.isSuccessful()) throw new java.io.IOException(result.stderr + " " + result.error);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        reload();
                        PackageTerminalLauncher.run(this, "Update package lists", "apt update");
                    });
                } catch (Exception e) {
                    showEditError(e);
                }
            });
        });
        content.addView(official);

        content.addView(PackageUi.heading(this, R.string.feature_packages_sources));
        LinearLayout actions = PackageUi.vertical(this);
        MaterialButton add = PackageUi.button(this, R.string.feature_packages_add_repository);
        add.setOnClickListener(view -> showAddRepository());
        actions.addView(add);
        MaterialButton refresh = PackageUi.button(this, R.string.feature_packages_refresh);
        refresh.setOnClickListener(view -> reload());
        actions.addView(refresh);
        content.addView(actions);
        sourcesContainer = PackageUi.vertical(this);
        content.addView(sourcesContainer);

        content.addView(PackageUi.heading(this, R.string.feature_packages_mirrors));
        content.addView(PackageUi.text(this, R.string.feature_packages_mirror_explanation, 14));
        mirrorStatus = PackageUi.text(this, "", 14);
        content.addView(mirrorStatus);
        MaterialButton selectMirror = PackageUi.button(this, R.string.feature_packages_select_mirror);
        selectMirror.setOnClickListener(view -> showMirrorSelection());
        content.addView(selectMirror);
        MaterialButton terminalMirror = PackageUi.button(this, R.string.feature_packages_open_change_repo);
        terminalMirror.setOnClickListener(view -> PackageTerminalLauncher.run(this, "Change repository",
            "termux-change-repo"));
        content.addView(terminalMirror);

        content.addView(PackageUi.heading(this, R.string.feature_packages_trusted_keys));
        content.addView(PackageUi.text(this, R.string.feature_packages_keys_explanation, 14));
        keysContainer = PackageUi.vertical(this);
        content.addView(keysContainer);
        status = PackageUi.text(this, "", 13);
        content.addView(status);
        setPackageContent(scrollView);
    }

    private void reload() {
        updatingOfficialSwitch = true;
        officialSwitch.setChecked(OfficialPackages.enabled(new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH)));
        officialSwitch.setEnabled(TermuxCommandRunner.isBootstrapInstalled());
        updatingOfficialSwitch = false;
        status.setText(R.string.feature_packages_loading);
        executor.execute(() -> {
            try {
                Snapshot snapshot = new Snapshot(RepositoryStore.loadSources(aptEtc), RepositoryStore.loadKeys(aptEtc),
                    RepositoryStore.loadMirrors(prefixEtc), RepositoryStore.currentMirror(prefixEtc));
                handler.post(() -> {
                    if (isDestroyed()) return;
                    showSources(snapshot.sources);
                    showKeys(snapshot.keys);
                    mirrorOptions = snapshot.mirrors;
                    currentMirror = snapshot.currentMirror;
                    updateMirrorStatus();
                    status.setText("");
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isDestroyed()) status.setText(getString(R.string.feature_packages_error, message(e)));
                });
            }
        });
    }

    private void showSources(@NonNull List<AptSourceEntry> sources) {
        sourcesContainer.removeAllViews();
        if (sources.isEmpty()) {
            sourcesContainer.addView(PackageUi.text(this, R.string.feature_packages_no_sources, 15));
            return;
        }
        for (AptSourceEntry source : sources) {
            MaterialCardView card = PackageUi.card(this);
            LinearLayout content = PackageUi.cardContent(card);
            TextView declaration = PackageUi.text(this, source.declaration, 15);
            declaration.setTypeface(android.graphics.Typeface.MONOSPACE);
            content.addView(declaration);
            content.addView(PackageUi.text(this, source.file.getName() + ":" + (source.line + 1), 12));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            MaterialSwitch enabled = new MaterialSwitch(this);
            enabled.setText(R.string.feature_packages_source_enabled);
            boolean managed = source.file.getName().equals("termux-re-official.list");
            enabled.setEnabled(!managed);
            enabled.setChecked(source.enabled);
            row.addView(enabled, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton remove = PackageUi.button(this, R.string.feature_packages_remove);
            row.addView(remove);
            remove.setEnabled(!managed);
            content.addView(row);
            enabled.setOnCheckedChangeListener((button, checked) -> {
                if (checked == source.enabled) return;
                editSource(() -> RepositoryStore.setEnabled(source, checked));
            });
            remove.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.feature_packages_remove_repository)
                .setMessage(source.declaration)
                .setPositiveButton(R.string.feature_packages_remove, (dialog, which) ->
                    editSource(() -> RepositoryStore.remove(source)))
                .setNegativeButton(android.R.string.cancel, null).show());
            sourcesContainer.addView(card);
        }
    }

    private void showKeys(@NonNull List<File> keys) {
        keysContainer.removeAllViews();
        if (keys.isEmpty()) {
            keysContainer.addView(PackageUi.text(this, R.string.feature_packages_no_keys, 15));
            return;
        }
        for (File key : keys) {
            MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(key.getName() + " · " + key.length() + " B");
            button.setOnClickListener(view -> showKey(key));
            keysContainer.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void showKey(@NonNull File key) {
        TermuxCommandRunner.run(this, new String[]{"gpg", "--show-keys", "--with-fingerprint", key.getAbsolutePath()},
            null, null, 30000, result -> {
                if (isDestroyed()) return;
                String output = result.stdout.trim().isEmpty() ? result.stderr : result.stdout;
                if (!result.isSuccessful() && output.trim().isEmpty()) output = result.error;
                ScrollView scroll = new ScrollView(this);
                TextView text = PackageUi.text(this, output == null ? "" : output, 13);
                text.setTypeface(android.graphics.Typeface.MONOSPACE);
                text.setTextIsSelectable(true);
                int padding = PackageUi.dp(text, 20);
                text.setPadding(padding, padding, padding, padding);
                scroll.addView(text);
                new MaterialAlertDialogBuilder(this).setTitle(key.getName()).setView(scroll)
                    .setPositiveButton(android.R.string.ok, null).show();
            });
    }

    private void showAddRepository() {
        LinearLayout form = PackageUi.vertical(this);
        int padding = PackageUi.dp(form, 20);
        form.setPadding(padding, padding, padding, padding);
        TextInputEditText url = addField(form, R.string.feature_packages_repo_url, "https://");
        TextInputEditText suite = addField(form, R.string.feature_packages_repo_suite, "stable");
        TextInputEditText components = addField(form, R.string.feature_packages_repo_components, "main");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_packages_add_repository).setView(scroll)
            .setPositiveButton(R.string.feature_packages_add, (dialog, which) -> {
                String repositoryUrl = text(url);
                String repositorySuite = text(suite);
                String repositoryComponents = text(components);
                if (!repositoryUrl.matches("https?://[^\\s]+") || repositorySuite.isEmpty() ||
                    repositoryComponents.isEmpty() || repositorySuite.contains("\n") || repositoryComponents.contains("\n")) {
                    Toast.makeText(this, R.string.feature_packages_invalid_repository, Toast.LENGTH_LONG).show();
                    return;
                }
                Runnable add = () -> editSource(() -> RepositoryStore.add(aptEtc, repositoryUrl, repositorySuite,
                    repositoryComponents));
                if (RepositorySafety.isOfficialTermuxRepository(repositoryUrl)) {
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_packages_official_repo_title)
                        .setMessage(R.string.feature_packages_official_repo_warning)
                        .setPositiveButton(R.string.feature_packages_add_anyway, (warning, warningWhich) -> add.run())
                        .setNegativeButton(android.R.string.cancel, null).show();
                } else add.run();
            }).setNegativeButton(android.R.string.cancel, null).show();
    }

    @NonNull
    private TextInputEditText addField(@NonNull LinearLayout form, int hint, @NonNull String value) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        TextInputEditText input = new TextInputEditText(this);
        input.setText(value);
        input.setSingleLine(true);
        layout.addView(input);
        form.addView(layout);
        return input;
    }

    @NonNull
    private static String text(@NonNull TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showMirrorSelection() {
        if (mirrorOptions.isEmpty()) {
            Toast.makeText(this, R.string.feature_packages_no_mirrors, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[mirrorOptions.size()];
        for (int i = 0; i < names.length; i++) names[i] = mirrorOptions.get(i).name;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_packages_select_mirror)
            .setItems(names, (dialog, which) -> confirmMirror(mirrorOptions.get(which)))
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmMirror(@NonNull MirrorOption option) {
        new MaterialAlertDialogBuilder(this).setTitle(option.name)
            .setMessage(R.string.feature_packages_mirror_warning)
            .setPositiveButton(R.string.feature_packages_use_mirror, (dialog, which) -> executor.execute(() -> {
                try {
                    RepositoryStore.selectMirror(prefixEtc, option);
                    afterEdit("chosen_mirrors", "pkg --check-mirror update");
                } catch (Exception e) {
                    showEditError(e);
                }
            })).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void updateMirrorStatus() {
        if (currentMirror == null) mirrorStatus.setText(R.string.feature_packages_no_mirror_selected);
        else mirrorStatus.setText(getString(R.string.feature_packages_current_mirror, currentMirror));
    }

    private void editSource(@NonNull FileEdit edit) {
        status.setText(R.string.feature_packages_saving);
        executor.execute(() -> {
            try {
                File backup = edit.run();
                afterEdit(backup.getName(), "apt update");
            } catch (Exception e) {
                showEditError(e);
            }
        });
    }

    private void afterEdit(@NonNull String backupName, @NonNull String updateCommand) {
        handler.post(() -> {
            if (isDestroyed()) return;
            Toast.makeText(this, getString(R.string.feature_packages_saved_backup, backupName), Toast.LENGTH_LONG).show();
            reload();
            PackageTerminalLauncher.run(this, "Update package lists", updateCommand);
        });
    }

    private void showEditError(@NonNull Exception error) {
        handler.post(() -> {
            if (isDestroyed()) return;
            reload();
            status.setText(getString(R.string.feature_packages_error, message(error)));
            Toast.makeText(this, message(error), Toast.LENGTH_LONG).show();
        });
    }

    @NonNull
    private static String message(@NonNull Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }

    private interface FileEdit {
        File run() throws Exception;
    }

    private static final class Snapshot {
        final List<AptSourceEntry> sources;
        final List<File> keys;
        final List<MirrorOption> mirrors;
        final String currentMirror;

        Snapshot(List<AptSourceEntry> sources, List<File> keys, List<MirrorOption> mirrors, String currentMirror) {
            this.sources = sources;
            this.keys = keys;
            this.mirrors = mirrors;
            this.currentMirror = currentMirror;
        }
    }

}
