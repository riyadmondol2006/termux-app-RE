package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses OpenSSH client config files ({@code ~/.ssh/config}) and renders the managed block that
 * the SSH connection manager syncs.
 *
 * <p>Pure Java, no Android dependencies, so it is unit tested on the JVM.
 */
public final class SshConfigCodec {

    /** First line of the block managed by the SSH connection manager. */
    public static final String MANAGED_BEGIN = "# >>> termux-re ssh manager (managed entries, do not edit) >>>";
    /** Last line of the block managed by the SSH connection manager. */
    public static final String MANAGED_END = "# <<< termux-re ssh manager <<<";

    private SshConfigCodec() {}

    /** One {@code Host} block of an ssh config file. */
    public static final class HostEntry {
        /** The patterns from the {@code Host} line, in order. */
        public final List<String> patterns = new ArrayList<>();
        /** Option keyword (lower-cased) to raw value, in file order. */
        public final LinkedHashMap<String, String> options = new LinkedHashMap<>();

        /** The value of an option, or {@code null}. The keyword match is case-insensitive. */
        public String getOption(String keyword) {
            return options.get(keyword.toLowerCase(java.util.Locale.US));
        }
    }

    /**
     * Parse the {@code Host} blocks of {@code text}. {@code Match} blocks end host-block parsing
     * until the next {@code Host} line; global options before the first {@code Host} line are
     * ignored. Never throws: malformed lines are skipped.
     */
    public static List<HostEntry> parse(String text) {
        List<HostEntry> entries = new ArrayList<>();
        if (text == null || text.isEmpty()) return entries;
        HostEntry current = null;
        boolean inMatch = false;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String keyword;
            String value;
            int eq = line.indexOf('=');
            int sp = indexOfWhitespace(line);
            if (eq != -1 && (sp == -1 || eq < sp)) {
                keyword = line.substring(0, eq).trim();
                value = line.substring(eq + 1).trim();
            } else if (sp != -1) {
                keyword = line.substring(0, sp);
                value = line.substring(sp + 1).trim();
            } else {
                continue;
            }
            if (keyword.equalsIgnoreCase("Host")) {
                inMatch = false;
                current = new HostEntry();
                for (String pattern : value.split("\\s+")) {
                    if (!pattern.isEmpty()) current.patterns.add(pattern);
                }
                if (!current.patterns.isEmpty()) entries.add(current);
                else current = null;
            } else if (keyword.equalsIgnoreCase("Match")) {
                current = null;
                inMatch = true;
            } else if (current != null && !inMatch && !value.isEmpty()) {
                current.options.putIfAbsent(keyword.toLowerCase(java.util.Locale.US), stripQuotes(value));
            }
        }
        return entries;
    }

    /**
     * Convert parsed {@code Host} entries to connections. Entries whose first pattern contains a
     * wildcard ({@code *?!}) or that have no usable host are skipped.
     */
    public static List<SshConnection> toConnections(List<HostEntry> entries) {
        List<SshConnection> connections = new ArrayList<>();
        for (HostEntry entry : entries) {
            if (entry.patterns.isEmpty()) continue;
            String alias = entry.patterns.get(0);
            if (alias.contains("*") || alias.contains("?") || alias.contains("!")) continue;
            String hostName = entry.getOption("hostname");
            if (hostName == null || hostName.isEmpty()) hostName = alias;
            SshConnection connection = new SshConnection();
            connection.label = alias;
            connection.host = hostName;
            connection.user = orEmpty(entry.getOption("user"));
            connection.port = parsePort(entry.getOption("port"));
            connection.identityFile = firstToken(entry.getOption("identityfile"));
            connection.jumpHost = orEmpty(entry.getOption("proxyjump"));
            connection.syncToConfig = true;
            connections.add(connection);
        }
        return connections;
    }

    /** Render the managed block (including the marker lines) for the synced connections. */
    public static String renderManagedBlock(List<SshConnection> connections) {
        StringBuilder out = new StringBuilder();
        out.append(MANAGED_BEGIN).append('\n');
        boolean any = false;
        for (SshConnection c : connections) {
            if (!c.syncToConfig) continue;
            if (c.label == null || c.label.isEmpty() || c.host == null || c.host.isEmpty()) continue;
            any = true;
            out.append("Host ").append(c.label).append('\n');
            out.append("    HostName ").append(c.host).append('\n');
            if (!c.user.isEmpty()) out.append("    User ").append(c.user).append('\n');
            if (c.port > 0 && c.port != 22) out.append("    Port ").append(c.port).append('\n');
            if (!c.identityFile.isEmpty()) out.append("    IdentityFile ").append(c.identityFile).append('\n');
            if (!c.jumpHost.isEmpty()) out.append("    ProxyJump ").append(c.jumpHost).append('\n');
            if (!c.extraOptions.isEmpty()) {
                for (String optionLine : c.extraOptions.split("\n")) {
                    String trimmed = optionLine.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) out.append("    ").append(trimmed).append('\n');
                }
            }
        }
        if (!any) out.append("# (no synced connections)\n");
        out.append(MANAGED_END).append('\n');
        return out.toString();
    }

    /**
     * Replace the managed block in {@code existing} config text with the block rendered from
     * {@code connections}, or append it when there is none. All text outside the markers is left
     * untouched, so manual edits are never clobbered.
     */
    public static String spliceManagedBlock(String existing, List<SshConnection> connections) {
        String block = renderManagedBlock(connections);
        if (existing == null || existing.isEmpty()) return block;
        int begin = existing.indexOf(MANAGED_BEGIN);
        int end = existing.indexOf(MANAGED_END);
        if (begin == -1 || end == -1 || end < begin) {
            String trimmed = existing.endsWith("\n") ? existing : existing + "\n";
            return trimmed + "\n" + block;
        }
        int endOfLine = existing.indexOf('\n', end);
        String after = endOfLine == -1 ? "" : existing.substring(endOfLine + 1);
        String before = existing.substring(0, begin);
        if (!before.isEmpty() && !before.endsWith("\n")) before += "\n";
        if (!after.isEmpty() && !before.endsWith("\n")) before += "\n";
        String result = before + block + after;
        if (!result.endsWith("\n")) result += "\n";
        return result;
    }

    private static int indexOfWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t') return i;
        }
        return -1;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String firstToken(String value) {
        if (value == null) return "";
        String trimmed = stripQuotes(value.trim());
        int sp = indexOfWhitespace(trimmed);
        return sp == -1 ? trimmed : trimmed.substring(0, sp);
    }

    private static int parsePort(String value) {
        if (value == null) return 0;
        try {
            int port = Integer.parseInt(value.trim());
            return port > 0 && port <= 65535 ? port : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Get an unmodifiable view hook for callers that need the map type; kept for tests. */
    static Map<String, String> optionsOf(HostEntry entry) {
        return Collections.unmodifiableMap(entry.options);
    }
}
