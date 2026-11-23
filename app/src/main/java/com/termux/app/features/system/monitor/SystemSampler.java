package com.termux.app.features.system.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the CPU, memory, battery and process values shown by {@link SystemMonitorActivity}.
 *
 * Android restricts what an app can read: {@code /proc/stat} can't be read since Android 8, and
 * {@code /proc} only lists the processes of the app's own uid since Android 7 (hidepid). Every
 * value that can't be read is reported as unavailable instead of failing, so the screen can say
 * what is missing.
 *
 * An instance keeps the previous samples to compute usage, so it must only be used from one thread.
 */
@WorkerThread
final class SystemSampler {

    /** A process of this app. */
    static final class ProcessInfo {
        final int pid;
        @NonNull final String name;
        @Nullable final String cmdline;
        final char state;
        /** CPU usage since the previous sample, in percent of one core. */
        final float cpuPercent;
        final long rssBytes;
        final int threads;
        final boolean isAppProcess;

        ProcessInfo(int pid, @NonNull String name, @Nullable String cmdline, char state, float cpuPercent,
                    long rssBytes, int threads, boolean isAppProcess) {
            this.pid = pid;
            this.name = name;
            this.cmdline = cmdline;
            this.state = state;
            this.cpuPercent = cpuPercent;
            this.rssBytes = rssBytes;
            this.threads = threads;
            this.isAppProcess = isAppProcess;
        }
    }

    static final class Snapshot {
        int coreCount;
        /** Whether {@code /proc/stat} could be read. If not, the overall and per core usage is unknown. */
        boolean cpuStatReadable;
        /** The overall CPU usage in percent, or -1 if unknown. */
        float cpuOverallPercent = -1;
        /** The usage in percent of each core, or -1 if unknown. Empty if {@link #cpuStatReadable} is false. */
        float[] cpuCorePercent = new float[0];
        /** The current frequency of each core in kHz, or -1 if it can't be read. */
        long[] cpuCoreFreqKhz = new long[0];
        /** The CPU usage of the app's processes together, in percent of all cores, or -1 for the first sample. */
        float appCpuPercent = -1;

        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        @Nullable ProcParsers.MemInfo memInfo;

        /** The battery level in percent, or -1 if unknown. */
        int batteryPercent = -1;
        boolean batteryCharging;
        /** From {@link BatteryManager#BATTERY_STATUS_FULL} etc, or 0 if unknown. */
        int batteryStatus;
        int batteryPlugged;
        /** The temperature in degrees Celsius, or NaN if unknown. */
        float batteryTemperature = Float.NaN;
        int batteryVoltageMillivolts = -1;
        int batteryHealth;

        List<ProcessInfo> processes = Collections.emptyList();
        /** Whether the list of processes could be read. */
        boolean processesReadable;
    }

    private final Context mContext;
    private final long mTicksPerSecond;
    private final int mPageSize;

    private List<ProcParsers.CpuTimes> mPreviousCpuTimes;
    private final Map<Integer, long[]> mPreviousProcessTicks = new HashMap<>();
    private long mPreviousProcessSampleTime = -1;

    SystemSampler(@NonNull Context context) {
        mContext = context.getApplicationContext();
        long ticks = 100;
        int pageSize = 4096;
        try {
            ticks = Os.sysconf(OsConstants._SC_CLK_TCK);
            pageSize = (int) Os.sysconf(OsConstants._SC_PAGESIZE);
        } catch (Exception e) {
            // Use the defaults of Linux.
        }
        mTicksPerSecond = ticks > 0 ? ticks : 100;
        mPageSize = pageSize > 0 ? pageSize : 4096;
    }

    @NonNull
    Snapshot sample() {
        Snapshot snapshot = new Snapshot();
        sampleCpu(snapshot);
        sampleMemory(snapshot);
        sampleBattery(snapshot);
        sampleProcesses(snapshot);
        return snapshot;
    }



    private void sampleCpu(@NonNull Snapshot snapshot) {
        snapshot.coreCount = Math.max(1, Runtime.getRuntime().availableProcessors());

        List<ProcParsers.CpuTimes> times = ProcParsers.parseCpuStat(readFile("/proc/stat", 64 * 1024));
        snapshot.cpuStatReadable = !times.isEmpty();
        if (snapshot.cpuStatReadable) {
            if (mPreviousCpuTimes != null) {
                // Match the cores by index, since a core that is offline is not listed.
                Map<Integer, ProcParsers.CpuTimes> previous = new HashMap<>();
                for (ProcParsers.CpuTimes t : mPreviousCpuTimes) previous.put(t.core, t);
                int maxCore = -1;
                for (ProcParsers.CpuTimes t : times) maxCore = Math.max(maxCore, t.core);
                snapshot.coreCount = Math.max(snapshot.coreCount, maxCore + 1);
                float[] cores = new float[snapshot.coreCount];
                java.util.Arrays.fill(cores, -1f);
                for (ProcParsers.CpuTimes t : times) {
                    ProcParsers.CpuTimes p = previous.get(t.core);
                    if (p == null) continue;
                    float usage = ProcParsers.getCpuUsagePercent(p, t);
                    if (t.core < 0) snapshot.cpuOverallPercent = usage;
                    else cores[t.core] = usage;
                }
                snapshot.cpuCorePercent = cores;
            }
            mPreviousCpuTimes = times;
        } else {
            mPreviousCpuTimes = null;
        }

        long[] freqs = new long[snapshot.coreCount];
        for (int i = 0; i < freqs.length; i++) {
            String value = readFile("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq", 64);
            freqs[i] = -1;
            if (value != null) {
                try {
                    freqs[i] = Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    // Leave it unknown.
                }
            }
        }
        snapshot.cpuCoreFreqKhz = freqs;
    }

