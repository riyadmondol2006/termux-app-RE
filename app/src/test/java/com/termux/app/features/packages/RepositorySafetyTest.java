package com.termux.app.features.packages;

import org.junit.Assert;
import org.junit.Test;

public class RepositorySafetyTest {

    @Test
    public void testOfficialRepositoryDetection() {
        Assert.assertTrue(RepositorySafety.isOfficialTermuxRepository("https://packages.termux.dev/apt/termux-main"));
        Assert.assertTrue(RepositorySafety.isOfficialTermuxRepository("https://packages-cf.termux.dev/apt/termux-main"));
        Assert.assertTrue(RepositorySafety.isOfficialTermuxRepository("https://grimler.se/termux-packages-24"));
        Assert.assertFalse(RepositorySafety.isOfficialTermuxRepository(
            "https://riyadmondol2006.github.io/termux-app-RE/apt/termux-main"));
        Assert.assertFalse(RepositorySafety.isOfficialTermuxRepository("not a URI"));
        Assert.assertFalse(RepositorySafety.isOfficialTermuxRepository("file:///data/data/com.termux.re/repo"));
    }

}
