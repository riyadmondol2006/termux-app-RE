package com.termux.app.update;

import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
public class AppUpdateCheckerTest {

    @Test
    public void testCompareVersions() {
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.0.1", "1.0.0") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.10.0", "1.9.9") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("2.0.0", "1.99.99") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.0.0", "0.118.0") > 0);
        Assert.assertEquals(0, AppUpdateChecker.compareVersions("1.2.0", "1.2.0"));
        Assert.assertEquals(0, AppUpdateChecker.compareVersions("v1.2.0", "1.2.0"));

        // Build metadata of builds after a release tag is ignored.
        Assert.assertEquals(0, AppUpdateChecker.compareVersions("1.2.0", "1.2.0+3.g1a2b3c4"));
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.2.1", "1.2.0+3.g1a2b3c4") > 0);

        // Prereleases are lower than the release.
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0", "1.3.0-beta.2") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0-beta.2", "1.3.0-beta.1") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0-beta.11", "1.3.0-beta.2") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0-rc.1", "1.3.0-beta.9") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0-beta.1", "1.3.0-beta") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.3.0-beta.123456789012345678901", "1.3.0-beta.9") > 0);

        // Versions that can't be parsed are lower than any other.
        Assert.assertTrue(AppUpdateChecker.compareVersions("1.0.0", "invalid") > 0);
        Assert.assertTrue(AppUpdateChecker.compareVersions(null, "0.0.1") < 0);
        Assert.assertEquals(0, AppUpdateChecker.compareVersions(null, "invalid"));
    }

    @Test
    public void testGetReleasesApiUrl() {
        Assert.assertEquals("https://api.github.com/repos/owner/repo/releases",
            AppUpdateChecker.getReleasesApiUrl("https://github.com/owner/repo"));
        Assert.assertEquals("https://api.github.com/repos/owner/termux-app-RE/releases",
            AppUpdateChecker.getReleasesApiUrl("https://github.com/owner/termux-app-RE/"));
        Assert.assertEquals("https://api.github.com/repos/owner/repo/releases",
            AppUpdateChecker.getReleasesApiUrl("https://github.com/owner/repo.git"));
        Assert.assertNull(AppUpdateChecker.getReleasesApiUrl("https://gitlab.com/owner/repo"));
        Assert.assertNull(AppUpdateChecker.getReleasesApiUrl("https://github.com/owner"));
        Assert.assertNull(AppUpdateChecker.getReleasesApiUrl(null));
    }

    @Test
    public void testGetApkIndex() {
        String[] names = {
            "termux-re_v1.1.0-release_universal.apk",
            "termux-re_v1.1.0-release_armeabi-v7a.apk",
            "termux-re_v1.1.0-release_arm64-v8a.apk",
            "termux-re_v1.1.0-release_x86_64.apk",
        };
        Assert.assertEquals(2, AppUpdateChecker.getApkIndex(Arrays.asList(names), new String[]{"arm64-v8a", "armeabi-v7a", "armeabi"}));
        Assert.assertEquals(1, AppUpdateChecker.getApkIndex(Arrays.asList(names), new String[]{"armeabi-v7a", "armeabi"}));
        // Falls back to the universal APK.
        Assert.assertEquals(0, AppUpdateChecker.getApkIndex(Arrays.asList(names), new String[]{"x86"}));
        // Debug APKs are not used.
        Assert.assertEquals(-1, AppUpdateChecker.getApkIndex(Arrays.asList("termux-re_v1.1.0-debug_arm64-v8a.apk"), new String[]{"arm64-v8a"}));
    }

    @Test
    public void testGetLatestRelease() throws Exception {
        JSONArray releases = new JSONArray("[" +
            // Newest by date, but a bootstrap release: ignored.
            "{\"tag_name\": \"bootstrap-com.termux.re-3\", \"html_url\": \"https://example.com/bootstrap\", \"assets\": []}," +
            // Prerelease and draft: ignored.
            "{\"tag_name\": \"v1.3.0-beta.1\", \"prerelease\": true, \"html_url\": \"https://example.com/v1.3.0-beta.1\"}," +
            "{\"tag_name\": \"v1.4.0\", \"draft\": true, \"html_url\": \"https://example.com/v1.4.0\"}," +
            "{\"tag_name\": \"v1.2.0\", \"html_url\": \"https://example.com/v1.2.0\", \"body\": \"Notes\", \"assets\": [" +
            "  {\"name\": \"sha256sums.txt\", \"browser_download_url\": \"https://example.com/sha256sums.txt\"}," +
            "  {\"name\": \"termux-re_v1.2.0-release_universal.apk\", \"browser_download_url\": \"https://example.com/universal.apk\"}," +
            "  {\"name\": \"termux-re_v1.2.0-release_arm64-v8a.apk\", \"browser_download_url\": \"https://example.com/arm64.apk\"}" +
            "]}," +
            "{\"tag_name\": \"v1.10.0-x\", \"html_url\": \"https://example.com/invalid\"}," +
            "{\"tag_name\": \"v1.1.9\", \"html_url\": \"https://example.com/v1.1.9\"}" +
            "]");

        AppUpdateChecker.Release release = AppUpdateChecker.getLatestRelease(releases, new String[]{"arm64-v8a"});
        Assert.assertNotNull(release);
        Assert.assertEquals("1.2.0", release.version);
        Assert.assertEquals("https://example.com/v1.2.0", release.htmlUrl);
        Assert.assertEquals("Notes", release.notes);
        Assert.assertEquals("https://example.com/arm64.apk", release.apkUrl);

        release = AppUpdateChecker.getLatestRelease(releases, new String[]{"x86"});
        Assert.assertNotNull(release);
        Assert.assertEquals("https://example.com/universal.apk", release.apkUrl);

        Assert.assertNull(AppUpdateChecker.getLatestRelease(new JSONArray("[{\"tag_name\": \"bootstrap-com.termux.re-1\"}]"), new String[]{"arm64-v8a"}));
        Assert.assertNull(AppUpdateChecker.getLatestRelease(new JSONArray("[]"), new String[]{"arm64-v8a"}));
    }

}
