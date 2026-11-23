package com.termux.app.features.system.monitor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsers for the text files of {@code /proc} and {@code /sys} read by the system monitor, kept
 * free of Android classes so they can be unit tested.
 */
public final class ProcParsers {

    private ProcParsers() {}

    /** The values of {@code /proc/meminfo}, in bytes. A value is -1 if the file does not have it. */
    public static final class MemInfo {
        public final long total, free, available, buffers, cached, swapTotal, swapFree;

        MemInfo(long total, long free, long available, long buffers, long cached, long swapTotal, long swapFree) {
            this.total = total;
            this.free = free;
            this.available = available;
            this.buffers = buffers;
            this.cached = cached;
            this.swapTotal = swapTotal;
            this.swapFree = swapFree;
        }

        /** The memory in use, which is {@code total - available}, or {@code total - free - buffers - cached} on old kernels. */
        public long getUsed() {
            if (total < 0) return -1;
            if (available >= 0) return Math.max(0, total - available);
            if (free >= 0) return Math.max(0, total - free - Math.max(0, buffers) - Math.max(0, cached));
            return -1;
        }
    }

    /** Parse the content of {@code /proc/meminfo}. Returns {@code null} if it has no "MemTotal". */
    @Nullable
    public static MemInfo parseMemInfo(@Nullable String content) {
        if (content == null) return null;
        long total = -1, free = -1, available = -1, buffers = -1, cached = -1, swapTotal = -1, swapFree = -1;
        for (String line : content.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim();
            long value = parseKilobytes(line.substring(colon + 1));
            if (value < 0) continue;
            switch (key) {
                case "MemTotal": total = value; break;
                case "MemFree": free = value; break;
                case "MemAvailable": available = value; break;
                case "Buffers": buffers = value; break;
                case "Cached": cached = value; break;
                case "SwapTotal": swapTotal = value; break;
                case "SwapFree": swapFree = value; break;
                default: break;
            }
        }
        if (total < 0) return null;
        return new MemInfo(total, free, available, buffers, cached, swapTotal, swapFree);
    }

    /** Parse a value like " 123456 kB" to bytes, or -1. */
    private static long parseKilobytes(@NonNull String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return -1;
        try {
            long number = Long.parseLong(parts[0]);
            return parts.length > 1 && parts[1].equalsIgnoreCase("kB") ? number * 1024 : number;
        } catch (NumberFormatException e) {
            return -1;
        }
    }



    /** The cumulative CPU times of a "cpu" line of {@code /proc/stat}, in clock ticks. */
    public static final class CpuTimes {
        /** The index of the core, or -1 for the line with the sum of all cores. */
        public final int core;
        public final long busy, total;

        public CpuTimes(int core, long busy, long total) {
            this.core = core;
            this.busy = busy;
            this.total = total;
        }
    }

    /**
     * Parse the "cpu" and "cpuN" lines of {@code /proc/stat}. The first item is the sum of all
     * cores, followed by the cores in the order they are listed. Empty if there are none.
     */
    @NonNull
    public static List<CpuTimes> parseCpuStat(@Nullable String content) {
        List<CpuTimes> result = new ArrayList<>();
        if (content == null) return result;
        for (String line : content.split("\n")) {
            if (!line.startsWith("cpu")) continue;
            String[] fields = line.trim().split("\\s+");
            String name = fields[0];
            int core;
            if (name.equals("cpu")) {
                core = -1;
            } else {
                try {
                    core = Integer.parseInt(name.substring(3));
                } catch (NumberFormatException e) {
                    continue;
                }
            }
            // user nice system idle iowait irq softirq steal [guest guest_nice, already in user and nice]
            if (fields.length < 5) continue;
            try {
                long total = 0, idle = 0;
                for (int i = 1; i < Math.min(fields.length, 9); i++) {
                    long value = Long.parseLong(fields[i]);
                    total += value;
                    if (i == 4 || i == 5) idle += value; // idle and iowait
                }
                result.add(new CpuTimes(core, total - idle, total));
            } catch (NumberFormatException e) {
                // Skip a broken line.
            }
        }
        return result;
    }

