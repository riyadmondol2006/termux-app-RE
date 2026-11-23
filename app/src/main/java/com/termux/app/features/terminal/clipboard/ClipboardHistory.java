package com.termux.app.features.terminal.clipboard;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * The history of text copied in the app, and of text read from the clipboard to paste it.
 *
 * Android 10+ does not let apps read the clipboard in the background, so text that other apps copy
 * is only added when it is pasted in the app. The history is stored in the private preferences of
 * the app, which are not backed up, and can be turned off and cleared.
 */
public final class ClipboardHistory {

    private static final String LOG_TAG = "ClipboardHistory";

    static final String PREFERENCES_NAME = "feature_terminal_clipboard_history";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_ITEMS = "items";

    private static ClipboardHistory sInstance;

    private final SharedPreferences mPreferences;
    private final ClipboardHistoryList mList = new ClipboardHistoryList();
    private boolean mEnabled;

    ClipboardHistory(@NonNull Context context) {
        mPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        mEnabled = mPreferences.getBoolean(KEY_ENABLED, true);
        if (mEnabled) mList.addAll(parseItems(mPreferences.getString(KEY_ITEMS, null)));
    }

    @NonNull
    public static synchronized ClipboardHistory getInstance(@NonNull Context context) {
        if (sInstance == null) sInstance = new ClipboardHistory(context.getApplicationContext());
        return sInstance;
    }

    /** Start adding the text copied to or read from the clipboard by the app to the history. */
    public static void install(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        ShareUtils.setClipboardTextListener((listenerContext, text) -> getInstance(appContext).add(text));
    }

    public synchronized boolean isEnabled() {
        return mEnabled;
    }

    /** Turn the history on or off. Turning it off clears it. */
    public synchronized void setEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!enabled) mList.clear();
        mPreferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
        save();
    }

    /** Get the items, most recent first. */
    @NonNull
    public synchronized List<String> getItems() {
        return mList.getItems();
    }

    /** Add {@code text} as the most recent item, if the history is on. */
    public synchronized void add(@Nullable String text) {
        if (mEnabled && mList.add(text)) save();
    }

    public synchronized void remove(@Nullable String text) {
        if (mList.remove(text)) save();
    }

    public synchronized void clear() {
        mList.clear();
        save();
    }

    private void save() {
        mPreferences.edit().putString(KEY_ITEMS, toJson(mList.getItems())).apply();
    }

    @NonNull
    static String toJson(@NonNull List<String> items) {
        return new JSONArray(items).toString();
    }

    @NonNull
    static List<String> parseItems(@Nullable String json) {
        List<String> items = new ArrayList<>();
        if (json == null || json.isEmpty()) return items;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, null);
                if (item != null) items.add(item);
            }
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read the clipboard history", e);
        }
        return items;
    }

}
