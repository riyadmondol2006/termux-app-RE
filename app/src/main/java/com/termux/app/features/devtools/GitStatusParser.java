package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code git status --porcelain=v2 -b} output.
 *
 * <p>Pure Java, no Android dependencies, so it is unit tested on the JVM.
 */
public final class GitStatusParser {

    private GitStatusParser() {}

    /** One changed path. */
    public static final class Entry {
        /** Raw two-letter status: X (index) + Y (worktree), e.g. {@code "M "}, {@code " M"}, {@code "??"}. */
        public final String xy;
        /** Path, relative to the repository root. */
        public final String path;
        /** Original path for renames/copies, or {@code null}. */
        public final String origPath;

        Entry(String xy, String path, String origPath) {
            this.xy = xy;
            this.path = path;
            this.origPath = origPath;
        }

        /** Whether the index (staged) side differs from HEAD. */
        public boolean isStaged() {
            return !xy.isEmpty() && xy.charAt(0) != '.' && xy.charAt(0) != ' ' && xy.charAt(0) != '?' && xy.charAt(0) != '!';
        }

        /** Whether the worktree (unstaged) side differs from the index. */
        public boolean isUnstaged() {
            if (xy.length() < 2) return false;
            char worktree = xy.charAt(1);
            return worktree != '.' && worktree != ' ' && worktree != '?' && worktree != '!';
        }

        public boolean isUntracked() {
            return xy.equals("??");
        }

        @Override
        public String toString() {
            return xy + " " + path + (origPath != null ? " <- " + origPath : "");
        }
    }

    /** Parsed status of one repository. */
    public static final class Status {
        /** Commit HEAD points at, or {@code "(initial)"} with no commits. */
        public String branchOid = "";
        /** Branch name, or {@code "(detached)"}. */
        public String branchHead = "";
        /** Upstream branch, or empty. */
        public String upstream = "";
        public int ahead;
        public int behind;
        public final List<Entry> entries = new ArrayList<>();

        public List<Entry> staged() {
            return filter(true);
        }

        public List<Entry> unstaged() {
            return filter(false);
        }

        private List<Entry> filter(boolean wantStaged) {
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries) {
                if (e.isUntracked()) continue;
                if (wantStaged ? e.isStaged() : e.isUnstaged()) out.add(e);
            }
            return out;
        }

        public List<Entry> untracked() {
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries) {
                if (e.isUntracked()) out.add(e);
            }
            return out;
        }

        /** Short branch description, like {@code "main ↑2 ↓1"} or {@code "main"}. */
        public String describeBranch() {
            StringBuilder out = new StringBuilder(branchHead);
            if (ahead > 0 || behind > 0) {
                out.append(" \u2191").append(ahead).append(" \u2193").append(behind);
            }
            return out.toString();
        }
    }

    /** Parse porcelain v2 output. Never throws: malformed lines are skipped. */
    public static Status parse(String output) {
        Status status = new Status();
        if (output == null || output.isEmpty()) return status;
        for (String rawLine : output.split("\n", -1)) {
            String line = stripCarriageReturn(rawLine);
            if (line.startsWith("# branch.oid ")) {
                status.branchOid = line.substring("# branch.oid ".length()).trim();
            } else if (line.startsWith("# branch.head ")) {
                status.branchHead = line.substring("# branch.head ".length()).trim();
            } else if (line.startsWith("# branch.upstream ")) {
                status.upstream = line.substring("# branch.upstream ".length()).trim();
            } else if (line.startsWith("# branch.ab ")) {
                int[] ab = parseAheadBehind(line.substring("# branch.ab ".length()).trim());
                status.ahead = ab[0];
                status.behind = ab[1];
            } else if (line.startsWith("1 ")) {
                // 1 XY subm <mH> <mI> <mW> <hH> <hI> <path>
                String[] fields = line.split(" ", 9);
                if (fields.length == 9 && fields[1].length() == 2) {
                    status.entries.add(new Entry(fields[1], unquotePath(fields[8]), null));
                }
            } else if (line.startsWith("2 ")) {
                // 2 XY subm <mH> <mI> <mW> <hH> <hI> <X><score> <path><TAB><origPath>
                String[] fields = line.split(" ", 10);
                if (fields.length == 10 && fields[1].length() == 2) {
                    String[] paths = fields[9].split("\t", -1);
                    String path = unquotePath(paths[0]);
                    String orig = paths.length > 1 ? unquotePath(paths[1]) : null;
                    status.entries.add(new Entry(fields[1], path, orig));
                }
            } else if ((line.startsWith("? ") || line.startsWith("! ")) && line.length() > 2) {
                status.entries.add(new Entry(line.substring(0, 1) + line.substring(0, 1), unquotePath(line.substring(2)), null));
            }
        }
        return status;
    }

    private static int[] parseAheadBehind(String value) {
        // Like "+2 -1".
        int ahead = 0;
        int behind = 0;
        for (String token : value.split("\\s+")) {
            try {
                if (token.startsWith("+")) ahead = Integer.parseInt(token.substring(1));
                else if (token.startsWith("-")) behind = Integer.parseInt(token.substring(1));
            } catch (NumberFormatException e) {
                // Keep what was parsed so far.
            }
        }
        return new int[]{ahead, behind};
    }

    private static String unquotePath(String path) {
        // Porcelain v2 C-quotes paths with special characters.
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            String inner = path.substring(1, path.length() - 1);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (c == '\\' && i + 1 < inner.length()) {
                    char e = inner.charAt(++i);
                    switch (e) {
                        case 'n': out.append('\n'); break;
                        case 't': out.append('\t'); break;
                        case '"': out.append('"'); break;
                        case '\\': out.append('\\'); break;
                        default: out.append(e); break;
                    }
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
        return path;
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
