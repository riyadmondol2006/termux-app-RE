package com.termux.app.features.packages;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import static org.junit.Assert.*;

public class OfficialPackagesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void partialInstallNeverReportsEnabled() throws Exception {
        File prefix = temporary.newFolder();
        String[] required = {"etc/apt/sources.list.d/termux-re-official.list",
            "etc/apt/apt.conf.d/90termux-re-compat", "etc/apt/preferences.d/termux-re-official",
            "share/keyrings/termux-re-official.gpg"};
        assertFalse(OfficialPackages.enabled(prefix));
        for (int i = 0; i < required.length; i++) {
            File file = new File(prefix, required[i]);
            file.getParentFile().mkdirs();
            assertTrue(file.createNewFile());
            assertEquals(i == required.length - 1, OfficialPackages.enabled(prefix));
        }
        for (String path : required) {
            File file = new File(prefix, path);
            assertTrue(file.delete());
            assertFalse(OfficialPackages.enabled(prefix));
            assertTrue(file.createNewFile());
        }
    }
}