    private void sampleMemory(@NonNull Snapshot snapshot) {
        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) activityManager.getMemoryInfo(snapshot.memory);
        snapshot.memInfo = ProcParsers.parseMemInfo(readFile("/proc/meminfo", 16 * 1024));
    }

    private void sampleBattery(@NonNull Snapshot snapshot) {
        // A sticky broadcast, so no receiver is needed.
        Intent intent = mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) snapshot.batteryPercent = Math.round(level * 100f / scale);
        snapshot.batteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        snapshot.batteryCharging = snapshot.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            snapshot.batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
        snapshot.batteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (temperature != Integer.MIN_VALUE) snapshot.batteryTemperature = temperature / 10f;
        snapshot.batteryVoltageMillivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        snapshot.batteryHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
    }

    private void sampleProcesses(@NonNull Snapshot snapshot) {
        long now = SystemClock.elapsedRealtime();
        long elapsed = mPreviousProcessSampleTime < 0 ? 0 : now - mPreviousProcessSampleTime;

        File[] entries = new File("/proc").listFiles();
        snapshot.processesReadable = entries != null;
        if (entries == null) return;

        int myUid = android.os.Process.myUid();
        int myPid = android.os.Process.myPid();
        List<ProcessInfo> processes = new ArrayList<>();
        Map<Integer, long[]> ticks = new HashMap<>();
        float appCpu = 0;
        boolean hasPrevious = mPreviousProcessSampleTime >= 0;

        for (File entry : entries) {
            String name = entry.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            int pid;
            try {
                pid = Integer.parseInt(name);
            } catch (NumberFormatException e) {
                continue;
            }

            // Processes of other apps are hidden by the system, but check the owner anyway.
            try {
                if (Os.stat(entry.getPath()).st_uid != myUid) continue;
            } catch (ErrnoException e) {
                continue;
            }

            ProcParsers.ProcessStat stat = ProcParsers.parseProcessStat(readFile("/proc/" + pid + "/stat", 4096));
            if (stat == null) continue; // The process exited, or its stat can't be read.
            String cmdline = ProcParsers.parseCmdline(readFile("/proc/" + pid + "/cmdline", 4096));

            float cpu = 0;
            long[] previous = mPreviousProcessTicks.get(pid);
            // The pid may have been reused by another process, which has a different start time.
            if (previous != null && previous[1] == stat.startTicks)
                cpu = ProcParsers.getProcessCpuPercent(previous[0], stat.cpuTicks, elapsed, mTicksPerSecond);
            ticks.put(pid, new long[]{stat.cpuTicks, stat.startTicks});
            appCpu += cpu;

            processes.add(new ProcessInfo(pid, ProcParsers.getProcessName(cmdline, stat.comm), cmdline, stat.state, cpu,
                stat.rssPages * mPageSize, stat.threads, pid == myPid));
        }

        mPreviousProcessTicks.clear();
        mPreviousProcessTicks.putAll(ticks);
        mPreviousProcessSampleTime = now;

        // The app process first, then by CPU and memory.
        Collections.sort(processes, (a, b) -> {
            if (a.isAppProcess != b.isAppProcess) return a.isAppProcess ? -1 : 1;
            int byCpu = Float.compare(b.cpuPercent, a.cpuPercent);
            if (byCpu != 0) return byCpu;
            int byMemory = Long.compare(b.rssBytes, a.rssBytes);
            return byMemory != 0 ? byMemory : Integer.compare(a.pid, b.pid);
        });
        snapshot.processes = processes;
        if (hasPrevious) snapshot.appCpuPercent = Math.min(100f, appCpu / snapshot.coreCount);
    }



    /** Read at most {@code maxBytes} of a file, or return {@code null} if it can't be read. */
    @Nullable
    static String readFile(@NonNull String path, int maxBytes) {
        try (InputStream in = new FileInputStream(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while (out.size() < maxBytes && (read = in.read(buffer)) != -1)
                out.write(buffer, 0, Math.min(read, maxBytes - out.size()));
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

}
