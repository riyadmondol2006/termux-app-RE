package com.termux.app.features.system;

import com.termux.app.features.system.monitor.ProcParsers;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ProcParsersTest {

    private static final String MEMINFO =
        "MemTotal:        7864320 kB\n" +
        "MemFree:          512000 kB\n" +
        "MemAvailable:    3145728 kB\n" +
        "Buffers:           10240 kB\n" +
        "Cached:          2048000 kB\n" +
        "SwapTotal:       4194304 kB\n" +
        "SwapFree:        4000000 kB\n" +
        "HugePages_Total:       0\n";

    @Test
    public void testParseMemInfo() {
        ProcParsers.MemInfo info = ProcParsers.parseMemInfo(MEMINFO);
        Assert.assertNotNull(info);
        Assert.assertEquals(7864320L * 1024, info.total);
        Assert.assertEquals(3145728L * 1024, info.available);
        Assert.assertEquals((7864320L - 3145728L) * 1024, info.getUsed());
        Assert.assertEquals(4194304L * 1024, info.swapTotal);
    }

    @Test
    public void testParseMemInfoWithoutAvailable() {
        ProcParsers.MemInfo info = ProcParsers.parseMemInfo("MemTotal: 1000 kB\nMemFree: 200 kB\nBuffers: 100 kB\nCached: 300 kB\n");
        Assert.assertNotNull(info);
        Assert.assertEquals(-1, info.available);
        Assert.assertEquals(400L * 1024, info.getUsed());
    }

    @Test
    public void testParseMemInfoInvalid() {
        Assert.assertNull(ProcParsers.parseMemInfo(null));
        Assert.assertNull(ProcParsers.parseMemInfo(""));
        Assert.assertNull(ProcParsers.parseMemInfo("MemFree: 1 kB\n"));
    }

    @Test
    public void testParseCpuStat() {
        String stat =
            "cpu  100 10 50 800 40 0 0 0 0 0\n" +
            "cpu0 60 5 20 400 20 0 0 0 0 0\n" +
            "cpu1 40 5 30 400 20 0 0 0 0 0\n" +
            "intr 12345\n";
        List<ProcParsers.CpuTimes> times = ProcParsers.parseCpuStat(stat);
        Assert.assertEquals(3, times.size());
        Assert.assertEquals(-1, times.get(0).core);
        Assert.assertEquals(1000, times.get(0).total);
        Assert.assertEquals(160, times.get(0).busy); // user + nice + system
        Assert.assertEquals(1, times.get(2).core);
        Assert.assertTrue(ProcParsers.parseCpuStat(null).isEmpty());
        Assert.assertTrue(ProcParsers.parseCpuStat("garbage\n").isEmpty());
    }

    @Test
    public void testGetCpuUsagePercent() {
        ProcParsers.CpuTimes a = new ProcParsers.CpuTimes(-1, 100, 1000);
        ProcParsers.CpuTimes b = new ProcParsers.CpuTimes(-1, 150, 1100);
        Assert.assertEquals(50f, ProcParsers.getCpuUsagePercent(a, b), 0.01f);
        Assert.assertEquals(-1f, ProcParsers.getCpuUsagePercent(a, a), 0f);
        // The counters went backwards, like after a CPU was taken offline.
        Assert.assertEquals(-1f, ProcParsers.getCpuUsagePercent(b, a), 0f);
    }

    @Test
    public void testParseProcessStat() {
        String stat = "4242 (bash) S 4000 4242 4242 34816 4242 4194560 1000 0 0 0 150 50 0 0 20 0 3 0 987654 12345678 2560 18446744073709551615 1 1 0 0 0 0 0 0 0 0 0 0 17 5 0 0 0 0 0";
        ProcParsers.ProcessStat parsed = ProcParsers.parseProcessStat(stat);
        Assert.assertNotNull(parsed);
        Assert.assertEquals(4242, parsed.pid);
        Assert.assertEquals("bash", parsed.comm);
        Assert.assertEquals('S', parsed.state);
        Assert.assertEquals(200, parsed.cpuTicks);
        Assert.assertEquals(3, parsed.threads);
        Assert.assertEquals(987654, parsed.startTicks);
        Assert.assertEquals(2560, parsed.rssPages);
    }

    @Test
    public void testParseProcessStatNameWithSpacesAndParentheses() {
        String stat = "77 (my (weird) name) R 1 77 77 0 -1 4194304 1 0 0 0 7 3 0 0 20 0 1 0 500 1000 99 0 0 0 0 0 0 0 0 0 0 0 0 0 17 0 0 0 0 0 0";
        ProcParsers.ProcessStat parsed = ProcParsers.parseProcessStat(stat);
        Assert.assertNotNull(parsed);
        Assert.assertEquals("my (weird) name", parsed.comm);
        Assert.assertEquals(10, parsed.cpuTicks);
        Assert.assertEquals(99, parsed.rssPages);
    }

    @Test
    public void testParseProcessStatInvalid() {
        Assert.assertNull(ProcParsers.parseProcessStat(null));
        Assert.assertNull(ProcParsers.parseProcessStat(""));
        Assert.assertNull(ProcParsers.parseProcessStat("12 (short) S 1 2"));
        Assert.assertNull(ProcParsers.parseProcessStat("abc (x) S 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23"));
    }

    @Test
    public void testCmdlineAndName() {
        Assert.assertEquals("/data/data/com.termux.re/files/usr/bin/bash -l", ProcParsers.parseCmdline("/data/data/com.termux.re/files/usr/bin/bash\0-l\0"));
        Assert.assertNull(ProcParsers.parseCmdline("\0\0"));
        Assert.assertEquals("bash", ProcParsers.getProcessName("/data/data/com.termux.re/files/usr/bin/bash -l", "bash"));
        Assert.assertEquals("com.termux.re", ProcParsers.getProcessName("com.termux.re", "ux.re"));
        Assert.assertEquals("kworker", ProcParsers.getProcessName(null, "kworker"));
    }

    @Test
    public void testGetProcessCpuPercent() {
        // 50 ticks of 100 per second in one second is half a core.
        Assert.assertEquals(50f, ProcParsers.getProcessCpuPercent(100, 150, 1000, 100), 0.01f);
        Assert.assertEquals(200f, ProcParsers.getProcessCpuPercent(0, 400, 2000, 100), 0.01f);
        Assert.assertEquals(0f, ProcParsers.getProcessCpuPercent(100, 100, 0, 100), 0f);
        Assert.assertEquals(0f, ProcParsers.getProcessCpuPercent(150, 100, 1000, 100), 0f);
    }

    @Test
    public void testFormatBytes() {
        Assert.assertEquals("0 B", ProcParsers.formatBytes(0));
        Assert.assertEquals("1.0 KB", ProcParsers.formatBytes(1024));
        Assert.assertEquals("1.5 MB", ProcParsers.formatBytes(1536 * 1024));
        Assert.assertEquals("7.5 GB", ProcParsers.formatBytes(8053063680L));
        Assert.assertEquals("512 MB", ProcParsers.formatBytes(512L * 1024 * 1024));
        Assert.assertEquals("?", ProcParsers.formatBytes(-1));
    }

}
