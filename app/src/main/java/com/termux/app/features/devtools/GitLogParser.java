package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code git log} output in the field/record separated format used by the Git integration
 * feature: fields joined by {@code \x1f} (unit separator), records ended by {@code \x1e}
 * (record separator).
 *
 * <p>The expected format string is {@code %H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%s%x1f%b%x1e}, so the
 * fields are: hash, parents, author name, author email, author date (ISO 8601), subject, body.
 *
 * <p>Pure Java, no Android dependencies, so it is unit tested on the JVM.
 */
public final class GitLogParser {

    private GitLogParser() {}

    /** One commit. */
    public static final class Commit {
        public String hash = "";
        public String parents = "";
        public String author = "";
        public String email = "";
        public String date = "";
        public String subject = "";
        public String body = "";

        /** First 7 characters of the hash, for display. */
        public String shortHash() {
            return hash.length() > 7 ? hash.substring(0, 7) : hash;
        }
    }

    /** Parse log output. Never throws: malformed records are skipped. */
    public static List<Commit> parse(String output) {
        List<Commit> commits = new ArrayList<>();
        if (output == null || output.isEmpty()) return commits;
        for (String record : output.split("\u001e", -1)) {
            if (record.trim().isEmpty()) continue;
            // A leading newline before %H of the next record belongs to the separator.
            if (record.startsWith("\n")) record = record.substring(1);
            if (record.startsWith("\r")) record = record.substring(1);
            if (record.trim().isEmpty()) continue;
            String[] fields = record.split("\u001f", -1);
            if (fields.length < 6) continue;
            Commit commit = new Commit();
            commit.hash = fields[0].trim();
            if (commit.hash.isEmpty()) continue;
            commit.parents = fields[1].trim();
            commit.author = fields[2];
            commit.email = fields[3];
            commit.date = fields[4];
            commit.subject = fields[5].trim();
            commit.body = fields.length > 6 ? fields[6].trim() : "";
            commits.add(commit);
        }
        return commits;
    }
}
