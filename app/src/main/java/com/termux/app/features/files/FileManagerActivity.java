package com.termux.app.features.files;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.theme.NightMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The file manager. Tap opens a folder or file, long press selects, and the toolbar then shows the
 * actions for the selection. What happens to files is in {@link FileActions}.
 */
public final class FileManagerActivity extends AppCompatActivity implements FileAdapter.Listener {
    private static final String STATE_DIRECTORY = "directory", STATE_SORT = "sort";
    private static final int REQUEST_STORAGE = 741;
    /** Folders with more entries than this are listed without counting what is inside each folder. */
    private static final int MAX_COUNTED = 1500;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private static final String HOME = TermuxConstants.TERMUX_HOME_DIR_PATH;
    private static final String PREFIX = TermuxConstants.TERMUX_PREFIX_DIR_PATH;
    private static final String STORAGE_LINKS = HOME + "/storage";

    private File directory = new File(HOME);
    private FileListing.SortOrder sort = FileListing.SortOrder.NAME;
    private String query = "";
    private boolean restoringSearch, waitingForStorage, pendingShared, unreadable;
    private int generation;
    /** All entries of the folder, sorted, before the search filter is applied. */
    private List<FileEntry> all = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();

    private FileActions actions;
    private FileAdapter adapter;
    private RecyclerView list;
    private View empty;
    private ImageView emptyIcon;
    private TextView emptyTitle, emptyBody;
    private MaterialButton emptyAction;
    private LinearLayout path;
    private HorizontalScrollView pathScroll;
    private LinearProgressIndicator loading;
    private FloatingActionButton newButton;
    private MenuItem searchItem;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_files_manager);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.feature_files_manager);

        list = findViewById(R.id.feature_files_list);
        empty = findViewById(R.id.feature_files_empty);
        emptyIcon = findViewById(R.id.feature_files_empty_icon);
        emptyTitle = findViewById(R.id.feature_files_empty_title);
        emptyBody = findViewById(R.id.feature_files_empty_body);
        emptyAction = findViewById(R.id.feature_files_empty_action);
        path = findViewById(R.id.feature_files_path);
        pathScroll = findViewById(R.id.feature_files_path_scroll);
        loading = findViewById(R.id.feature_files_loading);
        newButton = findViewById(R.id.feature_files_new);

        actions = new FileActions(this, () -> { refresh(); invalidateOptionsMenu(); });
        adapter = new FileAdapter(this, selected);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        newButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle(R.string.feature_files_new)
            .setItems(R.array.feature_files_new_menu, (d, i) -> actions.create(directory, i == 1)).show());

        if (state != null) {
            String saved = state.getString(STATE_DIRECTORY);
            if (saved != null) directory = new File(saved);
            sort = FileListing.SortOrder.values()[state.getInt(STATE_SORT, 0)];
        }
        refresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putString(STATE_DIRECTORY, directory.getPath());
        state.putInt(STATE_SORT, sort.ordinal());
    }

    // ---- Navigation ----

    private void navigate(File target) {
        directory = target;
        selected.clear();
        clearSearch();
        refresh();
        updateMode();
    }

    private void clearSearch() {
        query = "";
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            restoringSearch = true;
            searchItem.collapseActionView();
            restoringSearch = false;
        }
    }

    private void up() {
        File parent = directory.getParentFile();
        if (parent != null) navigate(parent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (!selected.isEmpty()) clearSelection(); else onBackPressed();
        return true;
    }

    /** Back closes the search, then the selection, then goes up a folder until the top of a location, then leaves. */
    @Override
    public void onBackPressed() {
        if (searchItem != null && searchItem.isActionViewExpanded()) { searchItem.collapseActionView(); return; }
        if (!selected.isEmpty()) { clearSelection(); return; }
        if (directory.getParentFile() != null && !Breadcrumbs.isTop(directory.getPath(),
            Arrays.asList(HOME, PREFIX, STORAGE_LINKS, Environment.getExternalStorageDirectory().getAbsolutePath()))) { up(); return; }
        super.onBackPressed();
    }

    private void openLocation(int index) {
        if (index == 3 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShared = true;
            requestStorage();
            actions.message(getString(R.string.feature_files_grant_storage));
            return;
        }
        navigate(new File(index == 0 ? HOME : index == 1 ? PREFIX : index == 2 ? STORAGE_LINKS :
            Environment.getExternalStorageDirectory().getAbsolutePath()));
    }

    private void requestStorage() {
        waitingForStorage = true;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != REQUEST_STORAGE || !waitingForStorage) return;
        waitingForStorage = false;
        boolean shared = pendingShared;
        pendingShared = false;
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Go to shared storage if that is what was asked for, otherwise read the folder again.
            if (shared) openLocation(3); else refresh();
        }
    }

    // ---- Listing ----

    private void refresh() {
        int request = ++generation;
        File folder = directory;
        loading.setVisibility(View.VISIBLE);
        updatePath();
        IO.execute(() -> {
            File[] files = folder.listFiles();
            List<FileEntry> found = new ArrayList<>();
            if (files != null) for (File f : files) {
                boolean dir = f.isDirectory();
                int children = FileEntry.UNKNOWN;
                if (dir && files.length <= MAX_COUNTED) {
                    String[] names = f.list();
                    if (names != null) children = names.length;
                }
                found.add(new FileEntry(f, dir, FileUtils.symlinkFileExists(f.getPath()), dir ? 0 : f.length(), f.lastModified(), children));
            }
            FileListing.sort(found, sort);
            boolean readable = files != null;
            runOnUiThread(() -> {
                if (request != generation || isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.INVISIBLE);
                all = found;
                unreadable = !readable;
                // Keep only the selected items that are still there.
                Set<String> present = new LinkedHashSet<>();
                for (FileEntry e : found) present.add(e.path());
                if (selected.retainAll(present)) updateMode();
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        adapter.submit(FileListing.filter(all, query));
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean none = adapter.getItemCount() == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none ? View.INVISIBLE : View.VISIBLE);
        if (!none) return;
        emptyAction.setVisibility(View.GONE);
        if (unreadable) {
            emptyIcon.setImageResource(R.drawable.feature_files_ic_lock);
            emptyTitle.setText(R.string.feature_files_denied_title);
            emptyBody.setText(R.string.feature_files_denied_body);
            boolean storage = directory.getPath().startsWith(Environment.getExternalStorageDirectory().getAbsolutePath())
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (storage) {
                emptyAction.setText(R.string.feature_files_allow_storage);
                emptyAction.setOnClickListener(v -> requestStorage());
                emptyAction.setVisibility(View.VISIBLE);
            } else if (directory.getParentFile() != null) {
                emptyAction.setText(R.string.feature_files_go_up);
                emptyAction.setOnClickListener(v -> up());
                emptyAction.setVisibility(View.VISIBLE);
            }
        } else if (!query.trim().isEmpty() && !all.isEmpty()) {
            emptyIcon.setImageResource(R.drawable.feature_files_ic_search);
            emptyTitle.setText(R.string.feature_files_no_matches_title);
            emptyBody.setText(getString(R.string.feature_files_no_matches_body, query.trim()));
        } else {
            emptyIcon.setImageResource(R.drawable.feature_files_ic_folder_open);
            emptyTitle.setText(R.string.feature_files_empty_title);
            emptyBody.setText(R.string.feature_files_empty_body);
        }
    }

    private void updatePath() {
        path.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<Breadcrumbs.Crumb> crumbs = Breadcrumbs.parse(directory.getPath(), HOME, PREFIX);
        for (int i = 0; i < crumbs.size(); i++) {
            Breadcrumbs.Crumb crumb = crumbs.get(i);
            if (i > 0) path.addView(inflater.inflate(R.layout.feature_files_item_crumb_separator, path, false));
            Chip chip = (Chip) inflater.inflate(R.layout.feature_files_item_crumb, path, false);
            chip.setText(crumb.label);
            if (i == crumbs.size() - 1) {
                chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(
                    MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSecondaryContainer)));
            } else {
                chip.setOnClickListener(v -> navigate(new File(crumb.path)));
            }
            path.addView(chip);
        }
        pathScroll.post(() -> pathScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ---- Taps and selection ----

    @Override
    public void onClick(FileEntry entry) {
        if (!selected.isEmpty()) toggle(entry);
        else if (entry.directory) navigate(entry.file);
        else actions.fileMenu(entry.file);
    }

    @Override
    public void onLongClick(FileEntry entry) { toggle(entry); }

    private void toggle(FileEntry entry) {
        if (!selected.remove(entry.path())) selected.add(entry.path());
        adapter.selectionChanged(entry);
        updateMode();
    }

    private void selectAll() {
        for (FileEntry e : adapter.entries()) selected.add(e.path());
        adapter.allSelectionChanged();
        updateMode();
    }

    private void clearSelection() {
        selected.clear();
        adapter.allSelectionChanged();
        updateMode();
    }

    private List<File> selectedFiles() {
        List<File> result = new ArrayList<>();
        for (FileEntry e : all) if (selected.contains(e.path())) result.add(e.file);
        return result;
    }

    /** Switches the toolbar between browsing and the actions for the selected items. */
    private void updateMode() {
        boolean selecting = !selected.isEmpty();
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(selecting ? getString(R.string.feature_files_selected, selected.size()) : getString(R.string.feature_files_manager));
            bar.setHomeAsUpIndicator(selecting ? R.drawable.feature_files_ic_close : 0);
            if (!selecting) bar.setDisplayHomeAsUpEnabled(true);
        }
        if (selecting) newButton.hide(); else newButton.show();
        invalidateOptionsMenu();
    }

    // ---- Menus ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(selected.isEmpty() ? R.menu.feature_files_menu_browse : R.menu.feature_files_menu_selection, menu);
        if (!selected.isEmpty()) { searchItem = null; prepareSelection(menu); return true; }
        searchItem = menu.findItem(R.id.feature_files_action_search);
        SearchView search = (SearchView) searchItem.getActionView();
        search.setQueryHint(getString(R.string.feature_files_search));
        search.setMaxWidth(Integer.MAX_VALUE);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextSubmit(String text) { search.clearFocus(); return true; }
            public boolean onQueryTextChange(String text) {
                if (!restoringSearch) { query = text; applyFilter(); }
                return true;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            public boolean onMenuItemActionExpand(MenuItem item) { return true; }
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (!restoringSearch && !query.isEmpty()) { query = ""; applyFilter(); }
                return true;
            }
        });
        // The menu is built again when the selection or the clipboard changes, so show the search again.
        if (!query.isEmpty()) {
            restoringSearch = true;
            searchItem.expandActionView();
            search.setQuery(query, false);
            restoringSearch = false;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (selected.isEmpty()) {
            menu.findItem(R.id.feature_files_action_up).setVisible(directory.getParentFile() != null);
            MenuItem paste = menu.findItem(R.id.feature_files_action_paste);
            paste.setVisible(actions.clipboardSize() > 0);
            menu.findItem(sort == FileListing.SortOrder.NAME ? R.id.feature_files_sort_name :
                sort == FileListing.SortOrder.NEWEST ? R.id.feature_files_sort_newest : R.id.feature_files_sort_largest).setChecked(true);
        } else {
            prepareSelection(menu);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void prepareSelection(Menu menu) {
        List<File> chosen = selectedFiles();
        boolean one = chosen.size() == 1;
        boolean file = one && !chosen.get(0).isDirectory();
        menu.findItem(R.id.feature_files_action_open).setVisible(one);
        menu.findItem(R.id.feature_files_action_rename).setVisible(one);
        menu.findItem(R.id.feature_files_action_details).setVisible(one);
        menu.findItem(R.id.feature_files_action_edit).setVisible(file);
        menu.findItem(R.id.feature_files_action_share).setVisible(file);
        menu.findItem(R.id.feature_files_action_open_with).setVisible(file);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        List<File> chosen = selectedFiles();
        if (id == R.id.feature_files_action_up) up();
        else if (id == R.id.feature_files_location_home) openLocation(0);
        else if (id == R.id.feature_files_location_prefix) openLocation(1);
        else if (id == R.id.feature_files_location_storage_links) openLocation(2);
        else if (id == R.id.feature_files_location_shared) openLocation(3);
        else if (id == R.id.feature_files_sort_name) setSort(FileListing.SortOrder.NAME);
        else if (id == R.id.feature_files_sort_newest) setSort(FileListing.SortOrder.NEWEST);
        else if (id == R.id.feature_files_sort_largest) setSort(FileListing.SortOrder.LARGEST);
        else if (id == R.id.feature_files_action_select_all) selectAll();
        else if (id == R.id.feature_files_action_paste) {
            if (actions.clipboardSize() == 0) actions.message(getString(R.string.feature_files_nothing_to_paste));
            else actions.paste(directory);
        } else if (id == R.id.feature_files_action_terminal) {
            if (FileManagerFeature.openTerminal(directory)) finish();
            else actions.message(getString(R.string.feature_files_terminal_failed));
        } else if (id == R.id.feature_files_action_copy || id == R.id.feature_files_action_move) {
            actions.toClipboard(chosen, id == R.id.feature_files_action_move);
            clearSelection();
            Snackbar.make(list, getResources().getQuantityString(R.plurals.feature_files_ready_to_paste,
                actions.clipboardSize(), actions.clipboardSize()), Snackbar.LENGTH_LONG).show();
        } else if (id == R.id.feature_files_action_delete) {
            actions.delete(chosen);
        } else if (chosen.size() == 1) {
            File file = chosen.get(0);
            if (id == R.id.feature_files_action_open) { if (file.isDirectory()) navigate(file); else actions.fileMenu(file); }
            else if (id == R.id.feature_files_action_edit) actions.edit(file);
            else if (id == R.id.feature_files_action_rename) actions.rename(file);
            else if (id == R.id.feature_files_action_details) actions.details(file);
            else if (id == R.id.feature_files_action_share) actions.share(file);
            else if (id == R.id.feature_files_action_open_with) actions.openWith(file);
            else return super.onOptionsItemSelected(item);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void setSort(FileListing.SortOrder order) {
        sort = order;
        FileListing.sort(all, sort);
        applyFilter();
        invalidateOptionsMenu();
    }
}
