package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class GitLogParserTest {

    private static final String U = "\u001f";
    private static final String R = "\u001e";

    private static final String OUTPUT =
        "abc123" + U + "def456" + U + "Alice" + U + "alice@example.com" + U + "2024-01-01T10:00:00+00:00"
            + U + "First subject" + U + "First body\nsecond line" + R + "\n"
        + "789abc" + U + "" + U + "Bob" + U + "" + U + "2024-01-02" + U + "Second subject" + U + "" + R + "\n";

    @Test
    public void testParseCommits() {
        List<GitLogParser.Commit> commits = GitLogParser.parse(OUTPUT);
        Assert.assertEquals(2, commits.size());
        GitLogParser.Commit first = commits.get(0);
        Assert.assertEquals("abc123", first.hash);
        Assert.assertEquals("abc123", first.shortHash());
        Assert.assertEquals("def456", first.parents);
        Assert.assertEquals("Alice", first.author);
        Assert.assertEquals("alice@example.com", first.email);
        Assert.assertEquals("2024-01-01T10:00:00+00:00", first.date);
        Assert.assertEquals("First subject", first.subject);
        Assert.assertEquals("First body\nsecond line", first.body);

        GitLogParser.Commit second = commits.get(1);
        Assert.assertEquals("789abc", second.hash);
        Assert.assertEquals("", second.parents);
        Assert.assertEquals("Second subject", second.subject);
        Assert.assertEquals("", second.body);
    }

    @Test
    public void testMalformedInputDoesNotThrow() {
        Assert.assertTrue(GitLogParser.parse(null).isEmpty());
        Assert.assertTrue(GitLogParser.parse("").isEmpty());
        Assert.assertTrue(GitLogParser.parse("no-separators-here").isEmpty());
    }
}
