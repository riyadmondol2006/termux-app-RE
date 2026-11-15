package com.termux.app.terminal;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TermuxTerminalSessionActivityClientTest {

    @Test
    public void testShouldShowTitleChangeToast() {
        final long interval = TermuxTerminalSessionActivityClient.TITLE_CHANGE_TOAST_MIN_INTERVAL;
        Map<String, Long> toastTimes = new HashMap<>();

        // The first title change of a session shows a toast.
        Assert.assertTrue(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session1", 1000));

        // Title changes of the same session within the interval do not, like for a spinner.
        for (long now = 1000; now < 1000 + interval; now += 50)
            Assert.assertFalse(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session1", now));

        // Other sessions are throttled separately.
        Assert.assertTrue(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session2", 1100));
        Assert.assertFalse(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session2", 1200));

        // Once the interval has passed, a toast is shown again.
        Assert.assertTrue(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session1", 1000 + interval));
        Assert.assertFalse(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session1", 1000 + interval + 1));

        // A time before the last toast does not keep the toasts disabled.
        Assert.assertTrue(TermuxTerminalSessionActivityClient.shouldShowTitleChangeToast(toastTimes, "session1", 0));
    }

}
