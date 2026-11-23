package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code git for-each-ref} output in the field-separated format used by the Git integration
 * feature: {@code %(refname:short)%x1f%(objectname:short)%x1f%(upstream:short)%x1f%(upstream:track)%x1f%(HEAD)}.
 *
 * <p>Pure Java, no Android dependencies, so it is unit tested on the JVM.
 */
public final class GitBranchParser {

    private GitBranchParser() {}

    private static final Pattern TRACK_PATTERN =
        Pattern.compile("(?:ahead (\\d+))?(?:, )?(?:behind (\\d+))?");

    /** One local branch. */
    public static final class Branch {
        public String name = "";
        public String objectShort = "";
        public String upstream = "";
        public int ahead;
        public int behind;
        public boolean gone;
        public boolean current;

        /** Short tracking description, like {@code "↑2 ↓1"}, {@code "[gone]"} or empty. */
        public String describeTracking() {
            if (gone) return "[gone]";
            if (upstream.isEmpty()) return "";
            if (ahead == 0 && behind == 0) return "";
            StringBuilder out = new StringBuilder();
            if (ahead > 0) out.append('\u2191').append(ahead);
            if (behind > 0) {
                if (out.length() > 0) out.append(' ');
                out.append('\u2193').append(behind);
            }
            return out.toString();
        }
    }

    /** Parse for-each-ref output. Never throws: malformed lines are skipped. */
    public static List<Branch> parse(String output) {
        List<Branch> branches = new ArrayList<>();
        if (output == null || output.isEmpty()) return branches;
        for (String rawLine : output.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.trim().isEmpty()) continue;
            String[] fields = line.split("\u001f", -1);
            if (fields.length < 5) continue;
            String name = fields[0].trim();
            if (name.isEmpty()) continue;
            Branch branch = new Branch();
            branch.name = name;
            branch.objectShort = fields[1].trim();
            branch.upstream = fields[2].trim();
            parseTrack(fields[3].trim(), branch);
            branch.current = fields[4].trim().equals("*");
            branches.add(branch);
        }
        return branches;
    }

    private static void parseTrack(String track, Branch branch) {
        if (track.isEmpty()) return;
        if (track.equals("gone")) {
            branch.gone = true;
            return;
        }
        Matcher matcher = TRACK_PATTERN.matcher(track);
        if (matcher.matches()) {
            if (matcher.group(1) != null) branch.ahead = parseInt(matcher.group(1));
            if (matcher.group(2) != null) branch.behind = parseInt(matcher.group(2));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
