package com.termux.app.features.system.floating;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.TermuxService;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

/**
 * Shows a terminal session in a {@link FloatingTerminalWindow} over other apps. It is a foreground
 * service so the window stays while the app is in the background, and binds to
 * {@link TermuxService} for the sessions.
 *
 * Closing the window only ends this service: its session keeps running and stays in the session
 * list of the app.
 */
public final class FloatingTerminalService extends Service implements ServiceConnection, FloatingTerminalWindow.Callbacks {

    private static final String ACTION_OPEN = "com.termux.re.system.floating.OPEN";
    private static final String ACTION_RESTORE = "com.termux.re.system.floating.RESTORE";
    private static final String ACTION_CLOSE = "com.termux.re.system.floating.CLOSE";
    private static final String EXTRA_SESSION_HANDLE = "session_handle";

    private static final String NOTIFICATION_CHANNEL_ID = "termux_system_floating_terminal";
    private static final int NOTIFICATION_ID = 1347;

    /** Same limit as the session list of the activity. */
    private static final int MAX_SESSIONS = 8;

    private static final String LOG_TAG = "FloatingTerminalService";

    private static volatile boolean sRunning;

    @Nullable private TermuxService mTermuxService;
    private boolean mBound;
    @Nullable private FloatingTerminalWindow mWindow;
    @Nullable private TerminalSession mSession;
    @Nullable private FloatingSessionClient mSessionClient;
    /** What to show when the {@link TermuxService} is connected: the handle of a session, or {@code null} for a new one. */
    @Nullable private String mPendingSessionHandle;
    private boolean mHasPendingRequest;

    /** Whether the floating terminal is open. */
    public static boolean isRunning() {
        return sRunning;
    }

    /**
     * Open the floating terminal with the session of {@code sessionHandle}, or with a new session if
     * it is {@code null}. Needs the "Display over other apps" permission.
     */
    public static void open(@NonNull Context context, @Nullable String sessionHandle) {
        Intent intent = new Intent(context, FloatingTerminalService.class).setAction(ACTION_OPEN);
        if (sessionHandle != null) intent.putExtra(EXTRA_SESSION_HANDLE, sessionHandle);
        startService(context, intent);
    }

    /** Close the floating terminal. Its session keeps running. */
    public static void close(@NonNull Context context) {
        if (sRunning) context.startService(new Intent(context, FloatingTerminalService.class).setAction(ACTION_CLOSE));
    }

    private static void startService(@NonNull Context context, @NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }



    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        // A service started with startForegroundService() must call startForeground() soon.
        NotificationUtils.setupNotificationChannel(this, NOTIFICATION_CHANNEL_ID,
            getString(R.string.feature_system_floating_channel), NotificationManager.IMPORTANCE_LOW);
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_CLOSE.equals(action) || action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!PermissionUtils.checkDisplayOverOtherAppsPermission(this)) {
            Logger.logWarn(LOG_TAG, "The Display over other apps permission is not granted");
            Logger.showToast(this, getString(R.string.feature_system_floating_permission_missing), true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_RESTORE.equals(action)) {
            if (mWindow != null) mWindow.restore();
        } else if (ACTION_OPEN.equals(action)) {
            mPendingSessionHandle = intent.getStringExtra(EXTRA_SESSION_HANDLE);
            mHasPendingRequest = true;
            if (mTermuxService != null) {
                openPendingSession();
            } else if (!mBound) {
                Intent serviceIntent = new Intent(this, TermuxService.class);
                // The terminal service is normally running already, this starts it if the user exited it.
                startService(serviceIntent);
                mBound = bindService(serviceIntent, this, 0);
                if (!mBound) {
                    Logger.logError(LOG_TAG, "Failed to bind to TermuxService");
                    stopSelf();
                }
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mWindow != null) mWindow.clampToScreen();
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        detachSession();
        if (mWindow != null) {
            mWindow.destroy();
            mWindow = null;
        }
        if (mBound) {
            unbindService(this);
            mBound = false;
        }
        mTermuxService = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mTermuxService = ((TermuxService.LocalBinder) service).service;
        if (mHasPendingRequest) openPendingSession();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // The terminal service was destroyed, which ends all sessions.
        mTermuxService = null;
        stopSelf();
    }



    private void openPendingSession() {
        mHasPendingRequest = false;
        TermuxService termuxService = mTermuxService;
        if (termuxService == null) return;

        TerminalSession session = null;
        if (mPendingSessionHandle != null) session = termuxService.getTerminalSessionForHandle(mPendingSessionHandle);
        if (session == null) session = createSession(termuxService);
        if (session == null) {
            // Nothing to show.
            if (mSession == null) stopSelf();
            return;
        }

        detachSession();
        mSession = session;
        if (mWindow == null) {
            mWindow = new FloatingTerminalWindow(this, this);
            mWindow.show();
        }
        mSessionClient = new FloatingSessionClient(this, mWindow);
        mWindow.attachSession(session);
        termuxService.setSessionClientWrapper(session, mSessionClient);
        mWindow.restore();
    }

    @Nullable
    private TerminalSession createSession(@NonNull TermuxService termuxService) {
        if (termuxService.getTermuxSessionsSize() >= MAX_SESSIONS) {
            Logger.showToast(this, getString(R.string.msg_max_terminals_reached), true);
            return null;
        }
        // A null working directory is the default working directory of the app.
        TermuxSession termuxSession = termuxService.createTermuxSession(null, null, null, null, false,
            getString(R.string.feature_system_floating_session_name));
        return termuxSession != null ? termuxSession.getTerminalSession() : null;
    }

    private void detachSession() {
        if (mTermuxService != null && mSession != null)
            mTermuxService.setSessionClientWrapper(mSession, null);
        mSession = null;
        mSessionClient = null;
    }



    @Override
    public void onCloseRequested() {
        stopSelf();
    }

    @Override
    public void onFinishedSessionEnter(@NonNull TerminalSession session) {
        // Like pressing Enter in the activity: remove the finished session, and close the window.
        TermuxService termuxService = mTermuxService;
        detachSession();
        if (termuxService != null) termuxService.removeTermuxSession(session);
        stopSelf();
    }

    @NonNull
    private Notification buildNotification() {
        PendingIntent restoreIntent = PendingIntent.getService(this, 0,
            new Intent(this, FloatingTerminalService.class).setAction(ACTION_RESTORE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent closeIntent = PendingIntent.getService(this, 1,
            new Intent(this, FloatingTerminalService.class).setAction(ACTION_CLOSE), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.feature_system_floating_notification_title))
            .setContentText(getString(R.string.feature_system_floating_notification_text))
            .setSmallIcon(R.drawable.ic_service_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(restoreIntent)
            .addAction(0, getString(R.string.feature_system_floating_close), closeIntent)
            .build();
    }

}
