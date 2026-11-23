package com.termux.app.features.files;

import android.content.Intent;
import androidx.annotation.NonNull;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;
import com.termux.app.features.TermuxSessionLauncher;
import java.io.File;
import java.lang.ref.WeakReference;

public final class FileManagerFeature extends TermuxFeature {
    /** The terminal screen that opened the file manager, to start a terminal in a folder. Weak, so it can be freed. */
    private static WeakReference<TermuxActivity> sTerminal = new WeakReference<>(null);

    public String getId() { return "files_manager"; }
    public int getTitle() { return R.string.feature_files_manager; }
    public int getSummary() { return R.string.feature_files_manager_summary; }

    public void open(TermuxActivity activity) {
        sTerminal = new WeakReference<>(activity);
        activity.startActivity(new Intent(activity, FileManagerActivity.class));
    }

    /** Starts a terminal session in {@code directory}. Returns false when that is not possible. */
    static boolean openTerminal(@NonNull File directory) {
        TermuxActivity activity = sTerminal.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        return TermuxSessionLauncher.runInNewSession(activity, directory.getName(), ":", true, directory.getPath()) != null;
    }
}
