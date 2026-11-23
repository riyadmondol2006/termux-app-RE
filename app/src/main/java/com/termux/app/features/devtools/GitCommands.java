package com.termux.app.features.devtools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the {@code git} argv arrays run by the Git integration feature. Read-only queries run
 * in the background via {@code TermuxCommandRunner}; network and credential operations run
 * visibly in a terminal session.
 *
 * <p>Pure Java, no Android dependencies, so it is unit tested on the JVM.
 */
public final class GitCommands {

    /** Log format matching {@link GitLogParser}: fields joined by {@code \x1f}, records by {@code \x1e}. */
    public static final String LOG_FORMAT = "%H%x1f%P%x1f%an%x1f%ae%x1f%aI%x1f%s%x1f%b%x1e";

    /** For-each-ref format matching {@link GitBranchParser}. */
    public static final String BRANCH_FORMAT =
        "%(refname:short)%x1f%(objectname:short)%x1f%(upstream:short)%x1f%(upstream:track)%x1f%(HEAD)";

    private GitCommands() {}

    public static List<String> status(String repo) {
        return Arrays.asList("git", "-C", repo, "status", "--porcelain=v2", "-b");
    }

    public static List<String> log(String repo, int limit) {
        return Arrays.asList("git", "-C", repo, "log", "--format=" + LOG_FORMAT, "-n", String.valueOf(limit));
    }

    public static List<String> branches(String repo) {
        return Arrays.asList("git", "-C", repo, "for-each-ref",
            "--format=" + BRANCH_FORMAT, "refs/heads");
    }

    public static List<String> currentBranch(String repo) {
        return Arrays.asList("git", "-C", repo, "rev-parse", "--abbrev-ref", "HEAD");
    }

    public static List<String> isRepo(String repo) {
        return Arrays.asList("git", "-C", repo, "rev-parse", "--git-dir");
    }

    public static List<String> toplevel(String repo) {
        return Arrays.asList("git", "-C", repo, "rev-parse", "--show-toplevel");
    }

    public static List<String> stage(String repo, List<String> paths) {
        List<String> argv = new ArrayList<>(Arrays.asList("git", "-C", repo, "add", "--"));
        argv.addAll(paths);
        return argv;
    }

    public static List<String> unstage(String repo, List<String> paths) {
        List<String> argv = new ArrayList<>(Arrays.asList("git", "-C", repo, "restore", "--staged", "--"));
        argv.addAll(paths);
        return argv;
    }

    public static List<String> commit(String repo, String message) {
        return Arrays.asList("git", "-C", repo, "commit", "-m", message);
    }

    public static List<String> stash(String repo, boolean includeUntracked) {
        List<String> argv = new ArrayList<>(Arrays.asList("git", "-C", repo, "stash", "push", "-m"));
        argv.add("termux-re stash");
        if (includeUntracked) argv.add("-u");
        return argv;
    }

    public static List<String> stashList(String repo) {
        return Arrays.asList("git", "-C", repo, "stash", "list");
    }

    public static List<String> diff(String repo, boolean staged, String path) {
        List<String> argv = new ArrayList<>(Arrays.asList("git", "-C", repo, "diff", "--no-color"));
        if (staged) argv.add("--cached");
        argv.add("--");
        if (path != null && !path.isEmpty()) argv.add(path);
        return argv;
    }

    public static List<String> showCommit(String repo, String hash) {
        return Arrays.asList("git", "-C", repo, "show", "--no-color", "--stat", hash);
    }

    public static List<String> switchBranch(String repo, String branch) {
        return Arrays.asList("git", "-C", repo, "switch", branch);
    }

    public static List<String> createBranch(String repo, String branch) {
        return Arrays.asList("git", "-C", repo, "switch", "-c", branch);
    }

    /** Wrap argv for display in a session; every argument is safely quoted. */
    public static String toShellCommand(List<String> argv) {
        StringBuilder out = new StringBuilder();
        for (String arg : argv) {
            if (out.length() > 0) out.append(' ');
            out.append(SshCommandBuilder.quote(arg));
        }
        return out.toString();
    }
}
