package com.termux.app.features.sessions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SessionPersistenceTest {

    @Test
    public void testSnapshotJsonRoundTrip() throws Exception {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.time = 1234;
        snapshot.currentHandle = "b2";
        SessionSnapshot.Session first = new SessionSnapshot.Session("a1");
        first.name = "build";
        first.workspaceId = "work";
        first.pinned = true;
        first.profileId = "p";
        first.workingDirectory = "/home/src";
        first.hasTranscript = true;
        snapshot.sessions.add(first);
        snapshot.sessions.add(new SessionSnapshot.Session("b2"));

        SessionSnapshot copy = SessionSnapshot.fromJson(snapshot.toJson());
        Assert.assertNotNull(copy);
        Assert.assertEquals(1234, copy.time);
        Assert.assertEquals("b2", copy.currentHandle);
        Assert.assertEquals(2, copy.sessions.size());
        SessionSnapshot.Session restored = copy.sessions.get(0);
        Assert.assertEquals("a1", restored.handle);
        Assert.assertEquals("build", restored.name);
        Assert.assertEquals("work", restored.workspaceId);
        Assert.assertTrue(restored.pinned);
        Assert.assertEquals("p", restored.profileId);
        Assert.assertEquals("/home/src", restored.workingDirectory);
        Assert.assertTrue(restored.hasTranscript);
        SessionSnapshot.Session second = copy.sessions.get(1);
        Assert.assertNull(second.name);
        Assert.assertNull(second.profileId);
        Assert.assertEquals(Workspace.DEFAULT_ID, second.workspaceId);
        Assert.assertFalse(second.hasTranscript);
    }

    @Test
    public void testInvalidSnapshots() {
        Assert.assertNull(SessionSnapshot.fromJson(null));
        Assert.assertNull(SessionSnapshot.fromJson("not json"));
        Assert.assertNull(SessionSnapshot.fromJson("{\"version\": 99, \"sessions\": []}"));
        // Handles name files, so other characters are not accepted.
        SessionSnapshot snapshot = SessionSnapshot.fromJson("{\"version\": 1, \"sessions\": [{\"handle\": \"../x\"}, {\"handle\": \"ok-1\"}]}");
        Assert.assertNotNull(snapshot);
        Assert.assertEquals(1, snapshot.sessions.size());
        Assert.assertEquals("ok-1", snapshot.sessions.get(0).handle);
    }

    @Test
    public void testCapTranscript() {
        Assert.assertEquals("", SessionPersistence.capTranscript(null, 10, 100));
        Assert.assertEquals("a\nb\nc", SessionPersistence.capTranscript("a\nb\nc\n\n  \n", 10, 100));
        Assert.assertEquals("b\nc", SessionPersistence.capTranscript("a\nb\nc", 2, 100));
        Assert.assertEquals("c", SessionPersistence.capTranscript("a\nb\nc", 1, 100));
        Assert.assertEquals("789", SessionPersistence.capTranscript("123456789", 10, 3));
        // A surrogate pair is not split.
        Assert.assertEquals("x", SessionPersistence.capTranscript("😀x", 10, 2));
    }

    @Test
    public void testFormatRestoredHistory() {
        String text = SessionPersistence.formatRestoredHistory("$ ls\n\u001b[31mred\u0007\tend", "Header", "Footer");
        Assert.assertEquals("\u001b[0;2mHeader\u001b[0m\r\n$ ls\r\n[31mred\tend\r\n\u001b[0;2mFooter\u001b[0m\r\n", text);
    }

}
