package com.termux.app.features.sessions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the rows of the sessions list in the drawer: the sessions of the selected workspace, or
 * of all workspaces grouped under a header per workspace, with pinned sessions first.
 */
public final class SessionListModel {

    public static final int ROW_SESSION = 0;
    public static final int ROW_HEADER = 1;
    public static final int ROW_EMPTY = 2;
    public static final int ROW_TYPE_COUNT = 3;

    private SessionListModel() {}

    /** A session, in the order the sessions were created. */
    public static final class Entry {
        /** The index of the session in the sessions list of the service. */
        public final int index;
        @NonNull public final String workspaceId;
        public final boolean pinned;

        public Entry(int index, @NonNull String workspaceId, boolean pinned) {
            this.index = index;
            this.workspaceId = workspaceId;
            this.pinned = pinned;
        }
    }

    /** A row of the list. */
    public static final class Row {
        public final int type;
        /** For {@link #ROW_SESSION}, the index of the session in the sessions list of the service. */
        public final int sessionIndex;
        /** For {@link #ROW_SESSION}, the number shown for the session, starting at 1. */
        public final int number;
        /** For {@link #ROW_HEADER}, the workspace. */
        @Nullable public final String workspaceId;

        Row(int type, int sessionIndex, int number, @Nullable String workspaceId) {
            this.type = type;
            this.sessionIndex = sessionIndex;
            this.number = number;
            this.workspaceId = workspaceId;
        }

        @NonNull
        @Override
        public String toString() {
            switch (type) {
                case ROW_SESSION: return "session:" + sessionIndex + "#" + number;
                case ROW_HEADER: return "header:" + workspaceId;
                default: return "empty";
            }
        }
    }

    /**
     * Build the rows.
     *
     * @param entries The sessions, in creation order.
     * @param workspaceIds The ids of the existing workspaces in the order they are listed, starting
     *                     with {@link Workspace#DEFAULT_ID}. Sessions of other workspaces are listed
     *                     in the default workspace.
     * @param filterWorkspaceId The id of the workspace to list the sessions of, or {@code null} for
     *                          all workspaces.
     */
    @NonNull
    public static List<Row> build(@NonNull List<Entry> entries, @NonNull List<String> workspaceIds,
                                  @Nullable String filterWorkspaceId) {
        Map<String, List<Entry>> groups = new LinkedHashMap<>();
        for (String workspaceId : workspaceIds) groups.put(workspaceId, new ArrayList<>());
        if (!groups.containsKey(Workspace.DEFAULT_ID)) groups.put(Workspace.DEFAULT_ID, new ArrayList<>());

        for (Entry entry : entries) {
            List<Entry> group = groups.get(entry.workspaceId);
            if (group == null) group = groups.get(Workspace.DEFAULT_ID);
            group.add(entry);
        }

        List<Row> rows = new ArrayList<>();
        int[] number = {1};
        if (filterWorkspaceId != null) {
            List<Entry> group = groups.get(filterWorkspaceId);
            if (group == null || group.isEmpty()) {
                rows.add(new Row(ROW_EMPTY, -1, 0, filterWorkspaceId));
            } else {
                addSessionRows(rows, group, number);
            }
            return rows;
        }

        int nonEmptyGroups = 0;
        for (List<Entry> group : groups.values()) if (!group.isEmpty()) nonEmptyGroups++;

        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            if (group.getValue().isEmpty()) continue;
            if (nonEmptyGroups > 1) rows.add(new Row(ROW_HEADER, -1, 0, group.getKey()));
            addSessionRows(rows, group.getValue(), number);
        }
        return rows;
    }

    private static void addSessionRows(@NonNull List<Row> rows, @NonNull List<Entry> group, int[] number) {
        for (Entry entry : group) {
            if (entry.pinned) rows.add(new Row(ROW_SESSION, entry.index, number[0]++, null));
        }
        for (Entry entry : group) {
            if (!entry.pinned) rows.add(new Row(ROW_SESSION, entry.index, number[0]++, null));
        }
    }

    /** Get the session indexes of the session rows of {@code rows}, in the order they are listed. */
    @NonNull
    public static List<Integer> getSessionOrder(@NonNull List<Row> rows) {
        List<Integer> order = new ArrayList<>();
        for (Row row : rows) {
            if (row.type == ROW_SESSION) order.add(row.sessionIndex);
        }
        return order;
    }

    /**
     * Get the index in {@code order} of the session to switch to from the session at
     * {@code currentPosition}, wrapping around. If the current session is not listed, the first
     * or last session is returned. Returns -1 if {@code size} is 0.
     */
    public static int getNextPosition(int currentPosition, int size, boolean forward) {
        if (size <= 0) return -1;
        if (currentPosition < 0 || currentPosition >= size) return forward ? 0 : size - 1;
        return forward ? (currentPosition + 1) % size : (currentPosition - 1 + size) % size;
    }

}
