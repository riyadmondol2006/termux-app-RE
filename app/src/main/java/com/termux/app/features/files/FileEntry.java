package com.termux.app.features.files;

import java.io.File;

/** One row of the file manager, with everything read from disk up front so the list can be sorted and filtered off the UI thread. */
final class FileEntry {
    /** Used for {@link #children} when the entry is not a folder or its content cannot be read. */
    static final int UNKNOWN = -1;

    final File file;
    final String name;
    final boolean directory;
    final boolean link;
    final long size;
    final long modified;
    final int children;

    FileEntry(File file, boolean directory, boolean link, long size, long modified, int children) {
        this.file = file;
        this.name = file.getName();
        this.directory = directory;
        this.link = link;
        this.size = size;
        this.modified = modified;
        this.children = children;
    }

    String path() { return file.getPath(); }
}
