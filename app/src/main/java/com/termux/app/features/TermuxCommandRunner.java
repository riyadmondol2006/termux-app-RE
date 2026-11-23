package com.termux.app.features;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.shared.logger.Logger;
import com.termux.shared.shell.command.environment.ShellEnvironmentUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs commands in the background with the Termux environment ($PREFIX, $HOME, $PATH, ...) and
 * returns their output, for features that need command output without a terminal session, like
 * listing packages or reading `git status`.
 *
 * Commands that need user interaction or show progress should run in a terminal session instead,
 * see {@link TermuxSessionLauncher}.
 */
public final class TermuxCommandRunner {

    private static final String LOG_TAG = "TermuxCommandRunner";

    /** Output above this size is truncated, so a runaway command can't exhaust memory. */
    public static final int MAX_OUTPUT_BYTES = 8 * 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private TermuxCommandRunner() {}

    /** The result of a command. */
    public static final class Result {
        /** The exit code, or -1 if the command could not be started or timed out. */
        public final int exitCode;
        @NonNull public final String stdout;
        @NonNull public final String stderr;
        /** Set if the command could not be started, failed with an exception or timed out. */
        @Nullable public final String error;

        Result(int exitCode, @NonNull String stdout, @NonNull String stderr, @Nullable String error) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.error = error;
        }

        public boolean isSuccessful() {
            return error == null && exitCode == 0;
        }
    }

    /** Called on the main thread with the result of a command. */
    public interface Callback {
        @MainThread
        void onResult(@NonNull Result result);
    }

    /** Whether the bootstrap is installed, so $PREFIX binaries can be run. */
    public static boolean isBootstrapInstalled() {
        return new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH, "sh").canExecute();
    }

    /**
     * Run {@code script} with {@code $PREFIX/bin/sh -c} in the background and call {@code callback}
     * on the main thread.
     */
    public static void runShell(@NonNull Context context, @NonNull String script, @Nullable String workingDirectory,
                                @NonNull Callback callback) {
        run(context, new String[]{TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh", "-c", script},
            workingDirectory, null, 0, callback);
    }

    /**
     * Run {@code command} (the executable path or name in $PATH, followed by its arguments) in the
     * background and call {@code callback} on the main thread.
     *
     * @param workingDirectory The working directory, or {@code null} for $HOME.
     * @param stdin Data written to the command's stdin, or {@code null}.
     * @param timeoutMillis Kill the command after this time, or 0 for no timeout.
     */
    public static void run(@NonNull Context context, @NonNull String[] command, @Nullable String workingDirectory,
                           @Nullable String stdin, long timeoutMillis, @NonNull Callback callback) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result result = runBlocking(appContext, command, workingDirectory, stdin, timeoutMillis);
            MAIN_HANDLER.post(() -> callback.onResult(result));
        });
    }

    /** Like {@link #run}, but blocks the calling thread. Must not be called on the main thread. */
    @WorkerThread
    @NonNull
    public static Result runBlocking(@NonNull Context context, @NonNull String[] command, @Nullable String workingDirectory,
                                     @Nullable String stdin, long timeoutMillis) {
        if (command.length == 0) return new Result(-1, "", "", "No command");
        if (!isBootstrapInstalled())
            return new Result(-1, "", "", "The Termux environment is not installed");

        HashMap<String, String> environment = new TermuxShellEnvironment().getEnvironment(context, false);
        List<String> args = new ArrayList<>();
        String executable = command[0];
        if (!executable.contains("/")) executable = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/" + executable;
        args.add(executable);
        for (int i = 1; i < command.length; i++) args.add(command[i]);

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(new File(workingDirectory != null ? workingDirectory : TermuxConstants.TERMUX_HOME_DIR_PATH));
        builder.environment().clear();
        builder.environment().putAll(environment);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to start " + args, e);
            return new Result(-1, "", "", e.getMessage() != null ? e.getMessage() : e.toString());
        }

        final Process finalProcess = process;
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stderrReader = new Thread(() -> readFully(finalProcess.getErrorStream(), stderr), "TermuxCommandRunner-stderr");
        stderrReader.start();

        try (OutputStream processStdin = process.getOutputStream()) {
            if (stdin != null) processStdin.write(stdin.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // The command may not read its stdin.
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        Thread stdoutReader = new Thread(() -> readFully(finalProcess.getInputStream(), stdout), "TermuxCommandRunner-stdout");
        stdoutReader.start();

        String error = null;
        int exitCode;
        try {
            if (timeoutMillis > 0) {
                if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    process.destroy();
                    error = "Timed out after " + timeoutMillis + " ms";
                }
            }
            exitCode = error == null ? process.waitFor() : -1;
            stdoutReader.join();
            stderrReader.join();
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            return new Result(-1, stdout.toString(), stderr.toString(), "Interrupted");
        }

        return new Result(exitCode, new String(stdout.toByteArray(), StandardCharsets.UTF_8),
            new String(stderr.toByteArray(), StandardCharsets.UTF_8), error);
    }

    private static void readFully(@NonNull InputStream input, @NonNull ByteArrayOutputStream output) {
        byte[] buffer = new byte[8192];
        int read;
        try (InputStream in = input) {
            while ((read = in.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_BYTES - output.size();
                if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
            }
        } catch (IOException e) {
            // The process exited.
        }
    }

    /** Quote {@code value} for use as a single word in a POSIX shell command. */
    @NonNull
    public static String shellQuote(@NonNull String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Get the environment commands run with, for example to pass to another process. */
    @NonNull
    public static List<String> getEnviron(@NonNull Context context) {
        return ShellEnvironmentUtils.convertEnvironmentToEnviron(new TermuxShellEnvironment().getEnvironment(context, false));
    }

}
