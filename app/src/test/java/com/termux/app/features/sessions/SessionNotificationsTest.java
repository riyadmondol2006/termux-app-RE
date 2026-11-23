package com.termux.app.features.sessions;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SessionNotificationsTest {

    @Test
    public void testClean() {
        Assert.assertEquals("", SessionNotifications.clean(null, 10));
        Assert.assertEquals("", SessionNotifications.clean(" \u0007 ", 10));
        Assert.assertEquals("a b c", SessionNotifications.clean("a\u001bb c", 10));
        Assert.assertEquals("Build done", SessionNotifications.clean("  Build done\r\n", 20));
        Assert.assertEquals("12345…", SessionNotifications.clean("1234567890", 5));
        // A surrogate pair is not split.
        Assert.assertEquals("1234…", SessionNotifications.clean("1234😀", 5));
    }

    @Test
    public void testShouldNotify() {
        Map<String, Long> times = new HashMap<>();
        Assert.assertTrue(SessionNotifications.shouldNotify(times, "s1", 10_000));
        Assert.assertFalse(SessionNotifications.shouldNotify(times, "s1", 10_500));
        Assert.assertTrue(SessionNotifications.shouldNotify(times, "s2", 10_500));
        Assert.assertTrue(SessionNotifications.shouldNotify(times, "s1", 10_000 + SessionNotifications.MIN_INTERVAL_MILLIS));
        // A time before the last notification does not block notifications.
        Assert.assertTrue(SessionNotifications.shouldNotify(times, "s1", 0));
    }

}
