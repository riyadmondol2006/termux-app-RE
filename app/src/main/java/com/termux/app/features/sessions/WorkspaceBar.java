package com.termux.app.features.sessions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.termux.R;
import com.termux.app.TermuxActivity;

import java.util.List;

/**
 * The row of workspace chips above the sessions list in the drawer: "All", the workspaces and a
 * chip to create a workspace. Only the chip to create a workspace is shown until one is created.
 */
public final class WorkspaceBar {

    private final TermuxActivity mActivity;
    private final ChipGroup mChipGroup;
    /** The workspaces and selection the chips were created for, to only recreate them if changed. */
    private String mShownState;

    private WorkspaceBar(@NonNull TermuxActivity activity, @NonNull ChipGroup chipGroup) {
        mActivity = activity;
        mChipGroup = chipGroup;
    }

    /** Add the bar above {@code listView}, or return {@code null} if it can't be added. */
    @Nullable
    public static WorkspaceBar attach(@NonNull TermuxActivity activity, @Nullable ListView listView) {
        if (listView == null || !(listView.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) listView.getParent();

        View bar = parent.findViewById(R.id.feature_sessions_workspace_bar);
        if (bar == null) {
            bar = LayoutInflater.from(activity).inflate(R.layout.feature_sessions_workspace_bar, parent, false);
            parent.addView(bar, parent.indexOfChild(listView));
        }
        WorkspaceBar workspaceBar = new WorkspaceBar(activity, bar.findViewById(R.id.feature_sessions_workspace_chips));
        workspaceBar.update();
        return workspaceBar;
    }

    /** Update the chips for the current workspaces and selection. */
    public void update() {
        List<Workspace> workspaces = WorkspaceStore.getWorkspaces(mActivity);
        String selected = WorkspaceStore.getSelectedWorkspaceId(mActivity);

        StringBuilder state = new StringBuilder(String.valueOf(selected));
        for (Workspace workspace : workspaces) state.append('\n').append(workspace.id).append('=').append(workspace.name);
        if (state.toString().equals(mShownState)) return;
        mShownState = state.toString();

        mChipGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mChipGroup.getContext());
        boolean hasUserWorkspaces = workspaces.size() > 1;

        if (hasUserWorkspaces) {
            addWorkspaceChip(inflater, null, mActivity.getString(R.string.feature_sessions_workspace_all), selected == null);
            for (Workspace workspace : workspaces)
                addWorkspaceChip(inflater, workspace, workspace.name, workspace.id.equals(selected));
        }

        Chip addChip = (Chip) inflater.inflate(R.layout.feature_sessions_chip_add_workspace, mChipGroup, false);
        if (hasUserWorkspaces) {
            addChip.setText(null);
            addChip.setContentDescription(mActivity.getString(R.string.feature_sessions_action_new_workspace));
            addChip.setTextStartPadding(0);
            addChip.setTextEndPadding(0);
            addChip.setChipEndPadding(addChip.getChipStartPadding());
        }
        addChip.setOnClickListener(v -> WorkspaceDialogs.showCreateWorkspace(mActivity,
            workspace -> SessionWorkspaces.selectWorkspace(mActivity, workspace.id)));
        mChipGroup.addView(addChip);
    }

    private void addWorkspaceChip(@NonNull LayoutInflater inflater, @Nullable Workspace workspace,
                                  @NonNull String name, boolean checked) {
        Chip chip = (Chip) inflater.inflate(R.layout.feature_sessions_chip_workspace, mChipGroup, false);
        chip.setText(name);
        chip.setChecked(checked);
        String workspaceId = workspace == null ? null : workspace.id;
        chip.setOnClickListener(v -> {
            // Keep the selected chip checked, the selection is shown by update().
            mShownState = null;
            SessionWorkspaces.selectWorkspace(mActivity, workspaceId);
        });
        if (workspace != null) {
            chip.setOnLongClickListener(v -> {
                WorkspaceDialogs.showWorkspaceMenu(mActivity, workspace, false);
                return true;
            });
        }
        mChipGroup.addView(chip);
    }

}
