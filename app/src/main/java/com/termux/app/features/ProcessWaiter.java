package com.termux.app.features;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeUnit;

/** Waits for a process with a timeout on all Android versions. Free of Android state, so it can be tested. */
final class ProcessWaiter {

    private ProcessWaiter() {}

    /**
     * Wait at most {@code timeoutMillis} for {@code process} to exit, and return whether it exited.
     * {@link Process#waitFor(long, TimeUnit)} needs Android 8, so poll on older versions.
     */
    static boolean waitFor(@NonNull Process process, long timeoutMillis) throws InterruptedException {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            return process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException e) {
                // Still running.
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0) return false;
            Thread.sleep(Math.min(remainingMillis, 50));
        }
    }

}
