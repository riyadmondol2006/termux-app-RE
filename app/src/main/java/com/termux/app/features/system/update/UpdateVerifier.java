package com.termux.app.features.system.update;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Checks of a downloaded update, free of Android classes so they can be tested. */
public final class UpdateVerifier {

    private static final Pattern SAFE_APK_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._+-]*\\.apk$");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private UpdateVerifier() {}

    /** Get a new SHA-256 digest. */
    @NonNull
    public static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Get the lowercase hex of {@code bytes}. */
    @NonNull
    public static String toHex(@NonNull byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            chars[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            chars[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(chars);
    }

    /** Get the lowercase hex SHA-256 of everything in {@code in}. */
    @NonNull
    public static String sha256Hex(@NonNull InputStream in) throws IOException {
        MessageDigest digest = newSha256();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        return toHex(digest.digest());
    }

    /** Whether {@code actual} is the expected SHA-256, which is compared ignoring case. */
    public static boolean checksumMatches(@NonNull String expectedSha256, @NonNull String actualSha256) {
        return expectedSha256.trim().equalsIgnoreCase(actualSha256.trim());
    }

    /**
     * Whether an APK may be downloaded from {@code url}: only files of the releases of the
     * repository at {@code repoUrl} over https, so a manipulated release list can't point the app
     * to another server.
     */
    public static boolean isTrustedDownloadUrl(@Nullable String url, @Nullable String repoUrl) {
        if (url == null || repoUrl == null) return false;
        String repo = repoUrl.trim().replaceAll("(\\.git)?/*$", "");
        if (!repo.startsWith("https://github.com/")) return false;
        String prefix = repo + "/releases/download/";
        return url.startsWith(prefix) && url.length() > prefix.length() && !url.contains("..") && url.indexOf('\n') < 0;
    }

    /** Whether {@code name} is a plain APK file name that is safe to use as a file name in the app's cache. */
    public static boolean isSafeApkName(@Nullable String name) {
        return name != null && name.length() <= 128 && SAFE_APK_NAME.matcher(name).matches();
    }

}
