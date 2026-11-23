package com.termux.app.features.packages;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.features.TermuxSessionLauncher;
import com.termux.shared.termux.TermuxConstants;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.lang.ref.WeakReference;

import static com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE;

final class PackageTerminalLauncher {

    private static WeakReference<TermuxActivity> terminalActivity = new WeakReference<>(null);

    private PackageTerminalLauncher() {}

    static void setTerminalActivity(@NonNull TermuxActivity activity) {
        terminalActivity = new WeakReference<>(activity);
    }

    static void run(@NonNull Activity owner, @NonNull String name, @NonNull String command) {
        TermuxActivity activity = terminalActivity.get();
        if (activity != null && !activity.isDestroyed() && activity.getTermuxService() != null) {
            activity.startActivity(new Intent(owner, TermuxActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            TerminalSession session = TermuxSessionLauncher.runInNewSession(activity, name, command, true,
                TermuxConstants.TERMUX_HOME_DIR_PATH);
            if (session != null) activity.getPreferences().setCurrentSession(session.mHandle);
            return;
        }

        Intent intent = new Intent(owner, TermuxService.class);
        intent.setAction(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
        intent.setData(Uri.fromFile(new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "bash")));
        intent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, new String[]{"-l", "-c", command + "\nexec " +
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/bash -l"});
        intent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TermuxConstants.TERMUX_HOME_DIR_PATH);
        intent.putExtra(TERMUX_SERVICE.EXTRA_RUNNER, "terminal_session");
        intent.putExtra(TERMUX_SERVICE.EXTRA_SHELL_NAME, name);
        intent.putExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION,
            Integer.toString(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY));
        owner.startService(intent);
    }

}
