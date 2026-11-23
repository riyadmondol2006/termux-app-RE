package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.List;

/**
 * A saved SSH connection. Passwords are never stored; authentication uses keys or interactive
 * prompts in the terminal session.
 *
 * <p>Includes minimal manual JSON serialization so the store stays unit testable on the JVM
 * without Android's {@code org.json} stubs.
 */
public final class SshConnection {

    public String label = "";
    public String user = "";
    public String host = "";
    /** TCP port, or 0 for the ssh default (22). */
    public int port;
    public String identityFile = "";
    public String jumpHost = "";
    /** Extra ssh config-style option lines, e.g. {@code "ServerAliveInterval 60"}. */
    public String extraOptions = "";
    /** Whether this connection is synced as a Host block in {@code ~/.ssh/config}. */
    public boolean syncToConfig = true;

    public SshConnection() {}

    public SshConnection(String label, String user, String host, int port,
                         String identityFile, String jumpHost, String extraOptions,
                         boolean syncToConfig) {
        this.label = label == null ? "" : label;
        this.user = user == null ? "" : user;
        this.host = host == null ? "" : host;
        this.port = port;
        this.identityFile = identityFile == null ? "" : identityFile;
        this.jumpHost = jumpHost == null ? "" : jumpHost;
        this.extraOptions = extraOptions == null ? "" : extraOptions;
        this.syncToConfig = syncToConfig;
    }

    /** Short one-line description, like {@code "user@example.com:2222"}. */
    public String describeTarget() {
        StringBuilder out = new StringBuilder();
        if (!user.isEmpty()) out.append(user).append('@');
        out.append(host);
        if (port > 0 && port != 22) out.append(':').append(port);
        return out.toString();
    }

    /** Serialize to a JSON object string. */
    public String toJson() {
        return "{\"label\":" + quote(label)
            + ",\"user\":" + quote(user)
            + ",\"host\":" + quote(host)
            + ",\"port\":" + port
            + ",\"identityFile\":" + quote(identityFile)
            + ",\"jumpHost\":" + quote(jumpHost)
            + ",\"extraOptions\":" + quote(extraOptions)
            + ",\"syncToConfig\":" + (syncToConfig ? "true" : "false") + "}";
    }

    /** Parse one connection from a JSON object string. Never throws: returns empty on failure. */
    public static SshConnection fromJson(String json) {
        SshConnection connection = new SshConnection();
        if (json == null) return connection;
        int[] pos = {0};
        skipWs(json, pos);
        if (!consume(json, pos, '{')) return connection;
        while (true) {
            skipWs(json, pos);
            if (consume(json, pos, '}')) break;
            String key = readString(json, pos);
            if (key == null) break;
            skipWs(json, pos);
            if (!consume(json, pos, ':')) break;
            skipWs(json, pos);
            String value = readValue(json, pos);
            if (value == null) break;
            switch (key) {
                case "label": connection.label = value; break;
                case "user": connection.user = value; break;
                case "host": connection.host = value; break;
                case "port":
                    try {
                        connection.port = Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        connection.port = 0;
                    }
                    break;
                case "identityFile": connection.identityFile = value; break;
                case "jumpHost": connection.jumpHost = value; break;
                case "extraOptions": connection.extraOptions = value; break;
                case "syncToConfig": connection.syncToConfig = value.equals("true"); break;
                default: break;
            }
            skipWs(json, pos);
            if (consume(json, pos, ',')) continue;
            if (consume(json, pos, '}')) break;
            break;
        }
        return connection;
    }

    /** Serialize a list to a JSON array string. */
    public static String listToJson(List<SshConnection> connections) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < connections.size(); i++) {
            if (i > 0) out.append(',');
            out.append(connections.get(i).toJson());
        }
        return out.append(']').toString();
    }

    /** Parse a JSON array string. Never throws: returns what could be parsed. */
    public static List<SshConnection> listFromJson(String json) {
        List<SshConnection> connections = new ArrayList<>();
        if (json == null) return connections;
        int[] pos = {0};
        skipWs(json, pos);
        if (!consume(json, pos, '[')) return connections;
        while (true) {
            skipWs(json, pos);
            if (consume(json, pos, ']')) break;
            if (pos[0] >= json.length() || json.charAt(pos[0]) != '{') break;
            int start = pos[0];
            if (readObject(json, pos) == null) break;
            connections.add(fromJson(json.substring(start, pos[0])));
            skipWs(json, pos);
            if (consume(json, pos, ',')) continue;
            if (consume(json, pos, ']')) break;
            break;
        }
        return connections;
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static void skipWs(String s, int[] pos) {
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos[0]++;
            else break;
        }
    }

    private static boolean consume(String s, int[] pos, char c) {
        if (pos[0] < s.length() && s.charAt(pos[0]) == c) {
            pos[0]++;
            return true;
        }
        return false;
    }

    /** Read a JSON string or literal (number/true/false) starting at pos; null on failure. */
    private static String readValue(String s, int[] pos) {
        if (pos[0] >= s.length()) return null;
        if (s.charAt(pos[0]) == '"') return readString(s, pos);
        int start = pos[0];
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
            pos[0]++;
        }
        return pos[0] > start ? s.substring(start, pos[0]) : null;
    }

    private static String readString(String s, int[] pos) {
        if (!consume(s, pos, '"')) return null;
        StringBuilder out = new StringBuilder();
        while (pos[0] < s.length()) {
            char c = s.charAt(pos[0]++);
            if (c == '"') return out.toString();
            if (c == '\\' && pos[0] < s.length()) {
                char e = s.charAt(pos[0]++);
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (pos[0] + 4 <= s.length()) {
                            try {
                                out.append((char) Integer.parseInt(s.substring(pos[0], pos[0] + 4), 16));
                                pos[0] += 4;
                            } catch (NumberFormatException ex) {
                                out.append('?');
                            }
                        }
                        break;
                    default: out.append(e); break;
                }
            } else {
                out.append(c);
            }
        }
        return null;
    }

    /** Advance pos past one balanced {...} object; null if unbalanced. */
    private static String readObject(String s, int[] pos) {
        int start = pos[0];
        boolean inString = false;
        int depth = 0;
        for (int i = pos[0]; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    pos[0] = i + 1;
                    return s.substring(start, pos[0]);
                }
            }
        }
        return null;
    }
}
