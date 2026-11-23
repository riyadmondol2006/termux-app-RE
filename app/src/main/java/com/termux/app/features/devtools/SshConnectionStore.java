package com.termux.app.features.devtools;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores SSH connections in private SharedPreferences and optionally syncs them as Host blocks
 * inside a clearly marked managed block in {@code ~/.ssh/config}, leaving manual edits outside
 * that block untouched.
 */
public final class SshConnectionStore {

    /** Preferences file for the devtools area. */
    public static final String PREFS_NAME = "termux_re_devtools";
    private static final String KEY_CONNECTIONS = "ssh_connections";
    private static final String KEY_GIT_RECENT = "git_recent_repos";
    private static final int MAX_RECENT_REPOS = 10;

    private SshConnectionStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Load all saved connections, in saved order. Never returns null. */
    @NonNull
    public static List<SshConnection> load(@NonNull Context context) {
        String json = prefs(context).getString(KEY_CONNECTIONS, "[]");
        List<SshConnection> connections = SshConnection.listFromJson(json);
        return connections != null ? connections : new ArrayList<>();
    }

    /** Save connections and re-sync the managed block in {@code ~/.ssh/config}. */
    public static void save(@NonNull Context context, @NonNull List<SshConnection> connections) {
        prefs(context).edit().putString(KEY_CONNECTIONS, SshConnection.listToJson(connections)).apply();
        syncConfig(connections);
    }

    /** Add or replace by label (case-insensitive), then save. */
    public static void upsert(@NonNull Context context, @NonNull SshConnection connection) {
        List<SshConnection> connections = load(context);
        boolean replaced = false;
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).label.equalsIgnoreCase(connection.label)) {
                connections.set(i, connection);
                replaced = true;
                break;
            }
        }
        if (!replaced) connections.add(connection);
        save(context, connections);
    }

    /** Delete the connection with this label, then save. */
    public static void delete(@NonNull Context context, @NonNull String label) {
        List<SshConnection> connections = load(context);
        for (int i = connections.size() - 1; i >= 0; i--) {
            if (connections.get(i).label.equalsIgnoreCase(label)) connections.remove(i);
        }
        save(context, connections);
    }

    /** The user's ssh config file. */
    @NonNull
    public static File configFile() {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.ssh/config");
    }

    /** Read {@code ~/.ssh/config}, or null when it does not exist or cannot be read. */
    @Nullable
    public static String readConfigFile() {
        File file = configFile();
        if (!file.isFile() || !file.canRead()) return null;
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Rewrite {@code ~/.ssh/config} with the managed block synced. Returns false when the file
     * could not be written.
     */
    public static boolean syncConfig(@NonNull List<SshConnection> connections) {
        File file = configFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        String existing = readConfigFile();
        String updated = SshConfigCodec.spliceManagedBlock(existing == null ? "" : existing, connections);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(updated.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Recent repositories for the Git integration, most recent first. */
    @NonNull
    public static List<String> recentRepos(@NonNull Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_GIT_RECENT, null);
        if (stored == null) return new ArrayList<>();
        List<String> repos = new ArrayList<>(stored);
        Collections.sort(repos);
        return repos;
    }

    /** Remember a repository path, keeping at most {@link #MAX_RECENT_REPOS} entries. */
    public static void rememberRepo(@NonNull Context context, @NonNull String repo) {
        Set<String> stored = prefs(context).getStringSet(KEY_GIT_RECENT, null);
        LinkedHashSet<String> repos = stored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stored);
        repos.remove(repo);
        repos.add(repo);
        while (repos.size() > MAX_RECENT_REPOS) {
            repos.remove(repos.iterator().next());
        }
        prefs(context).edit().putStringSet(KEY_GIT_RECENT, repos).apply();
    }
}
