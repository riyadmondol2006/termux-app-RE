package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class GitCommandsTest {

    @Test
    public void testStatusArgv() {
        Assert.assertEquals(
            Arrays.asList("git", "-C", "/home/repo", "status", "--porcelain=v2", "-b"),
            GitCommands.status("/home/repo"));
    }

    @Test
    public void testLogFormatMatchesParser() {
        // The log format must produce the separators GitLogParser expects.
        Assert.assertTrue(GitCommands.LOG_FORMAT.contains("%x1f"));
        Assert.assertTrue(GitCommands.LOG_FORMAT.contains("%x1e"));
    }

    @Test
    public void testBranchFormatMatchesParser() {
        Assert.assertTrue(GitCommands.BRANCH_FORMAT.contains("%x1f"));
    }

    @Test
    public void testStageUsesEndOfOptions() {
        // "--" keeps paths starting with "-" from being parsed as flags.
        Assert.assertEquals(
            Arrays.asList("git", "-C", "/r", "add", "--", "-evil.java"),
            GitCommands.stage("/r", Collections.singletonList("-evil.java")));
    }

    @Test
    public void testToShellCommandQuotes() {
        Assert.assertEquals("'git' '-C' '/r; rm' 'pull'",
            GitCommands.toShellCommand(Arrays.asList("git", "-C", "/r; rm", "pull")));
    }
}
