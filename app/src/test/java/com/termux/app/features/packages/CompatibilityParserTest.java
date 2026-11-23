package com.termux.app.features.packages;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CompatibilityParserTest {

    @Test
    public void testForeignPrefixDetection() {
        Assert.assertTrue(PackageCompatibility.hasForeignPrefix("/data/data/com.termux/files/usr/bin/bash"));
        Assert.assertTrue(PackageCompatibility.hasForeignPrefix("./data/data/com.termux/files/usr/lib/libfoo.so"));
        Assert.assertFalse(PackageCompatibility.hasForeignPrefix("/data/data/com.termux.re/files/usr/bin/bash"));
        Assert.assertFalse(PackageCompatibility.hasForeignPrefix("/data/user/0/com.termux/files/usr/bin/bash"));
        Assert.assertFalse(PackageCompatibility.hasForeignPrefix("/usr/bin/bash"));
    }

    @Test
    public void testParseInstalledScan() {
        List<CompatibilityIssue> issues = CompatibilityParser.parseInstalledScan(
            "official\t/data/data/com.termux/files/usr/bin/tool\n" +
            "compatible\t/data/data/com.termux.re/files/usr/bin/tool\n" +
            "malformed line\n" +
            "another\tprefix /data/data/com.termux/files/usr/share/file\n");
        Assert.assertEquals(2, issues.size());
        Assert.assertEquals("official", issues.get(0).packageName);
        Assert.assertEquals("another", issues.get(1).packageName);
    }

    @Test
    public void testParseDebContents() {
        String output = "-rwxr-xr-x root/root 123 2026-01-01 00:00 ./data/data/com.termux/files/usr/bin/tool\n" +
            "-rw-r--r-- root/root 123 2026-01-01 00:00 ./data/data/com.termux.re/files/usr/bin/good\n" +
            "lrwxrwxrwx root/root 0 2026-01-01 00:00 ./usr/bin/link -> /data/data/com.termux/files/usr/bin/tool\n";
        List<CompatibilityIssue> issues = CompatibilityParser.parseDebContents(output, "sample.deb");
        Assert.assertEquals(2, issues.size());
        Assert.assertEquals("./data/data/com.termux/files/usr/bin/tool", issues.get(0).path);
        Assert.assertEquals("sample.deb", issues.get(1).packageName);
        Assert.assertEquals("/data/data/com.termux/files/usr/bin/tool", issues.get(1).path);
    }

}
