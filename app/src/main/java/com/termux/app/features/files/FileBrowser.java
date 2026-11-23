package com.termux.app.features.files;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxOpenReceiver;
import com.termux.app.features.TermuxSessionLauncher;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.TermuxConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A browser owned by the terminal activity, so opening a terminal needs no core hooks. */
final class FileBrowser {
    private final TermuxActivity activity;
    private File directory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
    private final List<File> entries = new ArrayList<>(), clipboard = new ArrayList<>();
    private ListView list;
    private TextView path;
    private EditText search;
    private boolean move;
    private int sort;
    private Dialog dialog;
    private int generation;
    private static final java.util.concurrent.ExecutorService IO = Executors.newSingleThreadExecutor();

    FileBrowser(TermuxActivity activity) { this.activity = activity; }

    void show() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        path = new TextView(activity);
        body.addView(path);
        search = new EditText(activity);
        search.setHint("Search this folder");
        search.setSingleLine();
        body.addView(search);
        LinearLayout actions = new LinearLayout(activity);
        button(actions, "Up", () -> { if (directory.getParentFile() != null) { directory = directory.getParentFile(); refresh(); } });
        button(actions, "Locations", this::locations);
        button(actions, "Actions", this::actions);
        body.addView(actions);
        list = new ListView(activity);
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemLongClickListener((p, v, position, id) -> {
            File f = entries.get(position);
            if (f.isDirectory()) { directory = f; refresh(); } else fileMenu(f);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            public void afterTextChanged(Editable e) {}
        });
        dialog = new MaterialAlertDialogBuilder(activity).setTitle("File manager")
            .setView(body).setPositiveButton("Close", null).create();
        dialog.show();
        dialog.getWindow().setLayout(-1, (int) (activity.getResources().getDisplayMetrics().heightPixels * .88));
        refresh();
    }

    private void button(LinearLayout row, String text, Runnable run) {
        Button b = new Button(activity); b.setText(text); b.setOnClickListener(v -> run.run());
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void locations() {
        String[] labels = {"Home", "Installed packages ($PREFIX)", "Storage links", "Shared storage"};
        new MaterialAlertDialogBuilder(activity).setTitle("Locations").setItems(labels, (d, i) -> {
            if (i == 3 && activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 741);
                message("Grant storage permission, then choose Shared storage again."); return;
            }
            directory = new File(i == 0 ? TermuxConstants.TERMUX_HOME_DIR_PATH :
                i == 1 ? TermuxConstants.TERMUX_PREFIX_DIR_PATH :
                i == 2 ? TermuxConstants.TERMUX_HOME_DIR_PATH + "/storage" :
                    Environment.getExternalStorageDirectory().getAbsolutePath());
            refresh();
        }).show();
    }

    private void refresh() {
        int request = ++generation;
        File folder = directory;
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        path.setText(folder + "\nTap to select · hold to open");
        IO.execute(() -> {
            File[] files = folder.listFiles();
            List<File> found = new ArrayList<>();
            if (files != null) for (File f : files)
                if (f.getName().toLowerCase(Locale.ROOT).contains(query)) found.add(f);
            Comparator<File> order = sort == 1 ? Comparator.comparingLong(File::lastModified).reversed() :
                sort == 2 ? Comparator.comparingLong(File::length).reversed() :
                    Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER);
            found.sort(order);
            activity.runOnUiThread(() -> {
                if (request != generation || !dialog.isShowing()) return;
                entries.clear(); entries.addAll(found);
                String[] labels = new String[found.size()];
                for (int i = 0; i < labels.length; i++) {
                    File f = found.get(i);
                    labels[i] = (FileUtils.symlinkFileExists(f.getPath()) ? "↗ " : f.isDirectory() ? "▸ " : "") + f.getName();
                }
                list.clearChoices();
                list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_multiple_choice, labels));
                if (files == null) message("Cannot read this folder.");
            });
        });
    }

    private List<File> selected() {
        List<File> result = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (list.isItemChecked(i)) result.add(entries.get(i));
        return result;
    }

    private void actions() {
        String[] labels = {"Open selected", "Select all", "Copy selected", "Move selected", "Paste here",
            "Delete selected", "New file", "New folder", "Sort", "Open terminal here", "Details / rename selected"};
        new MaterialAlertDialogBuilder(activity).setTitle("Actions").setItems(labels, (d, which) -> {
            List<File> chosen = selected();
            switch (which) {
                case 0:
                    if (chosen.size() != 1) { message("Select one item."); break; }
                    File f = chosen.get(0);
                    if (f.isDirectory()) { directory = f; refresh(); } else fileMenu(f);
                    break;
                case 1: for (int i = 0; i < entries.size(); i++) list.setItemChecked(i, true); break;
                case 2: case 3:
                    clipboard.clear(); clipboard.addAll(chosen); move = which == 3;
                    message(clipboard.size() + " items ready. Browse to a destination and choose Paste here."); break;
                case 4:
                    List<File> sources = new ArrayList<>(clipboard);
                    File destination = directory;
                    List<File> mutations = new ArrayList<>(sources); mutations.add(destination);
                    warn(mutations, () -> task(cancel -> {
                        for (File source : sources) {
                            check(cancel);
                            File target = new File(destination, source.getName());
                            FileRules.destination(source, target);
                            if (FileUtils.fileExists(target.getPath(), false)) throw new IOException("Already exists: " + target);
                            copy(source, target, cancel);
                            if (move) remove(source, cancel);
                        }
                    })); break;
                case 5:
                    new MaterialAlertDialogBuilder(activity).setTitle("Delete " + chosen.size() + " items?")
                        .setMessage("This cannot be undone. Symbolic links are deleted without deleting their targets.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Delete", (x, y) ->
                            warn(chosen, () -> task(cancel -> { for (File item : chosen) remove(item, cancel); }))).show(); break;
                case 6: case 7:
                    File parent = directory;
                    input(which == 6 ? "New file" : "New folder", "", name -> warn(Collections.singletonList(parent),
                        () -> task(cancel -> {
                            File target = new File(parent, FileRules.name(name));
                            if (which == 6 ? !target.createNewFile() : !target.mkdir()) throw new IOException("Cannot create " + target);
                        }))); break;
                case 8:
                    new MaterialAlertDialogBuilder(activity).setTitle("Sort").setItems(new String[]{"Name", "Newest first", "Largest first"},
                        (x, y) -> { sort = y; refresh(); }).show(); break;
                case 10:
                    if (chosen.size() == 1) fileMenu(chosen.get(0));
                    else message("Select one item.");
                    break;
                case 9:
                    if (TermuxSessionLauncher.runInNewSession(activity, directory.getName(), ":", true, directory.getPath()) != null) dialog.dismiss();
                    break;
            }
        }).show();
    }

    private void fileMenu(File file) {
        new MaterialAlertDialogBuilder(activity).setTitle(file.getName())
            .setItems(new String[]{"View / edit text", "Details", "Rename", "Share", "Open with"}, (d, i) -> {
                if (i == 0) edit(file);
                if (i == 1) task(cancel -> {
                    StructStat stat = Os.lstat(file.getPath());
                    String details = file + "\nSize: " + stat.st_size + " bytes\nMode: " + Integer.toOctalString(stat.st_mode & 07777)
                        + "\nOwner: " + stat.st_uid + ":" + stat.st_gid + "\nModified: " + new Date(stat.st_mtime * 1000)
                        + (OsConstants.S_ISLNK(stat.st_mode) ? "\nTarget: " + Os.readlink(file.getPath()) : "");
                    activity.runOnUiThread(() -> message(details));
                });
                if (i == 2) input("Rename", file.getName(), name -> warn(Collections.singletonList(file), () -> task(cancel -> {
                    File target = new File(file.getParentFile(), FileRules.name(name));
                    if (FileUtils.fileExists(target.getPath(), false) || !file.renameTo(target)) throw new IOException("Cannot rename file");
                })));
                if (i >= 3) {
                    Intent intent = new Intent(i == 3 ? Intent.ACTION_SEND : Intent.ACTION_VIEW, Uri.fromFile(file));
                    intent.putExtra("chooser", true);
                    new TermuxOpenReceiver().onReceive(activity, intent);
                }
            }).show();
    }

    private void edit(File file) {
        task(cancel -> {
            if (!OsConstants.S_ISREG(Os.lstat(file.getPath()).st_mode) || file.length() > 256 * 1024)
                throw new IOException("Editor accepts regular UTF-8 text files up to 256 KiB.");
            byte[] data;
            try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 256 * 1024) throw new IOException("File grew beyond editor limit");
                    out.write(buffer, 0, n);
                }
                data = out.toByteArray();
            }
            String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();
            if (text.indexOf('\0') >= 0) throw new IOException("Binary file");
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                EditText editor = new EditText(activity); editor.setText(text); editor.setGravity(48);
                new MaterialAlertDialogBuilder(activity).setTitle(file.getName()).setView(editor)
                    .setNegativeButton("Close", null).setPositiveButton("Save", (d, w) ->
                        warn(Collections.singletonList(file), () -> task(c -> {
                            // Detect concurrent edits and avoid following a newly substituted symlink.
                            if (!OsConstants.S_ISREG(Os.lstat(file.getPath()).st_mode)) throw new IOException("File type changed");
                            byte[] current;
                            try (FileInputStream in = new FileInputStream(file)) { current = new byte[data.length + 1]; int size = 0, n;
                                while (size < current.length && (n = in.read(current, size, current.length - size)) > 0) size += n;
                                if (!Arrays.equals(data, Arrays.copyOf(current, size))) throw new IOException("File changed; reopen before saving");
                            }
                            try (FileOutputStream out = new FileOutputStream(Os.open(file.getPath(), OsConstants.O_WRONLY | OsConstants.O_TRUNC | OsConstants.O_NOFOLLOW, 0))) { out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8)); }
                        }))).show();
            });
        });
    }

    private interface Work { void run(AtomicBoolean cancel) throws Exception; }
    private interface TextAction { void run(String value); }
    private void input(String title, String initial, TextAction action) {
        EditText text = new EditText(activity); text.setSingleLine(); text.setText(initial);
        new MaterialAlertDialogBuilder(activity).setTitle(title).setView(text).setNegativeButton("Cancel", null)
            .setPositiveButton("OK", (d, w) -> action.run(text.getText().toString())).show();
    }
    private void warn(List<File> files, Runnable action) {
        try {
            String prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH).getCanonicalPath();
            for (File f : files) if (f.getCanonicalPath().equals(prefix) ||
                f.getCanonicalPath().startsWith(prefix + "/")) {
                new MaterialAlertDialogBuilder(activity).setTitle("Modify installed packages?")
                    .setMessage("Changing $PREFIX can break Termux or installed packages.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Continue", (d, w) -> action.run()).show(); return;
            }
            action.run();
        } catch (IOException e) { message(e.getMessage()); }
    }
    private void message(String text) { if (!activity.isFinishing() && !activity.isDestroyed()) new MaterialAlertDialogBuilder(activity).setMessage(text).setPositiveButton("OK", null).show(); }
    private void task(Work work) {
        AtomicBoolean cancel = new AtomicBoolean();
        Dialog progress = new MaterialAlertDialogBuilder(activity).setTitle("Working…").setView(new ProgressBar(activity))
            .setMessage("Operations may be partially completed if cancelled.")
            .setNegativeButton("Cancel", (d, w) -> cancel.set(true)).setCancelable(false).show();
        new Thread(() -> {
            String error = null;
            try { work.run(cancel); } catch (Exception e) { error = e.getMessage(); }
            String result = error;
            activity.runOnUiThread(() -> { progress.dismiss(); refresh(); if (result != null) message(result); });
        }, "files-operation").start();
    }
    private static void check(AtomicBoolean cancel) throws IOException { if (cancel.get()) throw new IOException("Cancelled"); }
    private static void remove(File file, AtomicBoolean cancel) throws Exception {
        check(cancel);
        if (FileUtils.directoryFileExists(file.getPath(), false)) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list " + file);
            for (File child : children) remove(child, cancel);
        }
        com.termux.shared.errors.Error error = FileUtils.deleteFile("file manager", file.getPath(), false);
        if (error != null) throw new IOException(error.toString());
    }
    private static void copy(File source, File target, AtomicBoolean cancel) throws Exception {
        check(cancel);
        if (FileUtils.directoryFileExists(source.getPath(), false)) {
            if (!target.mkdir()) throw new IOException("Cannot create " + target);
            File[] children = source.listFiles();
            if (children == null) throw new IOException("Cannot list " + source);
            for (File child : children) copy(child, new File(target, child.getName()), cancel);
            Os.chmod(target.getPath(), Os.lstat(source.getPath()).st_mode & 07777);
        } else if (FileUtils.regularFileExists(source.getPath(), false)) {
            try (InputStream in = new FileInputStream(Os.open(source.getPath(), OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW, 0));
                 OutputStream out = new FileOutputStream(Os.open(target.getPath(), OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL, 0600))) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) { check(cancel); out.write(buffer, 0, n); }
                Os.chmod(target.getPath(), Os.lstat(source.getPath()).st_mode & 07777);
                target.setLastModified(source.lastModified());
            }
        } else {
            com.termux.shared.errors.Error error = FileUtils.copyFile("file manager", source.getPath(), target.getPath(), false);
            if (error != null) throw new IOException(error.toString());
        }
    }
}
