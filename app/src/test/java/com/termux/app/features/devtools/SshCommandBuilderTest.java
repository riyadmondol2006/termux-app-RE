package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

public class SshCommandBuilderTest {

    @Test
    public void testQuote() {
        Assert.assertEquals("'plain'", SshCommandBuilder.quote("plain"));
        Assert.assertEquals("'a'\\''b'", SshCommandBuilder.quote("a'b"));
        Assert.assertEquals("'a; rm -rf ~'", SshCommandBuilder.quote("a; rm -rf ~"));
    }

    @Test
    public void testBuildSshCommand() {
        SshConnection connection = new SshConnection("work", "bob", "work.example.com", 2222,
            "~/.ssh/id_ed25519", "", "ServerAliveInterval 60", true);
        String command = SshCommandBuilder.buildSshCommand(connection);
        Assert.assertTrue(command.startsWith("ssh "));
        Assert.assertTrue(command.contains(" -p 2222 "));
        Assert.assertTrue(command.contains(" -i '" + "~/.ssh/id_ed25519" + "' "));
        Assert.assertTrue(command.contains(" -o'ServerAliveInterval=60' "));
        Assert.assertTrue(command.endsWith(" -- 'bob@work.example.com'"));
        // Default port is omitted.
        SshConnection plain = new SshConnection("h", "", "h.example.com", 0, "", "", "", true);
        Assert.assertEquals("ssh -- 'h.example.com'", SshCommandBuilder.buildSshCommand(plain));
    }

    @Test
    public void testHostInjectionIsQuoted() {
        SshConnection evil = new SshConnection("x", "", "h.example.com; rm -rf ~", 0, "", "", "", true);
        String command = SshCommandBuilder.buildSshCommand(evil);
        Assert.assertTrue(command.contains("'h.example.com; rm -rf ~'"));
    }

    @Test
    public void testDestinationCannotBeAnOption() {
        SshConnection evil = new SshConnection("x", "", "-oProxyCommand=touch /tmp/pwned", 0, "", "", "", true);
        Assert.assertTrue(SshCommandBuilder.buildSshCommand(evil).contains(" -- '-oProxyCommand=touch /tmp/pwned'"));
        Assert.assertFalse(SshCommandBuilder.isValidDestinationPart("-oProxyCommand=x"));
        Assert.assertFalse(SshCommandBuilder.isValidDestinationPart("a b"));
        Assert.assertFalse(SshCommandBuilder.isValidDestinationPart(""));
        Assert.assertTrue(SshCommandBuilder.isValidDestinationPart("host.example.com"));
        Assert.assertTrue(SshCommandBuilder.isValidDestinationPart("bob"));
    }

    @Test
    public void testExtraOptionsDropsJunk() {
        Assert.assertEquals("", SshCommandBuilder.buildExtraArgs("$(evil) x\n# just a comment\n"));
        Assert.assertEquals("-o'Foo=bar'",
            SshCommandBuilder.buildExtraArgs("# comment\nFoo bar\n"));
        Assert.assertEquals("-o'Foo=bar' -o'Baz=1'",
            SshCommandBuilder.buildExtraArgs("Foo=bar\nBaz 1"));
    }

    @Test
    public void testCopyIdCommandUsesKeyFile() {
        SshConnection connection = new SshConnection("h", "u", "h.example.com", 2222, "", "", "", true);
        String command = SshCommandBuilder.buildCopyIdCommand(connection, "/home/.ssh/id_ed25519.pub");
        Assert.assertTrue(command.contains("ssh-copy-id"));
        Assert.assertTrue(command.contains("'/home/.ssh/id_ed25519.pub'"));
        Assert.assertTrue(command.contains(" -- 'u@h.example.com'"));
        Assert.assertTrue(command.contains("authorized_keys"));
    }
}
