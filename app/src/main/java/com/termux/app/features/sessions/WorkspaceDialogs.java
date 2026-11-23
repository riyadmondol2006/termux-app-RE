package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.core.view.GravityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/** Dialogs to create, rename, delete and switch workspaces and to move sessions between them. */
public final class WorkspaceDialogs {

    private WorkspaceDialogs() {}

    public static void showCreateWorkspace(@NonNull TermuxActivity activity, @Nullable Consumer<Workspace> onCreated) {
        TextInputDialogUtils.textInput(activity, R.string.feature_sessions_title_new_workspace, null,
            R.string.feature_sessions_action_create, text -> {
                if (text == null || text.trim().isEmpty()) return;
                Workspace workspace = WorkspaceStore.createWorkspace(activity, text);
                activity.termuxSessionListNotifyUpdated();
                if (onCreated != null) onCreated.accept(workspace);
            }, -1, null, -1, null, null);
    }

    public static void showRenameWorkspace(@NonNull TermuxActivity activity, @NonNull Workspace workspace) {
        TextInputDialogUtils.textInput(activity, R.string.feature_sessions_title_rename_workspace, workspace.name,
            R.string.feature_sessions_action_rename, text -> {
                if (text == null || text.trim().isEmpty()) return;
                WorkspaceStore.renameWorkspace(activity, workspace.id, text);
                activity.termuxSessionListNotifyUpdated();
            }, -1, null, -1, null, null);
    }

    public static void showDeleteWorkspace(@NonNull TermuxActivity activity, @NonNull Workspace workspace) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.feature_sessions_title_delete_workspace, workspace.name))
            .setMessage(R.string.feature_sessions_msg_delete_workspace)
            .setPositiveButton(R.string.feature_sessions_action_delete, (dialog, which) -> {
                WorkspaceStore.deleteWorkspace(activity, workspace.id);
                activity.termuxSessionListNotifyUpdated();
                SessionPersistence.scheduleSave(activity.getTermuxService());
                activity.getTermuxTerminalSessionClient().checkAndScrollToSession(activity.getCurrentSession());
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Show the actions for a workspace, like when its chip in the drawer is long pressed. */
    public static void showWorkspaceMenu(@NonNull TermuxActivity activity, @NonNull Workspace workspace, boolean showSwitch) {
        List<CharSequence> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (showSwitch) {
            items.add(activity.getString(R.string.feature_sessions_action_switch_to_workspace));
            actions.add(() -> {
                SessionWorkspaces.selectWorkspace(activity, workspace.id);
                activity.getDrawer().openDrawer(GravityCompat.START);
            });
        }
        items.add(activity.getString(R.string.feature_sessions_action_new_session_in_workspace));
        actions.add(() -> {
            SessionWorkspaces.selectWorkspace(activity, workspace.id);
            activity.getTermuxTerminalSessionClient().addNewSession(false, null);
        });
        if (!workspace.isDefault()) {
            items.add(activity.getString(R.string.feature_sessions_action_rename_workspace));
            actions.add(() -> showRenameWorkspace(activity, workspace));
            items.add(activity.getString(R.string.feature_sessions_action_delete_workspace));
            actions.add(() -> showDeleteWorkspace(activity, workspace));
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(workspace.name)
            .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> actions.get(which).run())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Show the workspaces with their number of sessions, to switch to or manage them. */
    public static void showWorkspaces(@NonNull TermuxActivity activity) {
        List<Workspace> workspaces = WorkspaceStore.getWorkspaces(activity);
        TermuxService service = activity.getTermuxService();
        String selected = WorkspaceStore.getSelectedWorkspaceId(activity);

        List<CharSequence> items = new ArrayList<>();
        for (Workspace workspace : workspaces) {
            int count = 0;
            if (service != null) {
                for (TermuxSession session : service.getTermuxSessions()) {
                    if (SessionWorkspaces.getWorkspaceId(activity, session.getTerminalSession()).equals(workspace.id)) count++;
                }
            }
            String item = activity.getResources().getQuantityString(R.plurals.feature_sessions_workspace_item, count, workspace.name, count);
            if (workspace.id.equals(selected)) item = activity.getString(R.string.feature_sessions_workspace_item_selected, item);
            items.add(item);
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_sessions_title_workspaces)
            .setItems(items.toArray(new CharSequence[0]), (dialog, which) -> showWorkspaceMenu(activity, workspaces.get(which), true))
            .setPositiveButton(R.string.feature_sessions_action_new_workspace, (dialog, which) ->
                showCreateWorkspace(activity, workspace -> SessionWorkspaces.selectWorkspace(activity, workspace.id)))
            .setNeutralButton(R.string.feature_sessions_action_show_all, (dialog, which) -> {
                SessionWorkspaces.selectWorkspace(activity, null);
                activity.getDrawer().openDrawer(GravityCompat.START);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Show the workspaces to move {@code session} to. */
    public static void showMoveSession(@NonNull TermuxActivity activity, @NonNull TerminalSession session) {
        List<Workspace> workspaces = WorkspaceStore.getWorkspaces(activity);
        String current = SessionWorkspaces.getWorkspaceId(activity, session);
        CharSequence[] items = new CharSequence[workspaces.size()];
        int checked = 0;
        for (int i = 0; i < workspaces.size(); i++) {
            items[i] = workspaces.get(i).name;
            if (workspaces.get(i).id.equals(current)) checked = i;
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_sessions_title_move_session)
            .setSingleChoiceItems(items, checked, (dialog, which) -> {
                dialog.dismiss();
                SessionWorkspaces.moveSession(activity, session, workspaces.get(which).id);
            })
            .setPositiveButton(R.string.feature_sessions_action_new_workspace, (dialog, which) ->
                showCreateWorkspace(activity, workspace -> SessionWorkspaces.moveSession(activity, session, workspace.id)))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

}