    /** The usage in percent (0-100) between two samples of the same CPU, or -1 if there is no time between them. */
    public static float getCpuUsagePercent(@NonNull CpuTimes previous, @NonNull CpuTimes current) {
        long totalDelta = current.total - previous.total;
        long busyDelta = current.busy - previous.busy;
        if (totalDelta <= 0 || busyDelta < 0) return -1;
        return Math.min(100f, busyDelta * 100f / totalDelta);
    }



    /** The values of {@code /proc/<pid>/stat} used by the system monitor. */
    public static final class ProcessStat {
        public final int pid;
        /** The name of the executable, at most 15 characters. */
        @NonNull public final String comm;
        public final char state;
        /** User and system CPU time of the process, in clock ticks. */
        public final long cpuTicks;
        public final int threads;
        /** The resident set size, in pages. */
        public final long rssPages;
        /** The start time since boot, in clock ticks. */
        public final long startTicks;

        ProcessStat(int pid, @NonNull String comm, char state, long cpuTicks, int threads, long rssPages, long startTicks) {
            this.pid = pid;
            this.comm = comm;
            this.state = state;
            this.cpuTicks = cpuTicks;
            this.threads = threads;
            this.rssPages = rssPages;
            this.startTicks = startTicks;
        }
    }

    /**
     * Parse the content of {@code /proc/<pid>/stat}, like {@code 123 (my (cmd)) S 1 ...}. The name
     * may contain spaces and parentheses, so it ends at the last ")". Returns {@code null} if the
     * line is incomplete.
     */
    @Nullable
    public static ProcessStat parseProcessStat(@Nullable String content) {
        if (content == null) return null;
        int open = content.indexOf('(');
        int close = content.lastIndexOf(')');
        if (open <= 0 || close < open) return null;
        try {
            int pid = Integer.parseInt(content.substring(0, open).trim());
            String comm = content.substring(open + 1, close);
            String[] fields = content.substring(close + 1).trim().split("\\s+");
            // fields[0] is field 3 (state) of proc(5), so field N is fields[N - 3].
            if (fields.length < 22 || fields[0].isEmpty()) return null;
            long utime = Long.parseLong(fields[11]);
            long stime = Long.parseLong(fields[12]);
            int threads = Integer.parseInt(fields[17]);
            long startTicks = Long.parseLong(fields[19]);
            long rssPages = Long.parseLong(fields[21]);
            return new ProcessStat(pid, comm, fields[0].charAt(0), utime + stime, threads, Math.max(0, rssPages), startTicks);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Get the command line from the content of {@code /proc/<pid>/cmdline}, whose arguments are
     * separated by NUL. Returns {@code null} if it is empty, like for kernel threads and zombies.
     */
    @Nullable
    public static String parseCmdline(@Nullable String content) {
        if (content == null) return null;
        String cmdline = content.replace('\0', ' ').trim();
        return cmdline.isEmpty() ? null : cmdline;
    }

    /**
     * Get the name to show for a process: the executable name of its command line, or else the
     * name from {@code /proc/<pid>/stat}. App processes of Android apps have the package name as
     * the whole command line.
     */
    @NonNull
    public static String getProcessName(@Nullable String cmdline, @NonNull String comm) {
        if (cmdline == null) return comm;
        String command = cmdline.split(" ", 2)[0];
        int slash = command.lastIndexOf('/');
        String name = slash >= 0 ? command.substring(slash + 1) : command;
        return name.isEmpty() ? comm : name;
    }



    /** Get the CPU usage of a process between two samples, in percent of one core (so it can be above 100). */
    public static float getProcessCpuPercent(long previousTicks, long currentTicks, long elapsedMillis, long ticksPerSecond) {
        if (elapsedMillis <= 0 || ticksPerSecond <= 0 || currentTicks < previousTicks) return 0;
        return (currentTicks - previousTicks) * 1000f * 100f / (ticksPerSecond * (float) elapsedMillis);
    }

    /** Format a size like "1.5 GB", with 1024 as the base. */
    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value >= 100 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

}
