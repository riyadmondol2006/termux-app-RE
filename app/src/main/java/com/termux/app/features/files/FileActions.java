package com.termux.app.features.files;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.termux.R;
import com.termux.app.TermuxOpenReceiver;
import com.termux.shared.file.FileUtils;
import com.termux.shared.termux.TermuxConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The things the file manager does to files: copy, move, delete, create, rename, edit, share and show
 * details. It owns the confirmation dialogs and the safety checks. The screen only decides which files
 * and when, see {@link FileManagerActivity}.
 */
final class FileActions {
    private final AppCompatActivity activity;
    /** Called on the UI thread after something may have changed on disk. */
    private final Runnable changed;
    private final List<File> clipboard = new ArrayList<>();
    private boolean move;

    FileActions(@NonNull AppCompatActivity activity, @NonNull Runnable changed) {
        this.activity = activity;
        this.changed = changed;
    }

    int clipboardSize() { return clipboard.size(); }

    /** Remember {@code files} so {@link #paste} can copy or move them. */
    void toClipboard(List<File> files, boolean move) {
        clipboard.clear();
        clipboard.addAll(files);
        this.move = move;
    }

    void paste(File destination) {
        List<File> sources = new ArrayList<>(clipboard);
        boolean moving = move;
        List<File> mutations = new ArrayList<>(sources);
        mutations.add(destination);
        warn(mutations, () -> task(cancel -> {
            for (File source : sources) {
                check(cancel);
                File target = new File(destination, source.getName());
                FileRules.destination(source, target);
                if (FileUtils.fileExists(target.getPath(), false)) throw new IOException("Already exists: " + target);
                copy(source, target, cancel);
                if (moving) remove(source, cancel);
            }
            // The items are in their new place, so pasting them again would fail.
            if (moving) activity.runOnUiThread(clipboard::clear);
        }));
    }

    void delete(List<File> files) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getResources().getQuantityString(R.plurals.feature_files_delete_title, files.size(), files.size()))
            .setMessage(R.string.feature_files_delete_message)
            .setNegativeButton(R.string.feature_files_cancel, null)
            .setPositiveButton(R.string.feature_files_delete, (x, y) ->
                warn(files, () -> task(cancel -> { for (File item : files) remove(item, cancel); }))).show();
    }

    void create(File parent, boolean folder) {
        input(activity.getString(folder ? R.string.feature_files_new_folder : R.string.feature_files_new_file),
            activity.getString(R.string.feature_files_name_hint), "", name -> warn(Collections.singletonList(parent),
                () -> task(cancel -> {
                    File target = new File(parent, FileRules.name(name));
                    if (folder ? !target.mkdir() : !target.createNewFile()) throw new IOException("Cannot create " + target);
                })));
    }

    void rename(File file) {
        input(activity.getString(R.string.feature_files_rename), activity.getString(R.string.feature_files_name_hint),
            file.getName(), name -> warn(Collections.singletonList(file), () -> task(cancel -> {
                File target = new File(file.getParentFile(), FileRules.name(name));
                if (FileUtils.fileExists(target.getPath(), false) || !file.renameTo(target)) throw new IOException("Cannot rename file");
            })));
    }

    void details(File file) {
        task(cancel -> {
            StructStat stat = Os.lstat(file.getPath());
            String text = file + "\nSize: " + stat.st_size + " bytes\nMode: " + Integer.toOctalString(stat.st_mode & 07777)
                + "\nOwner: " + stat.st_uid + ":" + stat.st_gid + "\nModified: " + new Date(stat.st_mtime * 1000)
                + (OsConstants.S_ISLNK(stat.st_mode) ? "\nTarget: " + Os.readlink(file.getPath()) : "");
            activity.runOnUiThread(() -> message(text));
        }, false);
    }

    void share(File file) { send(file, Intent.ACTION_SEND); }

    void openWith(File file) { send(file, Intent.ACTION_VIEW); }

    private void send(File file, String action) {
        Intent intent = new Intent(action, Uri.fromFile(file));
        intent.putExtra("chooser", true);
        new TermuxOpenReceiver().onReceive(activity, intent);
    }

    /** What happens when a file is tapped: the same short menu as before. */
    void fileMenu(File file) {
        new MaterialAlertDialogBuilder(activity).setTitle(file.getName())
            .setItems(R.array.feature_files_file_menu, (d, i) -> {
                if (i == 0) edit(file);
                if (i == 1) details(file);
                if (i == 2) rename(file);
                if (i == 3) share(file);
                if (i == 4) openWith(file);
            }).show();
    }

    void edit(File file) {
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
                View body = LayoutInflater.from(activity).inflate(R.layout.feature_files_dialog_editor, null);
                TextInputEditText editor = body.findViewById(R.id.feature_files_editor_text);
                editor.setText(text);
                new MaterialAlertDialogBuilder(activity).setTitle(file.getName()).setView(body)
                    .setNegativeButton(R.string.feature_files_close, null).setPositiveButton(R.string.feature_files_save, (d, w) ->
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
        }, false);
    }

    private interface Work { void run(AtomicBoolean cancel) throws Exception; }
    private interface TextAction { void run(String value); }

    private void input(String title, String hint, String initial, TextAction action) {
        View body = LayoutInflater.from(activity).inflate(R.layout.feature_files_dialog_input, null);
        TextInputLayout layout = body.findViewById(R.id.feature_files_input_layout);
        TextInputEditText text = body.findViewById(R.id.feature_files_input_text);
        layout.setHint(hint);
        text.setText(initial);
        text.setSelection(initial.length());
        new MaterialAlertDialogBuilder(activity).setTitle(title).setView(body).setNegativeButton(R.string.feature_files_cancel, null)
            .setPositiveButton(android.R.string.ok, (d, w) -> action.run(String.valueOf(text.getText()))).show();
    }

    /** Ask first when the files are in $PREFIX, because changing it can break Termux. */
    private void warn(List<File> files, Runnable action) {
        try {
            String prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH).getCanonicalPath();
            for (File f : files) if (f.getCanonicalPath().equals(prefix) ||
                f.getCanonicalPath().startsWith(prefix + "/")) {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.feature_files_prefix_warning_title)
                    .setMessage(R.string.feature_files_prefix_warning_message)
                    .setNegativeButton(R.string.feature_files_cancel, null)
                    .setPositiveButton(R.string.feature_files_continue, (d, w) -> action.run()).show(); return;
            }
            action.run();
        } catch (IOException e) { message(e.getMessage()); }
    }

    void message(String text) {
        if (!activity.isFinishing() && !activity.isDestroyed())
            new MaterialAlertDialogBuilder(activity).setMessage(text).setPositiveButton(android.R.string.ok, null).show();
    }

    private void task(Work work) { task(work, true); }

    /** Runs {@code work} on a background thread behind a cancelable progress dialog. */
    private void task(Work work, boolean refresh) {
        AtomicBoolean cancel = new AtomicBoolean();
        View body = LayoutInflater.from(activity).inflate(R.layout.feature_files_dialog_progress, null);
        Dialog progress = new MaterialAlertDialogBuilder(activity).setTitle(R.string.feature_files_working).setView(body)
            .setMessage(R.string.feature_files_working_message)
            .setNegativeButton(R.string.feature_files_cancel, (d, w) -> cancel.set(true)).setCancelable(false).show();
        new Thread(() -> {
            String error = null;
            try { work.run(cancel); } catch (Exception e) { error = e.getMessage() != null ? e.getMessage() : e.toString(); }
            String result = error;
            activity.runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (refresh) changed.run();
                if (result != null) message(result);
            });
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
