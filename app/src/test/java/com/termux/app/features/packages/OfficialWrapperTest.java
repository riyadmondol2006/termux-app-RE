package com.termux.app.features.packages;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.assertEquals;

public class OfficialWrapperTest {
    @Test public void rewritesBinaryMetadataAndLinksWithoutChangingDownload() throws Exception {
        File project = new File(System.getProperty("user.dir"));
        if (!new File(project, "src/main/assets").isDirectory()) project = new File(project, "app");
        Process process = new ProcessBuilder("bash",
            new File(project, "src/test/resources/termux-re-compat/wrapper-test.sh").getAbsolutePath(),
            new File(project, "src/main/assets/termux-re-compat/dpkg").getAbsolutePath())
            .inheritIO().start();
        assertEquals(0, process.waitFor());
    }
}
