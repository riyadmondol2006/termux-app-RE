package com.termux.app.features.files;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Streaming gzip container. No Java serialization, shell interpolation or archive extraction command. */
final class BackupArchive {
    static final String MAGIC = "Termux RE backup";
    static final int VERSION = 1;
    interface Progress { void update(String path) throws IOException; }
    private final Map<String, File> roots;
    private final Progress progress;
    private long writtenBytes;
    private int writtenEntries;
    BackupArchive(Map<String, File> roots, Progress progress) { this.roots = roots; this.progress = progress; }

    void write(OutputStream stream, boolean home, boolean prefix, boolean caches, String appVersion) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(stream))) {
            out.writeUTF(MAGIC); out.writeInt(VERSION); out.writeUTF("com.termux.re"); out.writeUTF(appVersion);
            out.writeUTF("settings," + (home ? "home" : "home/.termux") + (prefix ? ",usr" : ""));
            for (Map.Entry<String, File> root : roots.entrySet()) {
                if (root.getKey().equals("usr") && !prefix) continue;
                File[] children = root.getValue().listFiles();
                if (children == null) continue;
                for (File file : children) {
                    if (root.getKey().equals("home") && !home && !file.getName().equals(".termux")) continue;
                    add(out, file, root.getKey() + "/" + file.getName(), caches);
                }
            }
            out.writeUTF(""); // Mandatory terminator distinguishes truncated archives.
        }
    }
    private void add(DataOutputStream out, File file, String path, boolean caches) throws Exception {
        if (FileRules.excluded(path, caches)) return;
        progress.update(path);
        StructStat stat = Os.lstat(file.getPath());
        int type = OsConstants.S_ISLNK(stat.st_mode) ? 2 : OsConstants.S_ISDIR(stat.st_mode) ? 1 :
            OsConstants.S_ISREG(stat.st_mode) ? 0 : -1;
        if (type < 0) return; // Sockets, devices and FIFOs are not portable backup data.
        if (type == 0) writtenBytes += stat.st_size;
        if (++writtenEntries > 100000 || writtenBytes < 0 || writtenBytes > 20L * 1024 * 1024 * 1024)
            throw new IOException("Backup exceeds the 100,000 entry or 20 GiB limit");
        out.writeUTF(FileRules.archivePath(path)); out.writeByte(type);
        out.writeInt(stat.st_mode & 07777); out.writeLong(stat.st_mtime);
        if (type == 2) out.writeUTF(Os.readlink(file.getPath()));
        if (type == 0) {
            out.writeLong(stat.st_size);
            try (InputStream in = new FileInputStream(file)) { transfer(in, out, stat.st_size, path); }
        }
        if (type == 1) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read " + file);
            for (File child : children) add(out, child, path + "/" + child.getName(), caches);
        }
    }

    String read(InputStream stream, boolean restore) throws Exception {
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<Runnable> directoryTimes = new ArrayList<>();
        long total = 0;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(stream))) {
            if (!MAGIC.equals(in.readUTF()) || in.readInt() != VERSION || !"com.termux.re".equals(in.readUTF()))
                throw new IOException("Not a supported Termux RE backup");
            String version = in.readUTF();
            in.readUTF(); // Declared selection; the review below is derived from validated entries.
            String path;
            while (!(path = in.readUTF()).isEmpty()) {
                FileRules.archivePath(path); progress.update(path);
                int type = in.readUnsignedByte(), mode = in.readInt(); long time = in.readLong();
                if (type > 2 || mode < 0 || mode > 07777 || time < 0 || seen.put(path, type) != null || seen.size() > 100000)
                    throw new IOException("Invalid or duplicate entry");
                String parent = path;
                while (parent.lastIndexOf('/') > 0) {
                    parent = parent.substring(0, parent.lastIndexOf('/'));
                    if (seen.containsKey(parent) && seen.get(parent) != 1) throw new IOException("Non-directory archive parent");
                }
                // Require parent directories before children, preventing later symlink substitution.
                if (path.indexOf('/') != path.lastIndexOf('/') &&
                    !Integer.valueOf(1).equals(seen.get(path.substring(0, path.lastIndexOf('/')))))
                    throw new IOException("Missing parent directory");
                File destination = restore ? safeDestination(path) : null;
                String link = type == 2 ? in.readUTF() : null;
                if (link != null && link.indexOf('\0') >= 0) throw new IOException("Invalid link");
                long length = type == 0 ? in.readLong() : 0;
                total += length;
                if (length < 0 || length > 20L * 1024 * 1024 * 1024 || total < 0 || total > 20L * 1024 * 1024 * 1024)
                    throw new IOException("Archive exceeds 20 GiB safety limit");
                if (!restore) { transfer(in, null, length, path); continue; }
                if (type == 1) {
                    if (!destination.exists() && !destination.mkdir()) throw new IOException("Cannot create " + destination);
                    if (!OsConstants.S_ISDIR(Os.lstat(destination.getPath()).st_mode)) throw new IOException("Directory conflict: " + destination);
                    File dir = destination; int permissions = mode; long modified = time;
                    directoryTimes.add(() -> { try { Os.chmod(dir.getPath(), permissions); dir.setLastModified(modified * 1000); } catch (Exception e) { throw new IllegalStateException(e); } });
                } else {
                    if (destination.isDirectory()) throw new IOException("File/directory conflict: " + destination);
                    File temp = File.createTempFile(".files-restore-", ".tmp", destination.getParentFile());
                    try {
                        if (type == 2) { if (!temp.delete()) throw new IOException("Cannot prepare link"); Os.symlink(link, temp.getPath()); }
                        else {
                            try (OutputStream out = new FileOutputStream(temp)) { transfer(in, out, length, path); }
                            Os.chmod(temp.getPath(), mode); temp.setLastModified(time * 1000);
                        }
                        Os.rename(temp.getPath(), destination.getPath());
                    } finally { temp.delete(); }
                }
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing archive data");
            Collections.reverse(directoryTimes);
            for (Runnable finish : directoryTimes) finish.run();
            Set<String> actualRoots = new TreeSet<>();
            StringBuilder preview = new StringBuilder();
            int shown = 0;
            for (String name : seen.keySet()) {
                actualRoots.add(name.substring(0, name.indexOf('/')));
                if (shown++ < 20) preview.append("\n").append(name);
            }
            if (seen.size() > 20) preview.append("\n… and ").append(seen.size() - 20).append(" more entries");
            return "App version: " + version + "\nIncluded roots: " + actualRoots +
                "\nEntries: " + seen.size() + "\nFile bytes: " + total + "\nPaths to restore:" + preview;
        }
    }
    private File safeDestination(String path) throws Exception {
        int slash = path.indexOf('/');
        File root = roots.get(path.substring(0, slash));
        if (root == null) throw new IOException("Unknown root");
        if (!root.exists() && !root.mkdirs()) throw new IOException("Cannot create root");
        if (!OsConstants.S_ISDIR(Os.lstat(root.getPath()).st_mode)) throw new IOException("Unsafe root");
        File result = new File(root, path.substring(slash + 1));
        File parent = result.getParentFile();
        while (!parent.equals(root)) {
            if (!OsConstants.S_ISDIR(Os.lstat(parent.getPath()).st_mode)) throw new IOException("Unsafe destination parent: " + parent);
            parent = parent.getParentFile();
        }
        return result;
    }
    private void transfer(InputStream in, OutputStream out, long length, String path) throws IOException {
        byte[] buffer = new byte[65536];
        while (length > 0) {
            progress.update(path);
            int n = in.read(buffer, 0, (int) Math.min(length, buffer.length));
            if (n < 0) throw new EOFException("Truncated file: " + path);
            if (out != null) out.write(buffer, 0, n);
            length -= n;
        }
    }
}
