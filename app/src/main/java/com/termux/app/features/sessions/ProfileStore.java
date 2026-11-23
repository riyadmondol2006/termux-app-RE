package com.termux.app.features.sessions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** The {@link TerminalProfile}s, stored as JSON in {@link SessionsPreferences}. */
public final class ProfileStore {

    private static final String LOG_TAG = "ProfileStore";

    private static List<TerminalProfile> sProfiles;

    private ProfileStore() {}

    @NonNull
    public static synchronized List<TerminalProfile> getProfiles(@NonNull Context context) {
        if (sProfiles == null) sProfiles = load(context);
        return Collections.unmodifiableList(new ArrayList<>(sProfiles));
    }

    @Nullable
    public static synchronized TerminalProfile getProfile(@NonNull Context context, @Nullable String id) {
        if (id == null) return null;
        for (TerminalProfile profile : getProfiles(context)) {
            if (profile.id.equals(id)) return profile;
        }
        return null;
    }

    @NonNull
    public static TerminalProfile newProfile() {
        return new TerminalProfile(UUID.randomUUID().toString());
    }

    /** Add {@code profile}, or replace the profile with the same id. */
    public static synchronized void saveProfile(@NonNull Context context, @NonNull TerminalProfile profile) {
        getProfiles(context);
        boolean replaced = false;
        for (int i = 0; i < sProfiles.size(); i++) {
            if (sProfiles.get(i).id.equals(profile.id)) {
                sProfiles.set(i, profile);
                replaced = true;
            }
        }
        if (!replaced) sProfiles.add(profile);
        save(context);
        SessionStartup.writeProfileStartupFile(profile);
    }

    public static synchronized void deleteProfile(@NonNull Context context, @NonNull String id) {
        getProfiles(context);
        for (int i = 0; i < sProfiles.size(); i++) {
            if (sProfiles.get(i).id.equals(id)) {
                sProfiles.remove(i);
                break;
            }
        }
        if (id.equals(getDefaultProfileId(context))) setDefaultProfileId(context, null);
        save(context);
        SessionStartup.deleteProfileStartupFile(id);
    }

    /** Get the id of the profile that the New session button uses, or {@code null} for none. */
    @Nullable
    public static String getDefaultProfileId(@NonNull Context context) {
        String id = SessionsPreferences.get(context).getString(SessionsPreferences.KEY_DEFAULT_PROFILE, null);
        return getProfile(context, id) != null ? id : null;
    }

    public static void setDefaultProfileId(@NonNull Context context, @Nullable String id) {
        SessionsPreferences.get(context).edit().putString(SessionsPreferences.KEY_DEFAULT_PROFILE, id).apply();
    }

    @NonNull
    private static List<TerminalProfile> load(@NonNull Context context) {
        List<TerminalProfile> profiles = new ArrayList<>();
        String json = SessionsPreferences.get(context).getString(SessionsPreferences.KEY_PROFILES, null);
        if (json == null) return profiles;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                TerminalProfile profile = TerminalProfile.fromJson(array.getJSONObject(i));
                if (profile != null) profiles.add(profile);
            }
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load profiles", e);
        }
        return profiles;
    }

    private static void save(@NonNull Context context) {
        JSONArray array = new JSONArray();
        try {
            for (TerminalProfile profile : sProfiles) array.put(profile.toJson());
        } catch (JSONException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to save profiles", e);
            return;
        }
        SessionsPreferences.get(context).edit().putString(SessionsPreferences.KEY_PROFILES, array.toString()).apply();
    }

}
