package com.termux.app.features.files;

import org.junit.Test;
import java.io.*;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;

public class BackupArchiveTest {
    interface Entries { void write(DataOutputStream out) throws IOException; }
    private byte[] archive(String packageName, Entries entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeUTF(BackupArchive.MAGIC); out.writeInt(BackupArchive.VERSION);
            out.writeUTF(packageName); out.writeUTF("test"); out.writeUTF("home");
            entries.write(out); out.writeUTF("");
        }
        return bytes.toByteArray();
    }
    private void entry(DataOutputStream out, String path, int type) throws IOException {
        out.writeUTF(path); out.writeByte(type); out.writeInt(0700); out.writeLong(123);
        if (type == 0) out.writeLong(0);
        if (type == 2) out.writeUTF("/outside");
    }
    private String inspect(byte[] bytes) throws Exception {
        return new BackupArchive(Collections.emptyMap(), p -> {}).read(new ByteArrayInputStream(bytes), false);
    }
    private void rejected(byte[] bytes) throws Exception {
        try { inspect(bytes); fail("Accepted malicious archive"); } catch (IOException expected) {}
    }
    @Test public void validManifestAndEntries() throws Exception {
        assertTrue(inspect(archive("com.termux.re", out -> {
            entry(out, "home/.termux", 1);
            entry(out, "home/.termux/config", 0);
            entry(out, "home/storage", 2);
        })).contains("Entries: 3"));
    }
    @Test public void foreignPackageRejected() throws Exception {
        rejected(archive("com.termux", out -> {}));
    }
    @Test public void traversalRejected() throws Exception {
        rejected(archive("com.termux.re", out -> entry(out, "home/../../outside", 0)));
    }
    @Test public void symlinkParentRejected() throws Exception {
        rejected(archive("com.termux.re", out -> {
            entry(out, "home/link", 2); entry(out, "home/link/escaped", 0);
        }));
    }
    @Test public void duplicateRejected() throws Exception {
        rejected(archive("com.termux.re", out -> {
            entry(out, "home/file", 0); entry(out, "home/file", 2);
        }));
    }
    @Test public void missingParentRejected() throws Exception {
        rejected(archive("com.termux.re", out -> entry(out, "home/missing/file", 0)));
    }
    @Test public void truncatedDataRejected() throws Exception {
        rejected(archive("com.termux.re", out -> {
            out.writeUTF("home/file"); out.writeByte(0); out.writeInt(0600); out.writeLong(0);
            out.writeLong(100);
        }));
    }
}
