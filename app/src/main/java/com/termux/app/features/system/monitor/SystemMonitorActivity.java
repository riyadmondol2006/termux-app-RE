package com.termux.app.features.system.monitor;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.termux.R;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A live view of the CPU, memory, storage and battery of the device and of the processes started
 * by the app. It only refreshes while it is visible.
 */
public class SystemMonitorActivity extends AppCompatActivity {

    /** The time between two samples of the CPU, memory, battery and processes. */
    private static final long REFRESH_INTERVAL_MILLIS = 1500;
    /** The time between two scans of the storage, which has to walk all files of the app. */
    private static final long STORAGE_REFRESH_INTERVAL_MILLIS = 30000;

    private static final String LOG_TAG = "SystemMonitorActivity";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ExecutorService mExecutor;
    private SystemSampler mSampler;
    private final AtomicBoolean mSampling = new AtomicBoolean(false);
    private final AtomicBoolean mScanningStorage = new AtomicBoolean(false);
    private boolean mVisible;
    private long mLastStorageScan;

    private Meter mCpuOverall, mRam, mSwap, mStorageData, mStorageShared, mBattery;
    private LinearLayout mCoresContainer, mProcessesContainer;
    private TextView mCpuNote, mMemoryDetails, mStorageDetails, mBatteryDetails, mProcessesNote, mProcessesTitle;
    private final List<Meter> mCoreMeters = new ArrayList<>();

    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mVisible) return;
            refresh();
            mHandler.postDelayed(this, REFRESH_INTERVAL_MILLIS);
        }
    };

    /** Open the monitor. */
    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, SystemMonitorActivity.class);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
        setContentView(R.layout.feature_system_monitor);
        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar);
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true);
        setTitle(R.string.feature_system_monitor_title);

        mCpuOverall = new Meter(findViewById(R.id.feature_system_cpu_overall));
        mCpuOverall.label.setText(R.string.feature_system_monitor_cpu_overall);
        mRam = new Meter(findViewById(R.id.feature_system_ram));
        mRam.label.setText(R.string.feature_system_monitor_ram);
        mSwap = new Meter(findViewById(R.id.feature_system_swap));
        mSwap.label.setText(R.string.feature_system_monitor_swap);
        mStorageData = new Meter(findViewById(R.id.feature_system_storage_data));
        mStorageData.label.setText(R.string.feature_system_monitor_storage_internal);
        mStorageShared = new Meter(findViewById(R.id.feature_system_storage_shared));
        mStorageShared.label.setText(R.string.feature_system_monitor_storage_shared);
        mBattery = new Meter(findViewById(R.id.feature_system_battery));
        mBattery.label.setText(R.string.feature_system_monitor_battery_level);

        mCoresContainer = findViewById(R.id.feature_system_cpu_cores);
        mProcessesContainer = findViewById(R.id.feature_system_processes);
        mCpuNote = findViewById(R.id.feature_system_cpu_note);
        mMemoryDetails = findViewById(R.id.feature_system_memory_details);
        mStorageDetails = findViewById(R.id.feature_system_storage_details);
        mBatteryDetails = findViewById(R.id.feature_system_battery_details);
        mProcessesNote = findViewById(R.id.feature_system_processes_note);
        mProcessesTitle = findViewById(R.id.feature_system_processes_title);

        mExecutor = Executors.newSingleThreadExecutor();
        mSampler = new SystemSampler(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mVisible = true;
        mHandler.removeCallbacks(mRefreshRunnable);
        mHandler.post(mRefreshRunnable);
    }

    @Override
    protected void onStop() {
        // Stop refreshing while the screen is not visible.
        mVisible = false;
        mHandler.removeCallbacks(mRefreshRunnable);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }



    private void refresh() {
        if (mSampling.compareAndSet(false, true)) {
            mExecutor.execute(() -> {
                SystemSampler.Snapshot snapshot = null;
                try {
                    snapshot = mSampler.sample();
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to sample the system", e);
                }
                final SystemSampler.Snapshot result = snapshot;
                mHandler.post(() -> {
                    mSampling.set(false);
                    if (result != null && mVisible) showSnapshot(result);
                });
            });
        }

        long now = System.currentTimeMillis();
        if (now - mLastStorageScan >= STORAGE_REFRESH_INTERVAL_MILLIS && mScanningStorage.compareAndSet(false, true)) {
            mLastStorageScan = now;
            final Context context = getApplicationContext();
            mExecutor.execute(() -> {
                StorageSampler.Snapshot snapshot = null;
                try {
                    snapshot = StorageSampler.sample(context);
                } catch (Exception e) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to sample the storage", e);
                }
                final StorageSampler.Snapshot result = snapshot;
                mHandler.post(() -> {
                    mScanningStorage.set(false);
                    if (result != null && !isDestroyed()) showStorage(result);
                });
            });
        }
    }

    private void showSnapshot(@NonNull SystemSampler.Snapshot s) {
        showCpu(s);
        showMemory(s);
        showBattery(s);
        showProcesses(s);
    }

    private void showCpu(@NonNull SystemSampler.Snapshot s) {
        if (s.cpuStatReadable) {
            if (s.cpuOverallPercent >= 0) mCpuOverall.set(percent(s.cpuOverallPercent), s.cpuOverallPercent);
            else mCpuOverall.set(getString(R.string.feature_system_monitor_measuring), 0);
            mCpuNote.setText(R.string.feature_system_monitor_cpu_note_stat);
        } else {
            // Android 8+ does not let apps read /proc/stat, so only the load of the app is known.
            if (s.appCpuPercent >= 0) {
                mCpuOverall.label.setText(R.string.feature_system_monitor_cpu_app);
                mCpuOverall.set(percent(s.appCpuPercent), s.appCpuPercent);
            } else {
                mCpuOverall.set(getString(R.string.feature_system_monitor_measuring), 0);
            }
            mCpuNote.setText(R.string.feature_system_monitor_cpu_note_restricted);
        }
        if (s.cpuStatReadable) mCpuOverall.label.setText(R.string.feature_system_monitor_cpu_overall);

        // Per core usage if it can be read, otherwise only the frequency, if that can be read.
        boolean anyFrequency = false;
        for (long f : s.cpuCoreFreqKhz) anyFrequency |= f >= 0;
        boolean showCores = s.cpuStatReadable || anyFrequency;
        mCoresContainer.setVisibility(showCores ? View.VISIBLE : View.GONE);
        if (showCores) {
            while (mCoreMeters.size() < s.coreCount) {
                Meter meter = new Meter(LayoutInflater.from(this).inflate(R.layout.feature_system_item_meter, mCoresContainer, false));
                mCoresContainer.addView(meter.root);
                mCoreMeters.add(meter);
            }
            for (int i = 0; i < mCoreMeters.size(); i++) {
                Meter meter = mCoreMeters.get(i);
                meter.root.setVisibility(i < s.coreCount ? View.VISIBLE : View.GONE);
                if (i >= s.coreCount) continue;
                meter.label.setText(getString(R.string.feature_system_monitor_core, i));
                String frequency = i < s.cpuCoreFreqKhz.length && s.cpuCoreFreqKhz[i] >= 0 ?
                    String.format(Locale.US, "%d MHz", s.cpuCoreFreqKhz[i] / 1000) : null;
                float usage = i < s.cpuCorePercent.length ? s.cpuCorePercent[i] : -1;
                if (usage >= 0)
                    meter.set(frequency != null ? percent(usage) + " · " + frequency : percent(usage), usage);
                else
                    meter.set(frequency != null ? frequency : getString(R.string.feature_system_monitor_offline), 0);
            }
        }
    }

    private void showMemory(@NonNull SystemSampler.Snapshot s) {
        long total = s.memory.totalMem;
        long available = s.memory.availMem;
        if (s.memInfo != null) {
            total = s.memInfo.total;
            if (s.memInfo.available >= 0) available = s.memInfo.available;
        }
        if (total > 0) {
            long used = Math.max(0, total - available);
            mRam.set(getString(R.string.feature_system_monitor_used_of, ProcParsers.formatBytes(used), ProcParsers.formatBytes(total)),
                used * 100f / total);
        } else {
            mRam.set(getString(R.string.feature_system_monitor_unavailable), 0);
        }

        if (s.memInfo != null && s.memInfo.swapTotal > 0) {
            mSwap.root.setVisibility(View.VISIBLE);
            long swapUsed = Math.max(0, s.memInfo.swapTotal - Math.max(0, s.memInfo.swapFree));
            mSwap.set(getString(R.string.feature_system_monitor_used_of, ProcParsers.formatBytes(swapUsed),
                ProcParsers.formatBytes(s.memInfo.swapTotal)), swapUsed * 100f / s.memInfo.swapTotal);
        } else {
            mSwap.root.setVisibility(View.GONE);
        }

        StringBuilder details = new StringBuilder();
        details.append(getString(R.string.feature_system_monitor_available, ProcParsers.formatBytes(available)));
        if (s.memInfo != null && s.memInfo.cached >= 0)
            details.append("\n").append(getString(R.string.feature_system_monitor_cached, ProcParsers.formatBytes(s.memInfo.cached)));
        details.append("\n").append(getString(R.string.feature_system_monitor_low_memory_threshold, ProcParsers.formatBytes(s.memory.threshold)));
        if (s.memory.lowMemory) details.append("\n").append(getString(R.string.feature_system_monitor_low_memory));
        long appRss = 0;
        for (SystemSampler.ProcessInfo process : s.processes) appRss += process.rssBytes;
        if (s.processesReadable)
            details.append("\n").append(getString(R.string.feature_system_monitor_app_memory, ProcParsers.formatBytes(appRss)));
        mMemoryDetails.setText(details);
    }

    private void showStorage(@NonNull StorageSampler.Snapshot s) {
        if (s.dataTotal > 0) {
            long used = s.dataTotal - s.dataFree;
            mStorageData.set(getString(R.string.feature_system_monitor_free_of, ProcParsers.formatBytes(s.dataFree),
                ProcParsers.formatBytes(s.dataTotal)), used * 100f / s.dataTotal);
        } else {
            mStorageData.set(getString(R.string.feature_system_monitor_unavailable), 0);
        }

        if (s.sharedTotal > 0) {
            long used = s.sharedTotal - s.sharedFree;
            mStorageShared.set(getString(R.string.feature_system_monitor_free_of, ProcParsers.formatBytes(s.sharedFree),
                ProcParsers.formatBytes(s.sharedTotal)), used * 100f / s.sharedTotal);
        } else {
            mStorageShared.set(getString(R.string.feature_system_monitor_unavailable), 0);
        }

        StringBuilder details = new StringBuilder();
        details.append(getString(R.string.feature_system_monitor_storage_prefix, ProcParsers.formatBytes(s.prefixSize)));
        details.append("\n").append(getString(R.string.feature_system_monitor_storage_home, ProcParsers.formatBytes(s.homeSize)));
        if (s.otherAppDataSize >= 0)
            details.append("\n").append(getString(R.string.feature_system_monitor_storage_other, ProcParsers.formatBytes(s.otherAppDataSize)));
        mStorageDetails.setText(details);
    }

    private void showBattery(@NonNull SystemSampler.Snapshot s) {
        if (s.batteryPercent >= 0)
            mBattery.set(s.batteryPercent + "%", s.batteryPercent);
        else
            mBattery.set(getString(R.string.feature_system_monitor_unavailable), 0);

        StringBuilder details = new StringBuilder(getString(getBatteryStatusString(s.batteryStatus)));
        if (s.batteryPlugged != 0) details.append(" · ").append(getString(getBatteryPluggedString(s.batteryPlugged)));
        if (!Float.isNaN(s.batteryTemperature))
            details.append("\n").append(getString(R.string.feature_system_monitor_battery_temperature,
                String.format(Locale.US, "%.1f", s.batteryTemperature)));
        if (s.batteryVoltageMillivolts > 0)
            details.append("\n").append(getString(R.string.feature_system_monitor_battery_voltage,
                String.format(Locale.US, "%.2f", s.batteryVoltageMillivolts / 1000f)));
        if (s.batteryHealth == BatteryManager.BATTERY_HEALTH_OVERHEAT)
            details.append("\n").append(getString(R.string.feature_system_monitor_battery_overheat));
        mBatteryDetails.setText(details);
    }

    private static int getBatteryStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return R.string.feature_system_monitor_battery_charging;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return R.string.feature_system_monitor_battery_discharging;
            case BatteryManager.BATTERY_STATUS_FULL: return R.string.feature_system_monitor_battery_full;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return R.string.feature_system_monitor_battery_not_charging;
            default: return R.string.feature_system_monitor_battery_unknown;
        }
    }

    private static int getBatteryPluggedString(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return R.string.feature_system_monitor_battery_plugged_ac;
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return R.string.feature_system_monitor_battery_plugged_usb;
        return R.string.feature_system_monitor_battery_plugged_wireless;
    }

    private void showProcesses(@NonNull SystemSampler.Snapshot s) {
        mProcessesTitle.setText(getString(R.string.feature_system_monitor_processes_count, s.processes.size()));
        mProcessesNote.setText(s.processesReadable ? R.string.feature_system_monitor_processes_note :
            R.string.feature_system_monitor_processes_unreadable);

        // Reuse the rows so the list does not flicker or lose the touch of the kill button.
        while (mProcessesContainer.getChildCount() > s.processes.size())
            mProcessesContainer.removeViewAt(mProcessesContainer.getChildCount() - 1);
        for (int i = 0; i < s.processes.size(); i++) {
            SystemSampler.ProcessInfo process = s.processes.get(i);
            View row;
            if (i < mProcessesContainer.getChildCount()) {
                row = mProcessesContainer.getChildAt(i);
            } else {
                row = LayoutInflater.from(this).inflate(R.layout.feature_system_item_process, mProcessesContainer, false);
                mProcessesContainer.addView(row);
            }
            ((TextView) row.findViewById(R.id.feature_system_process_name)).setText(
                process.isAppProcess ? getString(R.string.feature_system_monitor_app_process, process.name) : process.name);
            ((TextView) row.findViewById(R.id.feature_system_process_details)).setText(getString(R.string.feature_system_monitor_process_details,
                process.pid, String.format(Locale.US, "%.1f", process.cpuPercent), ProcParsers.formatBytes(process.rssBytes), process.threads));
            MaterialButton kill = row.findViewById(R.id.feature_system_process_kill);
            kill.setOnClickListener(v -> confirmKill(process));
        }
    }

    private void confirmKill(@NonNull SystemSampler.ProcessInfo process) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.feature_system_monitor_kill_title, process.name, process.pid))
            .setMessage(process.isAppProcess ? R.string.feature_system_monitor_kill_app_message :
                R.string.feature_system_monitor_kill_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.feature_system_monitor_terminate, (d, w) -> sendSignal(process, OsConstants.SIGTERM))
            .setPositiveButton(R.string.feature_system_monitor_kill, (d, w) -> sendSignal(process, OsConstants.SIGKILL));
        builder.show();
    }

    private void sendSignal(@NonNull SystemSampler.ProcessInfo process, int signal) {
        try {
            Os.kill(process.pid, signal);
            Logger.logInfo(LOG_TAG, "Sent signal " + signal + " to process " + process.pid + " (" + process.name + ")");
            Logger.showToast(this, getString(R.string.feature_system_monitor_signal_sent, process.name, process.pid), false);
            // Show the result soon, instead of waiting for the next refresh.
            mHandler.removeCallbacks(mRefreshRunnable);
            mHandler.postDelayed(mRefreshRunnable, 300);
        } catch (ErrnoException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to send signal " + signal + " to process " + process.pid, e);
            Logger.showToast(this, getString(R.string.feature_system_monitor_signal_failed, process.name, e.getMessage()), true);
        }
    }

    private static String percent(float value) {
        return String.format(Locale.US, "%.0f%%", value);
    }

    /** The views of a {@code feature_system_item_meter} layout. */
    private static final class Meter {
        final View root;
        final TextView label, value;
        final LinearProgressIndicator progress;

        Meter(@NonNull View root) {
            this.root = root;
            label = root.findViewById(R.id.feature_system_meter_label);
            value = root.findViewById(R.id.feature_system_meter_value);
            progress = root.findViewById(R.id.feature_system_meter_progress);
        }

        /** Show {@code text} and fill the bar to {@code percent}, which is 0-100. */
        void set(@NonNull String text, float percent) {
            value.setText(text);
            progress.setProgressCompat(Math.round(Math.max(0, Math.min(100, percent)) * 10), false);
        }
    }

}
