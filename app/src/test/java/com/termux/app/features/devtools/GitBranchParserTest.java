package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GitBranchParserTest {

    private static final String U = "\u001f";

    private static final String OUTPUT =
        "main" + U + "abc123" + U + "origin/main" + U + "ahead 2, behind 1" + U + "*" + "\n"
        + "feature" + U + "def456" + U + "" + U + "" + U + " " + "\n"
        + "stale" + U + "789abc" + U + "origin/stale" + U + "gone" + U + " " + "\n"
        + "behind-only" + U + "111aaa" + U + "origin/behind-only" + U + "behind 3" + U + " " + "\n";

    @Test
    public void testParseBranches() {
        List<GitBranchParser.Branch> branches = GitBranchParser.parse(OUTPUT);
        Assert.assertEquals(4, branches.size());

        GitBranchParser.Branch main = branches.get(0);
        Assert.assertEquals("main", main.name);
        Assert.assertEquals("abc123", main.objectShort);
        Assert.assertEquals("origin/main", main.upstream);
        Assert.assertEquals(2, main.ahead);
        Assert.assertEquals(1, main.behind);
        Assert.assertTrue(main.current);
        Assert.assertFalse(main.gone);
        Assert.assertEquals("\u21912 \u21931", main.describeTracking());

        GitBranchParser.Branch feature = branches.get(1);
        Assert.assertFalse(feature.current);
        Assert.assertEquals("", feature.describeTracking());

        GitBranchParser.Branch stale = branches.get(2);
        Assert.assertTrue(stale.gone);
        Assert.assertEquals("[gone]", stale.describeTracking());

        GitBranchParser.Branch behindOnly = branches.get(3);
        Assert.assertEquals(0, behindOnly.ahead);
        Assert.assertEquals(3, behindOnly.behind);
        Assert.assertEquals("\u21933", behindOnly.describeTracking());
    }

    @Test
    public void testMalformedInputDoesNotThrow() {
        Assert.assertTrue(GitBranchParser.parse(null).isEmpty());
        Assert.assertTrue(GitBranchParser.parse("").isEmpty());
        Assert.assertTrue(GitBranchParser.parse("main\n").isEmpty());
    }
}
