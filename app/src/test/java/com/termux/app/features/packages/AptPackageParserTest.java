package com.termux.app.features.packages;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AptPackageParserTest {

    @Test
    public void testParseAptList() {
        String output = "Listing... Done\n" +
            "bash/stable 5.3.3-1 aarch64 [upgradable from: 5.3.2]\n" +
            "coreutils/stable 9.7-1 aarch64 [installed,automatic]\n" +
            "python/stable 3.12.11 aarch64\n" +
            "not a package line\n";
        List<PackageInfo> packages = AptPackageParser.parseAptList(output);
        Assert.assertEquals(3, packages.size());
        Assert.assertEquals("bash", packages.get(0).name);
        Assert.assertEquals("5.3.3-1", packages.get(0).version);
        Assert.assertEquals("aarch64", packages.get(0).architecture);
        Assert.assertTrue(packages.get(0).upgradable);
        Assert.assertTrue(packages.get(1).installed);
        Assert.assertFalse(packages.get(2).installed);
    }

    @Test
    public void testParseSearch() {
        List<PackageInfo> packages = AptPackageParser.parseSearch(
            "python - Python programming language\npython-numpy - Numerical Python\nWARNING: apt does not have a stable CLI\n");
        Assert.assertEquals(2, packages.size());
        Assert.assertEquals("python", packages.get(0).name);
        Assert.assertEquals("Python programming language", packages.get(0).description);
        Assert.assertEquals("python-numpy", packages.get(1).name);
    }

    @Test
    public void testParseShow() {
        PackageDetails details = AptPackageParser.parseShow(
            "Package: example\nVersion: 1.2.3-4\nInstalled-Size: 2048\nSize: 345678\n" +
            "Depends: bash, libandroid-support\nDescription: Example package\n A continued description.\n .\n Another paragraph.\n" +
            "Homepage: https://example.com\n\nPackage: example\nVersion: 1.0\n", "requested");
        Assert.assertEquals("example", details.name);
        Assert.assertEquals("1.2.3-4", details.get("Version"));
        Assert.assertEquals("2048", details.get("Installed-Size"));
        Assert.assertEquals("345678", details.get("Size"));
        Assert.assertEquals("bash, libandroid-support", details.get("Depends"));
        Assert.assertEquals("Example package\nA continued description.\n\nAnother paragraph.", details.get("Description"));
        Assert.assertEquals("https://example.com", details.get("Homepage"));
    }

    @Test
    public void testParseShowUsesRequestedNameForEmptyOutput() {
        Assert.assertEquals("missing", AptPackageParser.parseShow("", "missing").name);
    }

}
