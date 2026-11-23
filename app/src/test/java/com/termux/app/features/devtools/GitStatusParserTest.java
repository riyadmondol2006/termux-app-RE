package com.termux.app.features.devtools;

import org.junit.Assert;
import org.junit.Test;

public class GitStatusParserTest {

    private static final String OUTPUT =
        "# branch.oid abc123def456\n"
        + "# branch.head main\n"
        + "# branch.upstream origin/main\n"
        + "# branch.ab +2 -1\n"
        + "1 M. N... 100644 100644 100644 abc123 abc123 src/Main.java\n"
        + "1 .M N... 100644 100644 100644 abc123 abc123 src/Other.java\n"
        + "1 MM N... 100644 100644 100644 abc123 abc123 src/Both.java\n"
        + "2 R. N... 100644 100644 100644 abc123 abc123 R100 src/New.java\tsrc/Old.java\n"
        + "? src/Untracked.java\n"
        + "! src/Ignored.java\n";

    @Test
    public void testBranchHeader() {
        GitStatusParser.Status status = GitStatusParser.parse(OUTPUT);
        Assert.assertEquals("abc123def456", status.branchOid);
        Assert.assertEquals("main", status.branchHead);
        Assert.assertEquals("origin/main", status.upstream);
        Assert.assertEquals(2, status.ahead);
        Assert.assertEquals(1, status.behind);
        Assert.assertEquals("main \u21912 \u21931", status.describeBranch());
    }

    @Test
    public void testEntries() {
        GitStatusParser.Status status = GitStatusParser.parse(OUTPUT);
        Assert.assertEquals(6, status.entries.size());

        Assert.assertEquals(3, status.staged().size());
        Assert.assertEquals(2, status.unstaged().size());
        Assert.assertEquals(1, status.untracked().size());

        // Rename keeps the original path.
        GitStatusParser.Entry rename = null;
        for (GitStatusParser.Entry e : status.entries) {
            if ("src/New.java".equals(e.path)) rename = e;
        }
        Assert.assertNotNull(rename);
        Assert.assertEquals("src/Old.java", rename.origPath);
        Assert.assertTrue(rename.isStaged());
        Assert.assertFalse(rename.isUnstaged());

        // Untracked entries are neither staged nor unstaged.
        GitStatusParser.Entry untracked = status.untracked().get(0);
        Assert.assertEquals("src/Untracked.java", untracked.path);
        Assert.assertFalse(untracked.isStaged());
        Assert.assertFalse(untracked.isUnstaged());
    }

    @Test
    public void testDetachedHeadAndInitialCommit() {
        GitStatusParser.Status status = GitStatusParser.parse(
            "# branch.oid (initial)\n# branch.head (detached)\n");
        Assert.assertEquals("(initial)", status.branchOid);
        Assert.assertEquals("(detached)", status.branchHead);
        Assert.assertEquals(0, status.ahead);
        Assert.assertTrue(status.entries.isEmpty());
    }

    @Test
    public void testMalformedInputDoesNotThrow() {
        GitStatusParser.Status status = GitStatusParser.parse(null);
        Assert.assertTrue(status.entries.isEmpty());
        status = GitStatusParser.parse("garbage\n1 X\n2 Y a b\n? \n");
        Assert.assertTrue(status.entries.isEmpty());
    }
}
