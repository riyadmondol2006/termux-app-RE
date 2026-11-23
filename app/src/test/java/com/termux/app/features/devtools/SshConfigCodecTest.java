package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SshConfigCodecTest {

    private static final String CONFIG =
        "# global comment\n"
        + "ServerAliveInterval 60\n"
        + "\n"
        + "Host home server\n"
        + "    HostName example.com\n"
        + "    User alice\n"
        + "    Port 2222\n"
        + "    IdentityFile ~/.ssh/id_ed25519\n"
        + "\n"
        + "Host em dash\n"
        + "    HostName 10.0.0.2\n"
        + "\n"
        + "Host *.example.org\n"
        + "    User bob\n"
        + "\n"
        + "Match host 10.0.0.1\n"
        + "    User ignored\n"
        + "\n"
        + "Host after-match\n"
        + "    HostName 10.0.0.3\n";

    @Test
    public void testParseHostBlocks() {
        List<SshConfigCodec.HostEntry> entries = SshConfigCodec.parse(CONFIG);
        Assert.assertEquals(4, entries.size());
        Assert.assertEquals(2, entries.get(0).patterns.size());
        Assert.assertEquals("example.com", entries.get(0).getOption("hostname"));
        Assert.assertEquals("alice", entries.get(0).getOption("User"));
        Assert.assertEquals("2222", entries.get(0).getOption("PORT"));
        // Global options before the first Host line are ignored.
        Assert.assertNull(entries.get(0).getOption("serveraliveinterval"));
        // Match blocks do not leak into Host entries.
        Assert.assertEquals("10.0.0.2", entries.get(1).getOption("hostname"));
        Assert.assertEquals("10.0.0.3", entries.get(3).getOption("hostname"));
    }

    @Test
    public void testToConnections() {
        List<SshConnection> connections = SshConfigCodec.toConnections(SshConfigCodec.parse(CONFIG));
        // The wildcard entry is skipped.
        Assert.assertEquals(3, connections.size());
        SshConnection first = connections.get(0);
        Assert.assertEquals("home", first.label);
        Assert.assertEquals("example.com", first.host);
        Assert.assertEquals("alice", first.user);
        Assert.assertEquals(2222, first.port);
        Assert.assertEquals("~/.ssh/id_ed25519", first.identityFile);
        SshConnection second = connections.get(1);
        Assert.assertEquals("em", second.label);
        Assert.assertEquals("10.0.0.2", second.host);
        Assert.assertEquals("", second.user);
        Assert.assertEquals(0, second.port);
    }

    @Test
    public void testEqualsSeparatedOptions() {
        List<SshConfigCodec.HostEntry> entries = SshConfigCodec.parse("Host x\nHostName=y\nPort=22\n");
        Assert.assertEquals(1, entries.size());
        Assert.assertEquals("y", entries.get(0).getOption("hostname"));
        Assert.assertEquals("22", entries.get(0).getOption("port"));
    }

    @Test
    public void testSplicePreservesManualEdits() {
        String manual = "# my own stuff\nHost personal\n    HostName personal.example.com\n";
        SshConnection connection = new SshConnection("work", "bob", "work.example.com", 0,
            "", "", "ServerAliveInterval 60", true);
        String updated = SshConfigCodec.spliceManagedBlock(manual,
            java.util.Collections.singletonList(connection));
        Assert.assertTrue(updated.contains("Host personal"));
        Assert.assertTrue(updated.contains("HostName personal.example.com"));
        Assert.assertTrue(updated.contains(SshConfigCodec.MANAGED_BEGIN));
        Assert.assertTrue(updated.contains("Host work"));
        Assert.assertTrue(updated.contains("ServerAliveInterval 60"));

        // Re-splicing replaces the managed block instead of duplicating it.
        SshConnection other = new SshConnection("other", "", "other.example.com", 0, "", "", "", true);
        String respliced = SshConfigCodec.spliceManagedBlock(updated,
            java.util.Collections.singletonList(other));
        Assert.assertTrue(respliced.contains("Host personal"));
        Assert.assertFalse(respliced.contains("Host work"));
        Assert.assertTrue(respliced.contains("Host other"));
        Assert.assertEquals(1, countOccurrences(respliced, SshConfigCodec.MANAGED_BEGIN));
    }

    @Test
    public void testSpliceAppendsWhenNoBlock() {
        SshConnection connection = new SshConnection("h", "", "h.example.com", 0, "", "", "", true);
        String updated = SshConfigCodec.spliceManagedBlock("",
            java.util.Collections.singletonList(connection));
        Assert.assertTrue(updated.contains(SshConfigCodec.MANAGED_BEGIN));
        Assert.assertTrue(updated.endsWith("\n"));
    }

    @Test
    public void testUnsyncedConnectionsAreSkipped() {
        SshConnection connection = new SshConnection("h", "", "h.example.com", 0, "", "", "", false);
        String updated = SshConfigCodec.spliceManagedBlock("",
            java.util.Collections.singletonList(connection));
        Assert.assertFalse(updated.contains("Host h"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
