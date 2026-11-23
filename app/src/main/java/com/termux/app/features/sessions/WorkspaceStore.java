package com.termux.app.features.sessions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The workspaces created by the user and the workspace selected in the drawer, stored in
 * {@link SessionsPreferences}. The default workspace always exists and is listed first.
 */
public final class WorkspaceStore {

    private static final String LOG_TAG = "WorkspaceStore";

    /** The user workspaces, loaded on first use. */
    private static List<Workspace> sUserWorkspaces;

    private WorkspaceStore() {}

    /** Get all workspaces, starting with the default one. */
    @NonNull
    public static synchronized List<Workspace> getWorkspaces(@NonNull Context context) {
        List<Workspace> workspaces = new ArrayList<>();
        workspaces.add(new Workspace(Workspace.DEFAULT_ID, context.getString(R.string.feature_sessions_workspace_default)));
        workspaces.addAll(getUserWorkspaces(context));
        return workspaces;
    }

    /** Get the workspaces created by the user. */
    @NonNull
    public static synchronized List<Workspace> getUserWorkspaces(@NonNull Context context) {
        if (sUserWorkspaces == null) sUserWorkspaces = load(context);
        return Collections.unmodifiableList(sUserWorkspaces);
    }

    public static boolean hasUserWorkspaces(@NonNull Context context) {
        return !getUserWorkspaces(context).isEmpty();
    }

    @Nullable
    public static synchronized Workspace getWorkspace(@NonNull Context context, @Nullable String id) {
        if (id == null) return null;
        for (Workspace workspace : getWorkspaces(context)) {
            if (workspace.id.equals(id)) return workspace;
        }
        return null;
    }

    /** Get the name of the workspace with {@code id}, or of the default workspace if it does not exist. */
    @NonNull
    public static String getWorkspaceName(@NonNull Context context, @Nullable String id) {
        Workspace workspace = getWorkspace(context, id);
        return workspace != null ? workspace.name : context.getString(R.string.feature_sessions_workspace_default);
    }

    @NonNull
    public static synchronized Workspace createWorkspace(@NonNull Context context, @NonNull String name) {
        getUserWorkspaces(context);
        Workspace workspace = new Workspace(UUID.randomUUID().toString(), name.trim());
        sUserWorkspaces.add(workspace);
        save(context);
        return workspace;
    }

    public static synchronized void renameWorkspace(@NonNull Context context, @NonNull String id, @NonNull String name) {
        getUserWorkspaces(context);
        for (int i = 0; i < sUserWorkspaces.size(); i++) {
            if (sUserWorkspaces.get(i).id.equals(id)) {
                sUserWorkspaces.set(i, new Workspace(id, name.trim()));
                save(context);
                return;
            }
        }
    }

    /** Delete the workspace with {@code id}. Its sessions are moved to the default workspace. */
    public static synchronized void deleteWorkspace(@NonNull Context context, @NonNull String id) {
        getUserWorkspaces(context);
        for (int i = 0; i < sUserWorkspaces.size(); i++) {
            if (sUserWorkspaces.get(i).id.equals(id)) {
                sUserWorkspaces.remove(i);
                save(context);
                break;
            }
        }
        SessionRegistry.moveSessionsToDefaultWorkspace(id);
        if (id.equals(getSelectedWorkspaceId(context))) setSelectedWorkspaceId(context, null);
    }

    /** Get the id of the workspace selected in the drawer, or {@code null} if all workspaces are shown. */
    @Nullable
    public static String getSelectedWorkspaceId(@NonNull Context context) {
        String id = SessionsPreferences.get(context).getString(SessionsPreferences.KEY_WORKSPACE_FILTER, null);
        // A workspace that has been deleted.
        if (id != null && getWorkspace(context, id) == null) return null;
        return id;
    }

    public static void setSelectedWorkspaceId(@NonNull Context context, @Nullable String id) {
        SessionsPreferences.get(context).edit().putString(SessionsPreferences.KEY_WORKSPACE_FILTER, id).apply();
    }

    @NonNull
    private static List<Workspace> load(@NonNull Context context) {
        List<Workspace> workspaces = new ArrayList<>();
        String json = SessionsPreferences.get(context).getString(SessionsPreferences.KEY_WORKSPACES, null);
        if (json == null) return workspaces;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String id = object.optString("id", "");
                String name = object.optString("name", "");
                if (!id.isEmpty() && !id.equals(Workspace.DEFAULT_ID)) workspaces.add(new Workspace(id, name));
            }
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load workspaces", e);
        }
        return workspaces;
    }

    private static void save(@NonNull Context context) {
        JSONArray array = new JSONArray();
        try {
            for (Workspace workspace : sUserWorkspaces) {
                array.put(new JSONObject().put("id", workspace.id).put("name", workspace.name));
            }
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save workspaces", e);
            return;
        }
        SessionsPreferences.get(context).edit().putString(SessionsPreferences.KEY_WORKSPACES, array.toString()).apply();
    }

}
