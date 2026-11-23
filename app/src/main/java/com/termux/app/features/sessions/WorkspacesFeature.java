package com.termux.app.features.sessions;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;

/** Tools entry to switch, create, rename and delete session workspaces. */
public final class WorkspacesFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "session_workspaces";
    }

    @Override
    public int getTitle() {
        return R.string.feature_sessions_workspaces_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_sessions_workspaces_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_sessions_ic_workspaces;
    }

    @Override
    public boolean isAvailable(@NonNull TermuxActivity activity) {
        return activity.getTermuxService() != null;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        WorkspaceDialogs.showWorkspaces(activity);
    }

}
