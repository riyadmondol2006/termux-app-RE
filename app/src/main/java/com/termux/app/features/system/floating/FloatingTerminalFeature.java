package com.termux.app.features.system.floating;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.features.TermuxFeature;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import java.util.ArrayList;
import java.util.List;

/**
 * The floating terminal, a terminal window over other apps, see {@link FloatingTerminalService}.
 * It needs the "Display over other apps" permission, which can also be granted from
 * Settings → Termux → Permissions.
 */
public final class FloatingTerminalFeature extends TermuxFeature {

    @NonNull
    @Override
    public String getId() {
        return "system_floating_terminal";
    }

    @Override
    public int getTitle() {
        return R.string.feature_system_floating_title;
    }

    @Override
    public int getSummary() {
        return R.string.feature_system_floating_summary;
    }

    @Override
    public int getIcon() {
        return R.drawable.feature_system_ic_float;
    }

    @Override
    public void open(@NonNull TermuxActivity activity) {
        if (!PermissionUtils.checkDisplayOverOtherAppsPermission(activity)) {
            showPermissionDialog(activity);
            return;
        }
        showSessionDialog(activity);
    }

    private static void showPermissionDialog(@NonNull TermuxActivity activity) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_system_floating_permission_title)
            .setMessage(R.string.feature_system_floating_permission_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.feature_system_floating_permission_grant, (dialog, which) -> {
                // The same request as the "Display over other apps" item of the Permissions screen.
                PermissionUtils.requestDisplayOverOtherAppsPermission(activity);
                continueWhenGranted(activity);
            })
            .show();
    }

    /** When the user comes back from the system settings and granted the permission, continue with the session choice. */
    private static void continueWhenGranted(@NonNull TermuxActivity activity) {
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onResume(@NonNull LifecycleOwner owner) {
                activity.getLifecycle().removeObserver(this);
                if (PermissionUtils.checkDisplayOverOtherAppsPermission(activity)) showSessionDialog(activity);
            }
        });
    }

    private static void showSessionDialog(@NonNull TermuxActivity activity) {
        final TerminalSession currentSession = activity.getCurrentSession();
        final boolean hasCurrentSession = currentSession != null && currentSession.isRunning();
        final boolean running = FloatingTerminalService.isRunning();

        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        items.add(activity.getString(R.string.feature_system_floating_new_session));
        actions.add(() -> open(activity, null));
        if (hasCurrentSession) {
            items.add(activity.getString(R.string.feature_system_floating_current_session));
            actions.add(() -> open(activity, currentSession.mHandle));
        }
        if (running) {
            items.add(activity.getString(R.string.feature_system_floating_close));
            actions.add(() -> FloatingTerminalService.close(activity));
        }

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.feature_system_floating_title)
            .setItems(items.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static void open(@NonNull Context context, @Nullable String sessionHandle) {
        try {
            FloatingTerminalService.open(context, sessionHandle);
        } catch (RuntimeException e) {
            Logger.logStackTraceWithMessage("FloatingTerminalFeature", "Failed to open the floating terminal", e);
            Logger.showToast(context, context.getString(R.string.feature_system_floating_failed, e.getMessage()), true);
        }
    }

}
