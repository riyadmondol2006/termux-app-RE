package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SshConnectionTest {

    @Test
    public void testJsonRoundTrip() {
        SshConnection connection = new SshConnection("work \"main\"",
            "b\\ob", "work.example.com", 2222,
            "~/.ssh/id_ed25519", "jump\nhost", "ServerAliveInterval 60", false);
        SshConnection parsed = SshConnection.fromJson(connection.toJson());
        Assert.assertEquals("work \"main\"", parsed.label);
        Assert.assertEquals("b\\ob", parsed.user);
        Assert.assertEquals("work.example.com", parsed.host);
        Assert.assertEquals(2222, parsed.port);
        Assert.assertEquals("~/.ssh/id_ed25519", parsed.identityFile);
        Assert.assertEquals("jump\nhost", parsed.jumpHost);
        Assert.assertEquals("ServerAliveInterval 60", parsed.extraOptions);
        Assert.assertFalse(parsed.syncToConfig);
    }

    @Test
    public void testListRoundTrip() {
        List<SshConnection> connections = new ArrayList<>();
        connections.add(new SshConnection("a", "u", "a.example.com", 0, "", "", "", true));
        connections.add(new SshConnection("b", "", "b.example.com", 22, "", "", "", false));
        List<SshConnection> parsed = SshConnection.listFromJson(SshConnection.listToJson(connections));
        Assert.assertEquals(2, parsed.size());
        Assert.assertEquals("a", parsed.get(0).label);
        Assert.assertEquals("u", parsed.get(0).user);
        Assert.assertTrue(parsed.get(0).syncToConfig);
        Assert.assertEquals("b", parsed.get(1).label);
        Assert.assertFalse(parsed.get(1).syncToConfig);
    }

    @Test
    public void testMalformedInputDoesNotThrow() {
        Assert.assertTrue(SshConnection.listFromJson(null).isEmpty());
        Assert.assertTrue(SshConnection.listFromJson("not json").isEmpty());
        Assert.assertTrue(SshConnection.listFromJson("[{unclosed").isEmpty());
        SshConnection parsed = SshConnection.fromJson(null);
        Assert.assertEquals("", parsed.label);
    }

    @Test
    public void testDescribeTarget() {
        Assert.assertEquals("u@h.example.com:2222",
            new SshConnection("l", "u", "h.example.com", 2222, "", "", "", true).describeTarget());
        Assert.assertEquals("h.example.com",
            new SshConnection("l", "", "h.example.com", 0, "", "", "", true).describeTarget());
        Assert.assertEquals("h.example.com",
            new SshConnection("l", "", "h.example.com", 22, "", "", "", true).describeTarget());
    }
}
