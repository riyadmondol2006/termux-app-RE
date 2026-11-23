package com.termux.app.features.packages;

import android.content.Context;
import com.termux.shared.termux.TermuxConstants;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Asset staging is inert; only the shell command enables the source. */
final class OfficialPackages {
    private OfficialPackages() {}

    static File layerDirectory(File prefix) {
        return new File(prefix, "libexec/termux-re-compat");
    }

    static boolean enabled(File prefix) {
        return new File(prefix, "etc/apt/sources.list.d/termux-re-official.list").isFile()
            && new File(prefix, "etc/apt/apt.conf.d/90termux-re-compat").isFile()
            && new File(prefix, "etc/apt/preferences.d/termux-re-official").isFile()
            && new File(prefix, "share/keyrings/termux-re-official.gpg").isFile();
    }

    static void stage(Context context) throws IOException {
        File prefix = new File(TermuxConstants.TERMUX_PREFIX_DIR_PATH);
        File directory = layerDirectory(prefix);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create compatibility directory");
        for (String name : new String[]{"dpkg", "official.gpg", "termux-re-official"}) {
            File destination = name.equals("termux-re-official") ? new File(prefix, "bin/" + name) : new File(directory, name);
            File temporary = new File(destination.getPath() + ".tmp");
            try (InputStream input = context.getAssets().open("termux-re-compat/" + name);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            }
            if (!name.endsWith(".gpg") && !temporary.setExecutable(true, true))
                throw new IOException("Cannot make compatibility command executable");
            if (!temporary.renameTo(destination)) throw new IOException("Cannot install " + name);
        }
    }
}
