package com.termux.app.features.sessions;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Shows the notifications that programs in terminal sessions ask for with the `OSC 9` and
 * `OSC 777;notify` escape sequences, like the command notifications of the shell integration, see
 * {@link SessionLaunchPlan}. They are only shown if the session is not in view, in their own
 * notification channel, and if the user allowed notifications. Tapping one shows the session.
 */
public final class SessionNotifications {

    private static final String LOG_TAG = "SessionNotifications";

    public static final String CHANNEL_ID = "termux_re_terminal_notifications";

    /** Notifications are tagged with this and the session handle, so each session has one notification. */
    private static final String TAG_PREFIX = "termux_re_session:";
    private static final int NOTIFICATION_ID = 1;
    private static final String GROUP_KEY = "termux_re_terminal_notifications";

    static final int MAX_TITLE_LENGTH = 100;
    static final int MAX_BODY_LENGTH = 1000;
    /** Notifications of a session within this time of the last one are dropped, against floods. */
    static final long MIN_INTERVAL_MILLIS = 1000;

    private static final Map<String, Long> sLastNotificationTimes = new HashMap<>();

    /** The activity while it is visible, to switch sessions from a notification. */
    @Nullable private static WeakReference<TermuxActivity> sVisibleActivity;

    private SessionNotifications() {}

    /**
     * Called when a program in {@code session} asked for a notification.
     *
     * @param sessionInView Whether the session is shown in the visible activity, in which case no
     *                      notification is shown.
     */
    @MainThread
    public static void onTerminalNotification(@NonNull Context context, @NonNull TerminalSession session,
                                              @Nullable String title, @Nullable String body, boolean sessionInView) {
        if (sessionInView || !SessionsPreferences.areNotificationsEnabled(context)) return;

        if (!shouldNotify(sLastNotificationTimes, session.mHandle, SystemClock.elapsedRealtime())) {
            Logger.logVerbose(LOG_TAG, "Ignoring notification of session sent too soon after the last one");
            return;
        }

        Context appContext = context.getApplicationContext();
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Logger.logInfo(LOG_TAG, "Not showing terminal notification since notifications are disabled");
            return;
        }
        setupNotificationChannel(appContext);

        String sessionName = getSessionName(appContext, session);
        String cleanTitle = clean(title, MAX_TITLE_LENGTH);
        String cleanBody = clean(body, MAX_BODY_LENGTH);
        if (cleanTitle.isEmpty()) {
            cleanTitle = cleanBody.isEmpty() ? appContext.getString(R.string.feature_sessions_notification_default_title) : sessionName;
            if (cleanBody.isEmpty()) cleanBody = sessionName;
        }

        Intent intent = new Intent(appContext, SessionNotificationActivity.class)
            .putExtra(SessionNotificationActivity.EXTRA_SESSION_HANDLE, session.mHandle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(appContext, session.mHandle.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(cleanTitle)
            .setContentText(cleanBody)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Not bundled by Android with the ongoing app notification, which opens it without this session.
            .setGroup(GROUP_KEY)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        if (!cleanTitle.equals(sessionName)) builder.setSubText(sessionName);

        try {
            NotificationManagerCompat.from(appContext).notify(TAG_PREFIX + session.mHandle, NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            // The notification permission was revoked.
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to show terminal notification", e);
        }
    }

    /** Remove the notification of {@code session}, like when it is shown. */
    public static void cancel(@NonNull Context context, @NonNull String sessionHandle) {
        NotificationManagerCompat.from(context.getApplicationContext()).cancel(TAG_PREFIX + sessionHandle, NOTIFICATION_ID);
        sLastNotificationTimes.remove(sessionHandle);
    }

    /** Create the notification channel of terminal notifications, if it does not exist. */
    public static void setupNotificationChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            context.getString(R.string.feature_sessions_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.feature_sessions_notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    /** Whether notifications can be shown: allowed for the app and the channel is not blocked. */
    public static boolean canShowNotifications(@NonNull Context context) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (!manager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = notificationManager != null ? notificationManager.getNotificationChannel(CHANNEL_ID) : null;
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    public static void setVisibleActivity(@Nullable TermuxActivity activity) {
        sVisibleActivity = activity == null ? null : new WeakReference<>(activity);
    }

    @Nullable
    static TermuxActivity getVisibleActivity() {
        TermuxActivity activity = sVisibleActivity != null ? sVisibleActivity.get() : null;
        return activity != null && activity.isVisible() ? activity : null;
    }

    /**
     * Check if a notification of {@code key} should be shown at {@code now}, which is not the case
     * within {@link #MIN_INTERVAL_MILLIS} of the last one. If it should, {@code now} is stored.
     */
    static <K> boolean shouldNotify(@NonNull Map<K, Long> times, @NonNull K key, long now) {
        Long last = times.get(key);
        if (last != null && now >= last && now - last < MIN_INTERVAL_MILLIS) return false;
        times.put(key, now);
        return true;
    }

    /** Remove control characters from {@code text} and limit its length. */
    @NonNull
    static String clean(@Nullable String text, int maxLength) {
        if (text == null) return "";
        StringBuilder builder = new StringBuilder(Math.min(text.length(), maxLength + 1));
        for (int i = 0; i < text.length() && builder.length() <= maxLength; i++) {
            char c = text.charAt(i);
            builder.append(Character.isISOControl(c) ? ' ' : c);
        }
        String result = builder.toString().trim();
        if (result.length() > maxLength) {
            int end = maxLength;
            if (Character.isHighSurrogate(result.charAt(end - 1))) end--;
            result = result.substring(0, end) + "…";
        }
        return result;
    }

    @NonNull
    private static String getSessionName(@NonNull Context context, @NonNull TerminalSession session) {
        if (!TextUtils.isEmpty(session.mSessionName)) return session.mSessionName;
        String title = session.getTitle();
        if (!TextUtils.isEmpty(title)) return clean(title, MAX_TITLE_LENGTH);
        return context.getString(R.string.feature_sessions_notification_default_title);
    }

}
