package com.termux.app.features.packages;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class RepositoryStoreTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void testLoadToggleAndBackup() throws Exception {
        File aptEtc = temporary.newFolder("apt");
        File source = new File(aptEtc, "sources.list");
        java.nio.file.Files.write(source.toPath(), ("# comment\n" +
            "deb https://example.com/apt stable main\n" +
            "# deb https://disabled.example.com stable main\n").getBytes(StandardCharsets.UTF_8));

        List<AptSourceEntry> sources = RepositoryStore.loadSources(aptEtc);
        Assert.assertEquals(2, sources.size());
        Assert.assertTrue(sources.get(0).enabled);
        Assert.assertFalse(sources.get(1).enabled);

        File backup = RepositoryStore.setEnabled(sources.get(0), false);
        Assert.assertTrue(backup.isFile());
        Assert.assertTrue(backup.getName().startsWith("sources.list.bak-"));
        // The backup keeps the file as it was before the change.
        Assert.assertEquals("deb https://example.com/apt stable main", read(backup).split("\n")[1]);
        Assert.assertEquals("# deb https://example.com/apt stable main", read(source).split("\n")[1]);

        sources = RepositoryStore.loadSources(aptEtc);
        RepositoryStore.remove(sources.get(0));
        Assert.assertFalse(read(source).contains("example.com/apt"));
    }

    @Test
    public void testAddRepository() throws Exception {
        File aptEtc = temporary.newFolder("apt");
        RepositoryStore.add(aptEtc, "https://example.com/apt", "stable", "main extra");
        List<AptSourceEntry> sources = RepositoryStore.loadSources(aptEtc);
        Assert.assertEquals(1, sources.size());
        Assert.assertEquals("deb https://example.com/apt stable main extra", sources.get(0).declaration);
    }

    private static String read(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
