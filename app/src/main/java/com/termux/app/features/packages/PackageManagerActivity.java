package com.termux.app.features.packages;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.features.TermuxCommandRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PackageManagerActivity extends PackageBaseActivity {

    private static final int MODE_ALL = 0;
    private static final int MODE_INSTALLED = 1;
    private static final int MODE_UPGRADABLE = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PackageInfo> displayedPackages = new ArrayList<>();
    private final Map<String, PackageInfo> allByName = new HashMap<>();
    private List<PackageInfo> allPackages = Collections.emptyList();
    private List<PackageInfo> installedPackages = Collections.emptyList();
    private List<PackageInfo> upgradablePackages = Collections.emptyList();

    private PackageAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView statusView;
    private TextView emptyView;
    private MaterialButton requestButton;
    private TextInputEditText searchInput;
    private int mode = MODE_ALL;
    private int refreshGeneration;
    private int searchGeneration;
    private boolean needsRefresh;
    private String currentQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildContent();
        refreshPackages(false);
    }

    @Override
    protected int getTitleResource() {
        return R.string.feature_packages_manager_title;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needsRefresh) {
            needsRefresh = false;
            refreshPackages(false);
        }
    }

    @Override
    protected void onDestroy() {
        refreshGeneration++;
        searchGeneration++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildContent() {
        LinearLayout content = PackageUi.vertical(this);
        int padding = PackageUi.dp(content, 16);
        content.setPadding(padding, 0, padding, 0);

        TextInputLayout searchLayout = new TextInputLayout(this);
        searchLayout.setHint(R.string.feature_packages_search_hint);
        searchLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        searchInput = new TextInputEditText(this);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPackages(searchInput.getText() == null ? "" : searchInput.getText().toString());
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable editable) {
                handler.removeCallbacks(searchRunnable);
                handler.postDelayed(searchRunnable, 450);
            }
        });
        searchLayout.addView(searchInput);
        content.addView(searchLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButtonToggleGroup modes = new MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);
        modes.setSelectionRequired(true);
        addModeButton(modes, R.string.feature_packages_tab_all, MODE_ALL);
        addModeButton(modes, R.string.feature_packages_tab_installed, MODE_INSTALLED);
        addModeButton(modes, R.string.feature_packages_tab_updates, MODE_UPGRADABLE);
        modes.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.feature_packages_mode_all) mode = MODE_ALL;
            else if (checkedId == R.id.feature_packages_mode_installed) mode = MODE_INSTALLED;
            else mode = MODE_UPGRADABLE;
            showCurrentList();
        });
        content.addView(modes, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialButton updateAll = PackageUi.button(this, R.string.feature_packages_update_all);
        updateAll.setOnClickListener(view -> confirmUpdateAll());
        content.addView(updateAll);

        statusView = PackageUi.text(this, "", 13);
        content.addView(statusView);

        FrameLayout listFrame = new FrameLayout(this);
        swipeRefresh = new SwipeRefreshLayout(this);
        ListView listView = new ListView(this);
        listView.setDividerHeight(0);
        adapter = new PackageAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> showDetails(displayedPackages.get(position)));
        swipeRefresh.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefresh.setOnRefreshListener(() -> refreshPackages(true));
        listFrame.addView(swipeRefresh, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout emptyLayout = PackageUi.vertical(this);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyView = PackageUi.text(this, R.string.feature_packages_loading, 16);
        emptyView.setGravity(Gravity.CENTER);
        emptyLayout.addView(emptyView);
        requestButton = PackageUi.button(this, R.string.feature_packages_request);
        requestButton.setOnClickListener(view -> requestPackage(currentQuery));
        requestButton.setVisibility(View.GONE);
        emptyLayout.addView(requestButton);
        listFrame.addView(emptyLayout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setEmptyView(emptyLayout);

        content.addView(listFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setPackageContent(content);
    }

    private final Runnable searchRunnable = () -> searchPackages(searchInput.getText() == null ? "" :
        searchInput.getText().toString());

    private void addModeButton(@NonNull MaterialButtonToggleGroup group, int label, int mode) {
        MaterialButton button = new MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(mode == MODE_ALL ? R.id.feature_packages_mode_all :
            mode == MODE_INSTALLED ? R.id.feature_packages_mode_installed : R.id.feature_packages_mode_updates);
        button.setText(label);
        group.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (mode == MODE_ALL) group.check(button.getId());
    }

    private void refreshPackages(boolean updateIndexes) {
        final int generation = ++refreshGeneration;
        searchGeneration++;
        swipeRefresh.setRefreshing(true);
        statusView.setText(updateIndexes ? R.string.feature_packages_updating_indexes : R.string.feature_packages_loading);
        if (updateIndexes) {
            TermuxCommandRunner.run(this, new String[]{"apt", "update"}, null, null, 180000, result -> {
                if (generation != refreshGeneration || isDestroyed()) return;
                if (!result.isSuccessful()) {
                    showRefreshError(result);
                    return;
                }
                loadLists(generation);
            });
        } else {
            loadLists(generation);
        }
    }

    private void loadLists(int generation) {
        TermuxCommandRunner.run(this, new String[]{"apt", "list"}, null, null, 120000, availableResult -> {
            if (generation != refreshGeneration || isDestroyed()) return;
            if (!availableResult.isSuccessful()) {
                showRefreshError(availableResult);
                return;
            }
            allPackages = AptPackageParser.parseAptList(availableResult.stdout);
            allByName.clear();
            for (PackageInfo item : allPackages) allByName.put(item.name, item);
            TermuxCommandRunner.run(this, new String[]{"apt", "list", "--installed"}, null, null, 120000,
                installedResult -> {
                    if (generation != refreshGeneration || isDestroyed()) return;
                    if (!installedResult.isSuccessful()) {
                        showRefreshError(installedResult);
                        return;
                    }
                    installedPackages = AptPackageParser.parseAptList(installedResult.stdout);
                    TermuxCommandRunner.run(this, new String[]{"apt", "list", "--upgradable"}, null, null, 120000,
                        upgradableResult -> {
                            if (generation != refreshGeneration || isDestroyed()) return;
                            if (!upgradableResult.isSuccessful()) {
                                showRefreshError(upgradableResult);
                                return;
                            }
                            upgradablePackages = AptPackageParser.parseAptList(upgradableResult.stdout);
                            swipeRefresh.setRefreshing(false);
                            statusView.setText(getString(R.string.feature_packages_counts, allPackages.size(),
                                installedPackages.size(), upgradablePackages.size()));
                            showCurrentList();
                        });
                });
        });
    }

    private void showRefreshError(@NonNull TermuxCommandRunner.Result result) {
        swipeRefresh.setRefreshing(false);
        String message = result.error != null ? result.error : result.stderr;
        if (message == null || message.trim().isEmpty()) message = result.stdout;
        statusView.setText(getString(R.string.feature_packages_error, message == null ? "apt" : message.trim()));
    }

    private void showCurrentList() {
        if (!currentQuery.trim().isEmpty()) return;
        List<PackageInfo> source = mode == MODE_INSTALLED ? installedPackages :
            mode == MODE_UPGRADABLE ? upgradablePackages : allPackages;
        setDisplayedPackages(source);
        requestButton.setVisibility(View.GONE);
        if (source.isEmpty()) {
            emptyView.setText(mode == MODE_UPGRADABLE ? R.string.feature_packages_no_updates :
                mode == MODE_INSTALLED ? R.string.feature_packages_none_installed : R.string.feature_packages_none_found);
        }
    }

    private void searchPackages(@NonNull String query) {
        currentQuery = query.trim();
        final int generation = ++searchGeneration;
        if (currentQuery.isEmpty()) {
            showCurrentList();
            return;
        }
        statusView.setText(R.string.feature_packages_searching);
        TermuxCommandRunner.run(this, new String[]{"apt-cache", "search", currentQuery}, null, null, 60000, result -> {
            if (generation != searchGeneration || isDestroyed()) return;
            if (!result.isSuccessful()) {
                statusView.setText(getString(R.string.feature_packages_error,
                    result.error != null ? result.error : result.stderr));
                return;
            }
            List<PackageInfo> results = AptPackageParser.parseSearch(result.stdout);
            List<PackageInfo> merged = new ArrayList<>();
            Map<String, PackageInfo> installed = byName(installedPackages);
            Map<String, PackageInfo> upgrades = byName(upgradablePackages);
            for (PackageInfo item : results) {
                PackageInfo available = allByName.get(item.name);
                String version = available != null ? available.version : null;
                String architecture = available != null ? available.architecture : null;
                merged.add(new PackageInfo(item.name, version, architecture, item.description,
                    installed.containsKey(item.name), upgrades.containsKey(item.name)));
            }
            setDisplayedPackages(merged);
            statusView.setText(getString(R.string.feature_packages_search_results, merged.size(), currentQuery));
            boolean unavailable = merged.isEmpty();
            emptyView.setText(unavailable ? R.string.feature_packages_not_available : R.string.feature_packages_none_found);
            requestButton.setVisibility(unavailable ? View.VISIBLE : View.GONE);
        });
    }

    @NonNull
    private static Map<String, PackageInfo> byName(@NonNull List<PackageInfo> packages) {
        Map<String, PackageInfo> result = new HashMap<>();
        for (PackageInfo item : packages) result.put(item.name, item);
        return result;
    }

    private void setDisplayedPackages(@NonNull List<PackageInfo> packages) {
        displayedPackages.clear();
        displayedPackages.addAll(packages);
        adapter.notifyDataSetChanged();
    }

    private void showDetails(@NonNull PackageInfo item) {
        statusView.setText(getString(R.string.feature_packages_loading_details, item.name));
        TermuxCommandRunner.run(this, new String[]{"apt", "show", item.name}, null, null, 60000, result -> {
            if (isDestroyed()) return;
            statusView.setText("");
            if (!result.isSuccessful()) {
                statusView.setText(getString(R.string.feature_packages_error,
                    result.error != null ? result.error : result.stderr));
                return;
            }
            PackageDetails details = AptPackageParser.parseShow(result.stdout, item.name);
            ScrollView scrollView = new ScrollView(this);
            LinearLayout content = PackageUi.vertical(this);
            int padding = PackageUi.dp(content, 20);
            content.setPadding(padding, padding, padding, padding);
            scrollView.addView(content);
            content.addView(PackageUi.heading(this, details.name));
            addDetail(content, R.string.feature_packages_detail_version, details.get("Version"));
            addDetail(content, R.string.feature_packages_detail_installed_version, details.get("Installed-Version"));
            addDetail(content, R.string.feature_packages_detail_download_size, details.get("Size"));
            addDetail(content, R.string.feature_packages_detail_installed_size, details.get("Installed-Size"));
            addDetail(content, R.string.feature_packages_detail_dependencies, details.get("Depends"));
            addDetail(content, R.string.feature_packages_detail_maintainer, details.get("Maintainer"));
            addDetail(content, R.string.feature_packages_detail_description, details.get("Description"));
            addDetail(content, R.string.feature_packages_detail_homepage, details.get("Homepage"));

            boolean installed = item.installed || byName(installedPackages).containsKey(item.name);
            boolean upgradable = item.upgradable || byName(upgradablePackages).containsKey(item.name);
            int actionLabel = upgradable ? R.string.feature_packages_action_update :
                installed ? R.string.feature_packages_action_reinstall : R.string.feature_packages_action_install;
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(scrollView)
                .setPositiveButton(actionLabel, (dialog, which) -> runPackageCommand("pkg install -y " +
                    TermuxCommandRunner.shellQuote(item.name), "Install " + item.name))
                .setNegativeButton(android.R.string.cancel, null);
            if (installed) {
                builder.setNeutralButton(R.string.feature_packages_action_uninstall, (dialog, which) ->
                    confirmUninstall(item.name));
            }
            builder.show();
        });
    }

    private static void addDetail(@NonNull LinearLayout content, int label, @Nullable String value) {
        if (value == null || value.trim().isEmpty()) return;
        TextView title = PackageUi.text(content.getContext(), content.getContext().getString(label), 13);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);
        content.addView(PackageUi.text(content.getContext(), value, 15));
    }

    private void confirmUninstall(@NonNull String name) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_packages_action_uninstall)
            .setMessage(getString(R.string.feature_packages_uninstall_confirmation, name))
            .setPositiveButton(R.string.feature_packages_action_uninstall, (dialog, which) ->
                runPackageCommand("pkg uninstall -y " + TermuxCommandRunner.shellQuote(name), "Remove " + name))
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmUpdateAll() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_packages_update_all)
            .setMessage(R.string.feature_packages_update_all_confirmation)
            .setPositiveButton(R.string.feature_packages_update_all, (dialog, which) ->
                runPackageCommand("pkg upgrade", "Update all packages"))
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void runPackageCommand(@NonNull String command, @NonNull String name) {
        needsRefresh = true;
        PackageTerminalLauncher.run(this, name, command);
    }

    private void requestPackage(@NonNull String name) {
        String url = "https://github.com/riyadmondol2006/termux-app-RE/issues/new?template=package-request.yml&package=" +
            Uri.encode(name.trim());
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.feature_packages_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private final class PackageAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return displayedPackages.size();
        }

        @Override
        public PackageInfo getItem(int position) {
            return displayedPackages.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) row = (LinearLayout) convertView;
            else {
                row = PackageUi.vertical(PackageManagerActivity.this);
                int padding = PackageUi.dp(row, 16);
                row.setPadding(padding, PackageUi.dp(row, 10), padding, PackageUi.dp(row, 10));
                row.setMinimumHeight(PackageUi.dp(row, 72));
            }
            row.removeAllViews();
            PackageInfo item = getItem(position);
            TextView name = PackageUi.text(PackageManagerActivity.this, item.name, 17);
            name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            row.addView(name);
            if (item.version != null) {
                String state = item.upgradable ? getString(R.string.feature_packages_state_update) :
                    item.installed ? getString(R.string.feature_packages_state_installed) : "";
                row.addView(PackageUi.text(PackageManagerActivity.this, item.version +
                    (item.architecture == null ? "" : " · " + item.architecture) +
                    (state.isEmpty() ? "" : " · " + state), 13));
            }
            if (item.description != null && !item.description.isEmpty()) {
                TextView description = PackageUi.text(PackageManagerActivity.this, item.description, 14);
                description.setMaxLines(2);
                row.addView(description);
            }
            return row;
        }

    }

}
