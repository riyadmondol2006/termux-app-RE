package com.termux.app.features.sessions;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SessionListModelTest {

    private static final List<String> WORKSPACES = Arrays.asList(Workspace.DEFAULT_ID, "work", "server");

    private static SessionListModel.Entry entry(int index, String workspaceId, boolean pinned) {
        return new SessionListModel.Entry(index, workspaceId, pinned);
    }

    private static String rows(List<SessionListModel.Row> rows) {
        return rows.toString();
    }

    @Test
    public void testAllSessionsInDefaultWorkspaceHaveNoHeaders() {
        List<SessionListModel.Entry> entries = Arrays.asList(
            entry(0, Workspace.DEFAULT_ID, false), entry(1, Workspace.DEFAULT_ID, false), entry(2, Workspace.DEFAULT_ID, false));
        Assert.assertEquals("[session:0#1, session:1#2, session:2#3]", rows(SessionListModel.build(entries, WORKSPACES, null)));
    }

    @Test
    public void testPinnedSessionsAreListedFirst() {
        List<SessionListModel.Entry> entries = Arrays.asList(
            entry(0, Workspace.DEFAULT_ID, false), entry(1, Workspace.DEFAULT_ID, true), entry(2, Workspace.DEFAULT_ID, false), entry(3, Workspace.DEFAULT_ID, true));
        List<SessionListModel.Row> rows = SessionListModel.build(entries, WORKSPACES, null);
        Assert.assertEquals("[session:1#1, session:3#2, session:0#3, session:2#4]", rows(rows));
        Assert.assertEquals(Arrays.asList(1, 3, 0, 2), SessionListModel.getSessionOrder(rows));
    }

    @Test
    public void testAllWorkspacesAreGroupedInWorkspaceOrder() {
        List<SessionListModel.Entry> entries = Arrays.asList(
            entry(0, "server", false), entry(1, Workspace.DEFAULT_ID, false), entry(2, "server", true), entry(3, "work", false));
        Assert.assertEquals("[header:default, session:1#1, header:work, session:3#2, header:server, session:2#3, session:0#4]",
            rows(SessionListModel.build(entries, WORKSPACES, null)));
    }

    @Test
    public void testSessionsOfDeletedWorkspacesAreInDefaultWorkspace() {
        List<SessionListModel.Entry> entries = Arrays.asList(entry(0, "deleted", false), entry(1, Workspace.DEFAULT_ID, false));
        Assert.assertEquals("[session:0#1, session:1#2]", rows(SessionListModel.build(entries, WORKSPACES, null)));
        Assert.assertEquals("[session:0#1, session:1#2]", rows(SessionListModel.build(entries, WORKSPACES, Workspace.DEFAULT_ID)));
    }

    @Test
    public void testSelectedWorkspace() {
        List<SessionListModel.Entry> entries = Arrays.asList(
            entry(0, "work", false), entry(1, Workspace.DEFAULT_ID, false), entry(2, "work", true));
        Assert.assertEquals("[session:2#1, session:0#2]", rows(SessionListModel.build(entries, WORKSPACES, "work")));
        Assert.assertEquals("[session:1#1]", rows(SessionListModel.build(entries, WORKSPACES, Workspace.DEFAULT_ID)));
        Assert.assertEquals("[empty]", rows(SessionListModel.build(entries, WORKSPACES, "server")));
        Assert.assertTrue(SessionListModel.getSessionOrder(SessionListModel.build(entries, WORKSPACES, "server")).isEmpty());
    }

    @Test
    public void testNoSessions() {
        Assert.assertEquals("[]", rows(SessionListModel.build(Arrays.asList(), WORKSPACES, null)));
    }

    @Test
    public void testGetNextPosition() {
        Assert.assertEquals(1, SessionListModel.getNextPosition(0, 3, true));
        Assert.assertEquals(0, SessionListModel.getNextPosition(2, 3, true));
        Assert.assertEquals(2, SessionListModel.getNextPosition(0, 3, false));
        Assert.assertEquals(0, SessionListModel.getNextPosition(1, 3, false));
        // The current session is not listed.
        Assert.assertEquals(0, SessionListModel.getNextPosition(-1, 3, true));
        Assert.assertEquals(2, SessionListModel.getNextPosition(-1, 3, false));
        Assert.assertEquals(-1, SessionListModel.getNextPosition(-1, 0, true));
    }

}
