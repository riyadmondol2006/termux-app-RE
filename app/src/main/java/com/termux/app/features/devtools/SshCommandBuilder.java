package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the shell commands run by the SSH connection manager. Arguments that carry user input
 * are always single-quoted; {@link #quote(String)} mirrors
 * {@code TermuxCommandRunner.shellQuote} but lives here so command building stays unit testable
 * on the JVM.
 */
public final class SshCommandBuilder {

    private SshCommandBuilder() {}

    /** Quote a value as a single POSIX shell word. */
    public static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Build the {@code ssh} command for a connection, e.g. {@code ssh -p 2222 user@host}. */
    public static String buildSshCommand(SshConnection connection) {
        StringBuilder out = new StringBuilder("ssh");
        if (connection.port > 0 && connection.port != 22) {
            out.append(" -p ").append(connection.port);
        }
        if (!connection.identityFile.isEmpty()) {
            out.append(" -i ").append(quote(connection.identityFile));
        }
        if (!connection.jumpHost.isEmpty()) {
            out.append(" -J ").append(quote(connection.jumpHost));
        }
        String extra = buildExtraArgs(connection.extraOptions);
        if (!extra.isEmpty()) out.append(' ').append(extra);
        // "--" ends the options, so a destination starting with "-" can't be taken as one.
        out.append(" -- ").append(quote(target(connection)));
        return out.toString();
    }

    /**
     * Whether {@code value} can be used as the user or host of a connection: not empty, no
     * whitespace or control characters, and not starting with "-" so it can't be mistaken for an
     * option like -oProxyCommand.
     */
    public static boolean isValidDestinationPart(String value) {
        if (value == null || value.isEmpty() || value.startsWith("-")) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        return true;
    }

    /**
     * Build an {@code ssh-copy-id}-style setup command that installs {@code publicKeyPath} on the
     * remote. Prefers {@code ssh-copy-id} when it exists, otherwise appends the key through
     * {@code ssh}. The public key itself is never embedded in the command; it is read from the
     * file at runtime.
     */
    public static String buildCopyIdCommand(SshConnection connection, String publicKeyPath) {
        String sshArgs = buildSshArgsOnly(connection);
        String remote = "'mkdir -p ~/.ssh && chmod 700 ~/.ssh"
            + " && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys'";
        // command -v keeps this working whether or not ssh-copy-id is installed.
        return "if command -v ssh-copy-id >/dev/null 2>&1; then ssh-copy-id"
            + sshArgs + " -i " + quote(publicKeyPath) + " -- " + quote(target(connection))
            + "; else ssh" + sshArgs + " -- " + quote(target(connection)) + " " + remote
            + " < " + quote(publicKeyPath) + "; fi";
    }

    private static String buildSshArgsOnly(SshConnection connection) {
        StringBuilder out = new StringBuilder();
        if (connection.port > 0 && connection.port != 22) {
            out.append(" -p ").append(connection.port);
        }
        if (!connection.identityFile.isEmpty()) {
            out.append(" -i ").append(quote(connection.identityFile));
        }
        if (!connection.jumpHost.isEmpty()) {
            out.append(" -J ").append(quote(connection.jumpHost));
        }
        String extra = buildExtraArgs(connection.extraOptions);
        if (!extra.isEmpty()) out.append(' ').append(extra);
        return out.toString();
    }

    private static String target(SshConnection connection) {
        String user = connection.user.isEmpty() ? "" : connection.user + "@";
        return user + connection.host;
    }

    /**
     * Extra ssh options are stored config-style (one {@code Keyword value} per line) but ssh
     * takes {@code -o Keyword=value}. Only single-line {@code Keyword value} pairs without shell
     * metacharacters in the keyword are passed through; anything else is dropped rather than
     * interpolated.
     */
    static String buildExtraArgs(String extraOptions) {
        if (extraOptions == null || extraOptions.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String rawLine : extraOptions.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[\\s=]+", 2);
            if (parts.length != 2) continue;
            String keyword = parts[0].trim();
            String value = parts[1].trim();
            if (!keyword.matches("[A-Za-z0-9]+") || value.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append("-o").append(quote(keyword + "=" + value));
        }
        return out.toString();
    }

    /** Build the {@code ssh-keygen} argv for generating an ed25519 key. */
    public static List<String> buildKeygenArgv(String keyPath, String passphrase, String comment) {
        List<String> argv = new ArrayList<>();
        argv.add("ssh-keygen");
        argv.add("-t");
        argv.add("ed25519");
        argv.add("-f");
        argv.add(keyPath);
        argv.add("-N");
        argv.add(passphrase == null ? "" : passphrase);
        if (comment != null && !comment.isEmpty()) {
            argv.add("-C");
            argv.add(comment);
        }
        return argv;
    }
}
