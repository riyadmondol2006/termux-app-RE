package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The saved terminal sessions, see {@link SessionPersistence}. */
public final class SessionSnapshot {

    public static final int VERSION = 1;

    /** A saved session. */
    public static final class Session {
        /** The handle of the session when it was saved, which names its transcript file. */
        @NonNull public final String handle;
        @Nullable public String name;
        @NonNull public String workspaceId = Workspace.DEFAULT_ID;
        public boolean pinned;
        @Nullable public String profileId;
        @Nullable public String workingDirectory;
        /** If a transcript was saved for the session. */
        public boolean hasTranscript;

        public Session(@NonNull String handle) {
            this.handle = handle;
        }
    }

    /** The time the snapshot was saved, in milliseconds since the epoch. */
    public long time;
    /** The handle of the session that was the current one. */
    @Nullable public String currentHandle;
    @NonNull public final List<Session> sessions = new ArrayList<>();

    @NonNull
    public String toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (Session session : sessions) {
            JSONObject object = new JSONObject()
                .put("handle", session.handle)
                .put("workspace", session.workspaceId)
                .put("pinned", session.pinned)
                .put("transcript", session.hasTranscript);
            if (session.name != null) object.put("name", session.name);
            if (session.profileId != null) object.put("profile", session.profileId);
            if (session.workingDirectory != null) object.put("cwd", session.workingDirectory);
            array.put(object);
        }
        JSONObject root = new JSONObject().put("version", VERSION).put("time", time).put("sessions", array);
        if (currentHandle != null) root.put("current", currentHandle);
        return root.toString();
    }

    /** Parse a snapshot saved with {@link #toJson()}, or return {@code null} if it is invalid. */
    @Nullable
    public static SessionSnapshot fromJson(@Nullable String json) {
        if (json == null) return null;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version", 0) != VERSION) return null;
            SessionSnapshot snapshot = new SessionSnapshot();
            snapshot.time = root.optLong("time", 0);
            snapshot.currentHandle = optString(root, "current");
            JSONArray array = root.optJSONArray("sessions");
            if (array == null) return snapshot;
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String handle = optString(object, "handle");
                if (handle == null || !isValidHandle(handle)) continue;
                Session session = new Session(handle);
                session.name = optString(object, "name");
                String workspaceId = optString(object, "workspace");
                if (workspaceId != null) session.workspaceId = workspaceId;
                session.pinned = object.optBoolean("pinned", false);
                session.profileId = optString(object, "profile");
                session.workingDirectory = optString(object, "cwd");
                session.hasTranscript = object.optBoolean("transcript", false);
                snapshot.sessions.add(session);
            }
            return snapshot;
        } catch (JSONException e) {
            return null;
        }
    }

    /** Session handles name transcript files, so only allow UUID characters. */
    static boolean isValidHandle(@NonNull String handle) {
        return !handle.isEmpty() && handle.length() <= 64 && handle.matches("[A-Za-z0-9-]+");
    }

    @Nullable
    private static String optString(@NonNull JSONObject object, @NonNull String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

}
