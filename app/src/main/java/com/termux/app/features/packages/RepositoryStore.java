package com.termux.app.features.packages;

import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class RepositoryStore {

    private RepositoryStore() {}

    @NonNull
    static List<AptSourceEntry> loadSources(@NonNull File aptEtc) throws IOException {
        List<File> files = new ArrayList<>();
        File main = new File(aptEtc, "sources.list");
        if (main.isFile()) files.add(main);
        File directory = new File(aptEtc, "sources.list.d");
        File[] children = directory.listFiles();
        if (children != null) {
            Arrays.sort(children, Comparator.comparing(File::getName));
            for (File file : children) {
                if (!file.isFile() || file.getName().contains(".bak-") || file.getName().endsWith("~")) continue;
                files.add(file);
            }
        }
        List<AptSourceEntry> sources = new ArrayList<>();
        for (File file : files) {
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                AptSourceParser.ParsedSource parsed = AptSourceParser.parse(lines.get(i));
                if (parsed != null) sources.add(new AptSourceEntry(file, i, parsed.enabled, parsed.declaration));
            }
        }
        return sources;
    }

    @NonNull
    static File setEnabled(@NonNull AptSourceEntry source, boolean enabled) throws IOException {
        List<String> lines = readLines(source.file);
        if (source.line < 0 || source.line >= lines.size()) throw new IOException("Source file changed; reload and try again");
        AptSourceParser.setEnabled(lines, source.line, enabled);
        return writeLines(source.file, lines);
    }

    @NonNull
    static File remove(@NonNull AptSourceEntry source) throws IOException {
        List<String> lines = readLines(source.file);
        if (source.line < 0 || source.line >= lines.size()) throw new IOException("Source file changed; reload and try again");
        if (AptSourceParser.parse(lines.get(source.line)) == null) throw new IOException("Source file changed; reload and try again");
        lines.remove(source.line);
        return writeLines(source.file, lines);
    }

    @NonNull
    static File add(@NonNull File aptEtc, @NonNull String url, @NonNull String suite,
                    @NonNull String components) throws IOException {
        File directory = new File(aptEtc, "sources.list.d");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create sources.list.d");
        String host;
        try {
            host = java.net.URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid repository URL");
        }
        if (host == null) throw new IOException("Repository URL must include a host");
        String filename = "termux-re-" + host.replaceAll("[^A-Za-z0-9.-]", "_") + ".list";
        File file = new File(directory, filename);
        List<String> lines = file.isFile() ? readLines(file) : new ArrayList<>();
        String declaration = "deb " + url + " " + suite + " " + components;
        for (String line : lines) {
            AptSourceParser.ParsedSource parsed = AptSourceParser.parse(line);
            if (parsed != null && parsed.declaration.equals(declaration)) throw new IOException("Repository already added");
        }
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) lines.add("");
        lines.add("# Added by Termux RE Repository Manager");
        lines.add(declaration);
        return writeLines(file, lines);
    }

    @NonNull
    static List<File> loadKeys(@NonNull File aptEtc) {
        File[] files = new File(aptEtc, "trusted.gpg.d").listFiles();
        if (files == null) return Collections.emptyList();
        List<File> keys = new ArrayList<>();
        for (File file : files) if (file.isFile()) keys.add(file);
        keys.sort(Comparator.comparing(File::getName));
        return keys;
    }

    @NonNull
    static List<MirrorOption> loadMirrors(@NonNull File prefixEtc) {
        File mirrors = new File(prefixEtc, "termux/mirrors");
        List<MirrorOption> options = new ArrayList<>();
        File[] groups = mirrors.listFiles();
        if (groups == null) return options;
        Arrays.sort(groups, Comparator.comparing(File::getName));
        for (File group : groups) {
            if (group.isDirectory()) options.add(new MirrorOption("Mirror group: " + group.getName(), group));
        }
        File defaultMirror = new File(mirrors, "default");
        if (defaultMirror.isFile()) options.add(new MirrorOption("Default mirror", defaultMirror));
        for (File group : groups) {
            if (!group.isDirectory()) continue;
            File[] files = group.listFiles();
            if (files == null) continue;
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File file : files) {
                if (file.isFile() && !file.getName().endsWith(".dpkg-old") && !file.getName().endsWith(".dpkg-new") &&
                    !file.getName().endsWith("~")) options.add(new MirrorOption("Mirror: " + file.getName(), file));
            }
        }
        return options;
    }

    @Nullable
    static String currentMirror(@NonNull File prefixEtc) {
        File chosen = new File(prefixEtc, "termux/chosen_mirrors");
        try {
            if (!chosen.exists() && !Os.readlink(chosen.getAbsolutePath()).isEmpty()) return null;
            return chosen.getCanonicalPath();
        } catch (Exception e) {
            return null;
        }
    }

    static void selectMirror(@NonNull File prefixEtc, @NonNull MirrorOption option) throws Exception {
        File termuxEtc = new File(prefixEtc, "termux");
        File chosen = new File(termuxEtc, "chosen_mirrors");
        if (chosen.exists() && !chosen.isDirectory() || !chosen.exists()) {
            if (chosen.exists() && !isSymlink(chosen)) throw new IOException("chosen_mirrors is not a symbolic link");
        } else {
            throw new IOException("chosen_mirrors is a real directory; use termux-change-repo to repair it");
        }
        if (isSymlink(chosen) && !chosen.delete()) throw new IOException("Cannot replace chosen_mirrors");
        Os.symlink(option.target.getAbsolutePath(), chosen.getAbsolutePath());
    }

    private static boolean isSymlink(@NonNull File file) {
        try {
            Os.readlink(file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    private static List<String> readLines(@NonNull File file) throws IOException {
        String content;
        try (FileInputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            content = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        return new ArrayList<>(Arrays.asList(content.split("\\r?\\n", -1)));
    }

    @NonNull
    private static File writeLines(@NonNull File file, @NonNull List<String> lines) throws IOException {
        File backup = null;
        if (file.exists()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            backup = new File(file.getParentFile(), file.getName() + ".bak-" + timestamp);
            int suffix = 1;
            while (backup.exists()) backup = new File(file.getParentFile(),
                file.getName() + ".bak-" + timestamp + "-" + suffix++);
            copy(file, backup);
        }
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!temporary.renameTo(file)) {
            temporary.delete();
            throw new IOException("Cannot replace " + file.getName());
        }
        return backup != null ? backup : file;
    }

    private static void copy(@NonNull File source, @NonNull File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source); FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        }
    }

}
