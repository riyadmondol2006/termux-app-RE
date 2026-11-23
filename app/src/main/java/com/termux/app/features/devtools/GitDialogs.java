package com.termux.app.features.devtools;

import android.app.ProgressDialog;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxCommandRunner;
import com.termux.app.features.TermuxSessionLauncher;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dialog flows for the Git integration feature. Hosted by the TermuxActivity. */
public final class GitDialogs {

    private GitDialogs() {}

    /** Whether the git package (the {@code git} binary) is installed. */
    public static boolean isGitInstalled() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/git").canExecute();
    }

    /** Entry point: pick a repository, then open its dashboard. */
    public static void showRepoPicker(@NonNull TermuxActivity activity) {
        if (!isGitInstalled()) {
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feature_devtools_git_install_title)
                .setMessage(R.string.feature_devtools_git_install_message)
                .setPositiveButton(R.string.feature_devtools_git_install, (d, w) ->
                    TermuxSessionLauncher.runInNewSession(activity, "pkg", "pkg install git", false, null))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
            return;
        }

        String cwd = currentDirectory(activity);
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        options.add(activity.getString(R.string.feature_devtools_git_use_current) + "\n" + cwd);
        final String useCwd = cwd;
        actions.add(() -> openRepo(activity, useCwd));
        for (String repo : SshConnectionStore.recentRepos(activity)) {
            if (repo.equals(cwd) || !new File(repo).isDirectory()) continue;
            options.add(repo);
            final String remembered = repo;
            actions.add(() -> openRepo(activity, remembered));
        }

        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        LinearLayout buttons = SshManagerDialogs.buttonRow(activity);
        buttons.addView(SshManagerDialogs.smallButton(activity, R.string.feature_devtools_git_browse,
            v -> showBrowse(activity)));
        buttons.addView(SshManagerDialogs.smallButton(activity, R.string.feature_devtools_git_clone,
            v -> showClone(activity)));
        root.addView(buttons);
        ListView list = new ListView(activity);
        list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, options));
        list.setOnItemClickListener((parent, view, position, id) -> actions.get(position).run());
        root.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, SshManagerDialogs.dp(activity, 300)));

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_git_repo)
            .setView(root)
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showBrowse(@NonNull TermuxActivity activity) {
        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        EditText path = SshManagerDialogs.field(root, activity, R.string.feature_devtools_git_repo,
            currentDirectory(activity));
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_git_browse)
            .setView(root)
            .setPositiveButton(R.string.feature_devtools_ok,
                (d, w) -> openRepo(activity, path.getText().toString().trim()))
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showClone(@NonNull TermuxActivity activity) {
        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        EditText url = SshManagerDialogs.field(root, activity, R.string.feature_devtools_git_clone_url, "");
        EditText dir = SshManagerDialogs.field(root, activity, R.string.feature_devtools_git_clone_dir,
            TermuxConstants.TERMUX_HOME_DIR_PATH + "/");
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_git_clone)
            .setView(new ScrollView(activity) {{ addView(root); }})
            .setPositiveButton(R.string.feature_devtools_ok, (d, w) -> {
                String urlText = url.getText().toString().trim();
                String dirText = dir.getText().toString().trim();
                if (urlText.isEmpty() || dirText.isEmpty()) return;
                // Cloning needs credentials/prompts, so it runs visibly in a terminal session.
                String command = "git " + TermuxCommandRunner.shellQuote("clone")
                    + " " + TermuxCommandRunner.shellQuote(urlText)
                    + " " + TermuxCommandRunner.shellQuote(dirText);
                TermuxSessionLauncher.runInNewSession(activity, "git clone", command, true, null);
            })
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    /** Resolve the repo top level and open its dashboard, or report that it is not a repo. */
    private static void openRepo(@NonNull TermuxActivity activity, @NonNull String path) {
        if (path.isEmpty()) return;
        runGit(activity, GitCommands.toplevel(path), result -> {
            if (result.isSuccessful() && !result.stdout.trim().isEmpty()) {
                String repo = result.stdout.trim().split("\n")[0].trim();
                SshConnectionStore.rememberRepo(activity, repo);
                showDashboard(activity, repo);
            } else {
                Toast.makeText(activity,
                    activity.getString(R.string.feature_devtools_git_not_a_repo, path),
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void showDashboard(@NonNull TermuxActivity activity, @NonNull String repo) {
        runGit(activity, GitCommands.status(repo), result -> {
            GitStatusParser.Status status = GitStatusParser.parse(result.stdout);
            String title = repo + "\n" + status.describeBranch();
            String[] items = {
                activity.getString(R.string.feature_devtools_git_status),
                activity.getString(R.string.feature_devtools_git_branches),
                activity.getString(R.string.feature_devtools_git_log),
                activity.getString(R.string.feature_devtools_git_commit),
                activity.getString(R.string.feature_devtools_git_pull),
                activity.getString(R.string.feature_devtools_git_push),
                activity.getString(R.string.feature_devtools_git_fetch),
                activity.getString(R.string.feature_devtools_git_stash),
                activity.getString(R.string.feature_devtools_git_diff),
            };
            new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showStatus(activity, repo); break;
                        case 1: showBranches(activity, repo); break;
                        case 2: showLog(activity, repo); break;
                        case 3: showCommit(activity, repo); break;
                        case 4: runInSession(activity, repo, "git pull", "pull"); break;
                        case 5: runInSession(activity, repo, "git push", "push"); break;
                        case 6: runInSession(activity, repo, "git fetch", "fetch"); break;
                        case 7: showStash(activity, repo); break;
                        case 8: showDiff(activity, repo, false, null); break;
                        default: break;
                    }
                })
                .setPositiveButton(R.string.feature_devtools_git_refresh,
                    (d, w) -> showDashboard(activity, repo))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
        });
    }

    private static void showStatus(@NonNull TermuxActivity activity, @NonNull String repo) {
        runGit(activity, GitCommands.status(repo), result -> {
            GitStatusParser.Status status = GitStatusParser.parse(result.stdout);
            List<GitStatusParser.Entry> staged = status.staged();
            List<GitStatusParser.Entry> unstaged = status.unstaged();
            List<GitStatusParser.Entry> untracked = status.untracked();

            List<String> labels = new ArrayList<>();
            List<GitStatusParser.Entry> entries = new ArrayList<>();
            List<Boolean> isStagedRow = new ArrayList<>();
            for (GitStatusParser.Entry e : staged) {
                labels.add("[staged] " + e.path);
                entries.add(e);
                isStagedRow.add(true);
            }
            for (GitStatusParser.Entry e : unstaged) {
                labels.add("[unstaged] " + e.path);
                entries.add(e);
                isStagedRow.add(false);
            }
            for (GitStatusParser.Entry e : untracked) {
                labels.add("[untracked] " + e.path);
                entries.add(e);
                isStagedRow.add(false);
            }
            if (labels.isEmpty()) {
                Toast.makeText(activity, R.string.feature_devtools_git_no_changes, Toast.LENGTH_SHORT).show();
                return;
            }
            new MaterialAlertDialogBuilder(activity)
                .setTitle(status.describeBranch())
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    GitStatusParser.Entry entry = entries.get(which);
                    showEntryActions(activity, repo, entry, isStagedRow.get(which));
                })
                .setPositiveButton(R.string.feature_devtools_git_commit, (d, w) -> showCommit(activity, repo))
                .setNeutralButton(R.string.feature_devtools_git_refresh, (d, w) -> showStatus(activity, repo))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
        });
    }

    private static void showEntryActions(@NonNull TermuxActivity activity, @NonNull String repo,
                                         @NonNull GitStatusParser.Entry entry, boolean stagedRow) {
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (entry.isUntracked() || !stagedRow) {
            items.add(activity.getString(R.string.feature_devtools_git_stage));
            actions.add(() -> runGit(activity,
                GitCommands.stage(repo, Collections.singletonList(entry.path)),
                result -> toastResult(activity, result, () -> showStatus(activity, repo))));
        }
        if (stagedRow) {
            items.add(activity.getString(R.string.feature_devtools_git_unstage));
            actions.add(() -> runGit(activity,
                GitCommands.unstage(repo, Collections.singletonList(entry.path)),
                result -> toastResult(activity, result, () -> showStatus(activity, repo))));
        }
        items.add(activity.getString(R.string.feature_devtools_git_diff));
        actions.add(() -> showDiff(activity, repo, stagedRow, entry.path));
        new MaterialAlertDialogBuilder(activity)
            .setTitle(entry.path)
            .setItems(items.toArray(new String[0]),
                (d, which) -> actions.get(which).run())
            .show();
    }

    private static void showBranches(@NonNull TermuxActivity activity, @NonNull String repo) {
        runGit(activity, GitCommands.branches(repo), result -> {
            List<GitBranchParser.Branch> branches = GitBranchParser.parse(result.stdout);
            if (branches.isEmpty()) {
                Toast.makeText(activity, R.string.feature_devtools_git_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> labels = new ArrayList<>();
            for (GitBranchParser.Branch b : branches) {
                String label = (b.current ? "* " : "") + b.name;
                String tracking = b.describeTracking();
                if (!tracking.isEmpty()) label += "  " + tracking;
                else if (!b.upstream.isEmpty()) label += "  " + b.upstream;
                labels.add(label);
            }
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feature_devtools_git_branches)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    GitBranchParser.Branch branch = branches.get(which);
                    if (!branch.current) {
                        runGit(activity, GitCommands.switchBranch(repo, branch.name),
                            r -> toastResult(activity, r, () -> showDashboard(activity, repo)));
                    }
                })
                .setPositiveButton(R.string.feature_devtools_git_create_branch,
                    (d, w) -> showCreateBranch(activity, repo))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
        });
    }

    private static void showCreateBranch(@NonNull TermuxActivity activity, @NonNull String repo) {
        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        EditText name = SshManagerDialogs.field(root, activity, R.string.feature_devtools_git_branch_name, "");
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_git_create_branch)
            .setView(root)
            .setPositiveButton(R.string.feature_devtools_ok, (d, w) -> {
                String branch = name.getText().toString().trim();
                if (branch.isEmpty()) return;
                runGit(activity, GitCommands.createBranch(repo, branch),
                    result -> toastResult(activity, result, () -> showDashboard(activity, repo)));
            })
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showLog(@NonNull TermuxActivity activity, @NonNull String repo) {
        runGit(activity, GitCommands.log(repo, 50), result -> {
            List<GitLogParser.Commit> commits = GitLogParser.parse(result.stdout);
            if (commits.isEmpty()) {
                Toast.makeText(activity, R.string.feature_devtools_git_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> labels = new ArrayList<>();
            for (GitLogParser.Commit c : commits) {
                labels.add(c.shortHash() + "  " + c.subject + "\n" + c.author + "  " + c.date);
            }
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feature_devtools_git_log)
                .setItems(labels.toArray(new String[0]),
                    (d, which) -> showCommitDetail(activity, repo, commits.get(which)))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
        });
    }

    private static void showCommitDetail(@NonNull TermuxActivity activity, @NonNull String repo,
                                         @NonNull GitLogParser.Commit commit) {
        runGit(activity, GitCommands.showCommit(repo, commit.hash), result -> {
            StringBuilder text = new StringBuilder();
            text.append(commit.hash).append('\n');
            text.append(commit.author);
            if (!commit.email.isEmpty()) text.append(" <").append(commit.email).append('>');
            text.append('\n').append(commit.date).append("\n\n");
            text.append(commit.subject).append('\n');
            if (!commit.body.isEmpty()) text.append('\n').append(commit.body).append('\n');
            if (result.isSuccessful() && !result.stdout.isEmpty()) {
                text.append("\n---\n").append(result.stdout);
            }
            showScrollableText(activity, commit.shortHash() + "  " + commit.subject, text.toString());
        });
    }

    private static void showCommit(@NonNull TermuxActivity activity, @NonNull String repo) {
        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        EditText message = SshManagerDialogs.field(root, activity, R.string.feature_devtools_git_commit_message, "");
        message.setSingleLine(false);
        message.setMinLines(3);
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_devtools_git_commit)
            .setView(root)
            .setPositiveButton(R.string.feature_devtools_ok, (d, w) -> {
                String text = message.getText().toString().trim();
                if (text.isEmpty()) {
                    Toast.makeText(activity, R.string.feature_devtools_git_needs_message,
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                runGit(activity, GitCommands.commit(repo, text),
                    result -> toastResult(activity, result, () -> showDashboard(activity, repo)));
            })
            .setNegativeButton(R.string.feature_devtools_cancel, null)
            .show();
    }

    private static void showStash(@NonNull TermuxActivity activity, @NonNull String repo) {
        LinearLayout root = SshManagerDialogs.paddedColumn(activity);
        CheckBox untracked = new CheckBox(activity);
        untracked.setText(R.string.feature_devtools_git_include_untracked);
        root.addView(untracked);
        runGit(activity, GitCommands.stashList(repo), result -> {
            TextView existing = new TextView(activity);
            String list = result.stdout.trim();
            existing.setText(list.isEmpty() ? activity.getString(R.string.feature_devtools_git_empty) : list);
            existing.setPadding(0, SshManagerDialogs.dp(activity, 8), 0, 0);
            root.addView(existing);
            new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.feature_devtools_git_stash)
                .setView(new ScrollView(activity) {{ addView(root); }})
                .setPositiveButton(R.string.feature_devtools_git_stash, (d, w) ->
                    runGit(activity, GitCommands.stash(repo, untracked.isChecked()),
                        r -> toastResult(activity, r, () -> showDashboard(activity, repo))))
                .setNegativeButton(R.string.feature_devtools_cancel, null)
                .show();
        });
    }

    private static void showDiff(@NonNull TermuxActivity activity, @NonNull String repo,
                                 boolean staged, @Nullable String path) {
        runGit(activity, GitCommands.diff(repo, staged, path), result -> {
            String diff = result.stdout;
            if (diff.trim().isEmpty()) {
                Toast.makeText(activity, R.string.feature_devtools_git_no_changes, Toast.LENGTH_SHORT).show();
                return;
            }
            showScrollableText(activity,
                activity.getString(staged
                    ? R.string.feature_devtools_git_diff_staged
                    : R.string.feature_devtools_git_diff),
                diff);
        });
    }

    private static void showScrollableText(@NonNull TermuxActivity activity, @NonNull String title,
                                           @NonNull String text) {
        ScrollView scroll = new ScrollView(activity);
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTypeface(android.graphics.Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        int padding = SshManagerDialogs.dp(activity, 16);
        view.setPadding(padding, padding, padding, padding);
        scroll.addView(view);
        new MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.feature_devtools_ok, null)
            .show();
    }

    /** Network and credential operations run visibly in a terminal session so prompts work. */
    private static void runInSession(@NonNull TermuxActivity activity, @NonNull String repo,
                                     @NonNull String gitArgs, @NonNull String sessionName) {
        String command = "cd " + TermuxCommandRunner.shellQuote(repo) + " && " + gitArgs;
        TermuxSessionLauncher.runInNewSession(activity, "git " + sessionName, command, true, repo);
    }

    private static void runGit(@NonNull TermuxActivity activity, @NonNull List<String> argv,
                               @NonNull TermuxCommandRunner.Callback callback) {
        if (activity.isFinishing()) return;
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(activity.getString(R.string.feature_devtools_git_running));
        progress.setCancelable(false);
        progress.show();
        TermuxCommandRunner.run(activity, argv.toArray(new String[0]), null, null, 120000, result -> {
            try {
                progress.dismiss();
            } catch (IllegalArgumentException e) {
                // Activity went away while the command ran.
            }
            if (activity.isFinishing()) return;
            callback.onResult(result);
        });
    }

    private static void toastResult(@NonNull TermuxActivity activity,
                                    @NonNull TermuxCommandRunner.Result result,
                                    @Nullable Runnable onSuccess) {
        if (result.isSuccessful()) {
            Toast.makeText(activity, R.string.feature_devtools_git_done, Toast.LENGTH_SHORT).show();
            if (onSuccess != null) onSuccess.run();
        } else {
            String error = result.error != null ? result.error : result.stderr.trim();
            if (error.isEmpty()) error = result.stdout.trim();
            Toast.makeText(activity,
                activity.getString(R.string.feature_devtools_git_failed, error),
                Toast.LENGTH_LONG).show();
        }
    }

    @NonNull
    private static String currentDirectory(@NonNull TermuxActivity activity) {
        TerminalSession session = activity.getCurrentSession();
        String cwd = session != null ? session.getCwd() : null;
        if (cwd == null || cwd.isEmpty()) cwd = TermuxConstants.TERMUX_HOME_DIR_PATH;
        return cwd;
    }
}
