package com.termux.app.features.system.monitor;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.termux.TermuxConstants;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

/** Reads the storage values shown by {@link SystemMonitorActivity}. Walking directories is slow, so call it rarely. */
@WorkerThread
final class StorageSampler {

    static final class Snapshot {
        /** Free and total bytes of the partition of the app data, or -1. */
        long dataFree = -1, dataTotal = -1;
        /** Free and total bytes of the shared storage, or -1. */
        long sharedFree = -1, sharedTotal = -1;
        /** The disk use of $PREFIX, $HOME and the other files of the app, or -1 if it could not be read. */
        long prefixSize = -1, homeSize = -1, otherAppDataSize = -1;
    }

    private StorageSampler() {}

    @NonNull
    static Snapshot sample(@NonNull Context context) {
        Snapshot snapshot = new Snapshot();

        long[] data = getFilesystemSize(context.getFilesDir());
        snapshot.dataFree = data[0];
        snapshot.dataTotal = data[1];

        long[] shared = getFilesystemSize(Environment.getExternalStorageDirectory());
        snapshot.sharedFree = shared[0];
        snapshot.sharedTotal = shared[1];

        snapshot.prefixSize = getDiskUsage(new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH));
        snapshot.homeSize = getDiskUsage(new File(TermuxConstants.TERMUX_HOME_DIR_PATH));
        File dataDir = context.getApplicationInfo().dataDir != null ? new File(context.getApplicationInfo().dataDir) : null;
        if (dataDir != null) {
            long dataDirSize = getDiskUsage(dataDir);
            if (dataDirSize >= 0 && snapshot.prefixSize >= 0 && snapshot.homeSize >= 0)
                snapshot.otherAppDataSize = Math.max(0, dataDirSize - snapshot.prefixSize - snapshot.homeSize);
        }
        return snapshot;
    }

    /** Returns {free, total} bytes of the filesystem of {@code file}, or {-1, -1}. */
    @NonNull
    private static long[] getFilesystemSize(@Nullable File file) {
        if (file == null) return new long[]{-1, -1};
        try {
            StatFs statFs = new StatFs(file.getAbsolutePath());
            return new long[]{statFs.getAvailableBytes(), statFs.getTotalBytes()};
        } catch (IllegalArgumentException e) {
            return new long[]{-1, -1};
        }
    }

    /**
     * Get the disk use of a directory tree in bytes, like `du`, without following symbolic links.
     * Returns -1 if {@code root} does not exist.
     */
    static long getDiskUsage(@NonNull File root) {
        Deque<File> pending = new ArrayDeque<>();
        long total = 0;
        StructStat rootStat;
        try {
            rootStat = Os.lstat(root.getPath());
        } catch (ErrnoException e) {
            return -1;
        }
        total += rootStat.st_blocks * 512;
        if (!OsConstants.S_ISDIR(rootStat.st_mode)) return total;
        pending.add(root);

        while (!pending.isEmpty()) {
            File[] children = pending.poll().listFiles();
            if (children == null) continue;
            for (File child : children) {
                try {
                    StructStat stat = Os.lstat(child.getPath());
                    total += stat.st_blocks * 512;
                    if (OsConstants.S_ISDIR(stat.st_mode)) pending.add(child);
                } catch (ErrnoException e) {
                    // Removed while walking, or not accessible.
                }
            }
        }
        return total;
    }

}
