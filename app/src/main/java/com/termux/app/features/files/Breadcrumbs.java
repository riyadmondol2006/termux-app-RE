package com.termux.app.features.files;

import java.util.ArrayList;
import java.util.List;

/** Pure path handling for the path bar and for Back in the file manager. */
final class Breadcrumbs {
    private Breadcrumbs() {}

    /** One tappable part of the path bar. */
    static final class Crumb {
        final String label, path;
        Crumb(String label, String path) { this.label = label; this.path = path; }
        @Override public boolean equals(Object o) {
            return o instanceof Crumb && ((Crumb) o).label.equals(label) && ((Crumb) o).path.equals(path);
        }
        @Override public int hashCode() { return label.hashCode() * 31 + path.hashCode(); }
        @Override public String toString() { return label + "=" + path; }
    }

    /**
     * Splits {@code path} into crumbs. Paths in the home folder start with "~" and paths in the
     * installed packages folder with "$PREFIX", because the full path is long and the same for everything else.
     * All other paths start with "/".
     */
    static List<Crumb> parse(String path, String home, String prefix) {
        path = trim(path);
        List<Crumb> crumbs = new ArrayList<>();
        String base;
        if (inside(path, home)) { base = trim(home); crumbs.add(new Crumb("~", base)); }
        else if (inside(path, prefix)) { base = trim(prefix); crumbs.add(new Crumb("$PREFIX", base)); }
        else { base = ""; crumbs.add(new Crumb("/", "/")); }
        String rest = path.substring(base.length());
        StringBuilder current = new StringBuilder(base);
        for (String part : rest.split("/")) {
            if (part.isEmpty()) continue;
            current.append('/').append(part);
            crumbs.add(new Crumb(part, current.toString()));
        }
        return crumbs;
    }

    /** Whether Back must leave the file manager instead of going up, because {@code path} is the top of a location. */
    static boolean isTop(String path, List<String> roots) {
        path = trim(path);
        if (path.equals("/")) return true;
        for (String root : roots) if (path.equals(trim(root))) return true;
        return false;
    }

    private static boolean inside(String path, String base) {
        base = trim(base);
        return path.equals(base) || path.startsWith(base + "/");
    }

    private static String trim(String path) {
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }
}
