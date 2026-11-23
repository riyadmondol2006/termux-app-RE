package com.termux.app.features.packages;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import com.termux.R;
import com.termux.app.features.TermuxCommandRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CompatibilityToolsActivity extends PackageBaseActivity {

    private static final int REQUEST_DEB = 42;
    private static final String INSTALLED_SCAN =
        "dpkg-query -W -f='${binary:Package}\\n' | while IFS= read -r package; do " +
        "dpkg -L \"$package\" 2>/dev/null | while IFS= read -r path; do " +
        "case \"$path\" in *'/data/data/com.termux/'*) " +
        "case \"$path\" in *'/data/data/com.termux.re/'*) ;; *) printf '%s\\t%s\\n' \"$package\" \"$path\" ;; esac ;; " +
        "esac; done; done";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout results;
    private TextView status;
    private MaterialButton scanInstalled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildContent();
    }

    @Override
    protected int getTitleResource() {
        return R.string.feature_packages_compatibility_title;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = PackageUi.vertical(this);
        int padding = PackageUi.dp(content, 16);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);

        content.addView(PackageUi.heading(this, R.string.feature_packages_why_title));
        content.addView(PackageUi.text(this, R.string.feature_packages_why_explanation, 16));
        content.addView(PackageUi.heading(this, R.string.feature_packages_options_title));
        content.addView(PackageUi.text(this, R.string.feature_packages_options_explanation, 15));
        MaterialButton request = PackageUi.button(this, R.string.feature_packages_request_general);
        request.setOnClickListener(view -> openUrl(
            "https://github.com/riyadmondol2006/termux-app-RE/issues/new?template=package-request.yml"));
        content.addView(request);
        MaterialButton official = PackageUi.button(this, R.string.feature_packages_get_official);
        official.setOnClickListener(view -> openUrl("https://termux.dev/en/"));
        content.addView(official);

        content.addView(PackageUi.heading(this, R.string.feature_packages_check_title));
        content.addView(PackageUi.text(this, R.string.feature_packages_check_explanation, 15));
        scanInstalled = PackageUi.button(this, R.string.feature_packages_scan_installed);
        scanInstalled.setOnClickListener(view -> scanInstalled());
        content.addView(scanInstalled);
        MaterialButton scanDeb = PackageUi.button(this, R.string.feature_packages_choose_deb);
        scanDeb.setOnClickListener(view -> chooseDeb());
        content.addView(scanDeb);
        status = PackageUi.text(this, "", 14);
        content.addView(status);
        results = PackageUi.vertical(this);
        content.addView(results);
        setPackageContent(scroll);
    }

    private void scanInstalled() {
        results.removeAllViews();
        scanInstalled.setEnabled(false);
        status.setText(R.string.feature_packages_scanning_installed);
        TermuxCommandRunner.runShell(this, INSTALLED_SCAN, null, result -> {
            if (isDestroyed()) return;
            scanInstalled.setEnabled(true);
            if (!result.isSuccessful()) {
                status.setText(getString(R.string.feature_packages_error,
                    result.error != null ? result.error : result.stderr));
                return;
            }
            showIssues(CompatibilityParser.parseInstalledScan(result.stdout), true);
        });
    }

    private void chooseDeb() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_DEB);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.feature_packages_no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DEB || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String displayName = displayName(uri);
        results.removeAllViews();
        status.setText(getString(R.string.feature_packages_scanning_deb, displayName));
        executor.execute(() -> {
            File local = new File(getCacheDir(), "feature_packages_scan.deb");
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(local)) {
                if (input == null) throw new IllegalStateException("Cannot open selected file");
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            } catch (Exception e) {
                runOnUiThread(() -> status.setText(getString(R.string.feature_packages_error,
                    e.getMessage() == null ? e.toString() : e.getMessage())));
                return;
            }
            TermuxCommandRunner.run(this, new String[]{"dpkg-deb", "--contents", local.getAbsolutePath()}, null,
                null, 60000, result -> {
                    local.delete();
                    if (isDestroyed()) return;
                    if (!result.isSuccessful()) {
                        status.setText(getString(R.string.feature_packages_error,
                            result.error != null ? result.error : result.stderr));
                        return;
                    }
                    showIssues(CompatibilityParser.parseDebContents(result.stdout, displayName), false);
                });
        });
    }

    @NonNull
    private String displayName(@NonNull Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null,
            null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        String name = uri.getLastPathSegment();
        return name == null ? "selected.deb" : name;
    }

    private void showIssues(@NonNull List<CompatibilityIssue> issues, boolean installed) {
        results.removeAllViews();
        if (issues.isEmpty()) {
            status.setText(installed ? R.string.feature_packages_no_incompatible_installed :
                R.string.feature_packages_no_incompatible_deb);
            return;
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (CompatibilityIssue issue : issues) grouped.computeIfAbsent(issue.packageName, key -> new ArrayList<>()).add(issue.path);
        status.setText(getString(R.string.feature_packages_incompatible_count, grouped.size(), issues.size()));
        results.addView(PackageUi.text(this, installed ? R.string.feature_packages_installed_guidance :
            R.string.feature_packages_deb_guidance, 15));
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            MaterialCardView card = PackageUi.card(this);
            LinearLayout content = PackageUi.cardContent(card);
            content.addView(PackageUi.heading(this, entry.getKey()));
            content.addView(PackageUi.text(this, R.string.feature_packages_foreign_prefix, 14));
            int shown = Math.min(entry.getValue().size(), 12);
            for (int i = 0; i < shown; i++) {
                TextView path = PackageUi.text(this, entry.getValue().get(i), 12);
                path.setTypeface(android.graphics.Typeface.MONOSPACE);
                path.setTextIsSelectable(true);
                content.addView(path);
            }
            if (entry.getValue().size() > shown) content.addView(PackageUi.text(this,
                getString(R.string.feature_packages_more_paths, entry.getValue().size() - shown), 13));
            results.addView(card);
        }
    }

    private void openUrl(@NonNull String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.feature_packages_no_browser, Toast.LENGTH_LONG).show();
        }
    }

}
