package com.termux.app.features.system.update;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class UpdateVerifierTest {

    private static final String REPO = "https://github.com/riyadmondol2006/termux-app-RE";

    @Test
    public void testSha256Hex() throws Exception {
        Assert.assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateVerifier.sha256Hex(new ByteArrayInputStream(new byte[0])));
        Assert.assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateVerifier.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
        // More than one read buffer.
        byte[] large = new byte[200 * 1024];
        Assert.assertEquals(64, UpdateVerifier.sha256Hex(new ByteArrayInputStream(large)).length());
    }

    @Test
    public void testChecksumMatches() {
        String sum = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        Assert.assertTrue(UpdateVerifier.checksumMatches(sum, sum));
        Assert.assertTrue(UpdateVerifier.checksumMatches(sum.toUpperCase(), sum));
        Assert.assertTrue(UpdateVerifier.checksumMatches(" " + sum + "\n", sum));
        Assert.assertFalse(UpdateVerifier.checksumMatches(sum, sum.replace('b', 'c')));
        Assert.assertFalse(UpdateVerifier.checksumMatches(sum, ""));
    }

    @Test
    public void testIsTrustedDownloadUrl() {
        Assert.assertTrue(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/v1.0.0/termux-re_v1.0.0-release_arm64-v8a.apk", REPO));
        Assert.assertTrue(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/v1.0.0/a.apk", REPO + ".git"));
        Assert.assertTrue(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/v1.0.0/a.apk", REPO + "/"));
        // Another repository, another host, no https, path tricks.
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://github.com/someone/else/releases/download/v1/a.apk", REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://evil.example/riyadmondol2006/termux-app-RE/releases/download/v1/a.apk", REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl("http://github.com/riyadmondol2006/termux-app-RE/releases/download/v1/a.apk", REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/../../x.apk", REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/", REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl(null, REPO));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/v1/a.apk", "https://gitlab.com/a/b"));
        Assert.assertFalse(UpdateVerifier.isTrustedDownloadUrl(REPO + "/releases/download/v1/a.apk", null));
    }

    @Test
    public void testIsSafeApkName() {
        Assert.assertTrue(UpdateVerifier.isSafeApkName("termux-re_v1.0.0-release_arm64-v8a.apk"));
        Assert.assertFalse(UpdateVerifier.isSafeApkName("../evil.apk"));
        Assert.assertFalse(UpdateVerifier.isSafeApkName("a/b.apk"));
        Assert.assertFalse(UpdateVerifier.isSafeApkName(".hidden.apk"));
        Assert.assertFalse(UpdateVerifier.isSafeApkName("file.txt"));
        Assert.assertFalse(UpdateVerifier.isSafeApkName(null));
        Assert.assertFalse(UpdateVerifier.isSafeApkName(""));
    }

}
