package com.termux.app.features;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link ProcessWaiter#waitFor} polls on Android 7, where Process#waitFor(long, TimeUnit)
 * doesn't exist. Unit tests run with SDK_INT 0, so they test that path.
 */
public class ProcessWaiterTest {

    @Test
    public void unitTestsUseThePollingPath() {
        assertEquals(0, android.os.Build.VERSION.SDK_INT);
    }

    @Test
    public void returnsTrueWhenTheProcessExitsInTime() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sleep 0.2").start();
        long start = System.nanoTime();
        assertTrue(ProcessWaiter.waitFor(process, 5000));
        assertTrue("returned long after the process exited", (System.nanoTime() - start) / 1_000_000 < 3000);
        assertEquals(0, process.exitValue());
    }

    @Test
    public void returnsFalseWhenTheTimeoutPasses() throws Exception {
        Process process = new ProcessBuilder("sh", "-c", "sleep 30").start();
        try {
            long start = System.nanoTime();
            assertFalse(ProcessWaiter.waitFor(process, 300));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertTrue("waited " + elapsedMillis + " ms", elapsedMillis >= 250 && elapsedMillis < 3000);
        } finally {
            process.destroy();
        }
    }
}
