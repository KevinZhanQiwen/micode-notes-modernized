/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Singleton that manages the privacy space lock state, auth method, and
 * credential storage. The unlocked state is in-memory only and is cleared
 * whenever the app goes to the background.
 */
public class PrivacySpaceManager {

    public static final String AUTH_TYPE_PATTERN = "pattern";
    public static final String AUTH_TYPE_PIN     = "pin";

    private static final String PREFS_NAME           = "privacy_space_prefs";
    private static final String KEY_IS_SETUP         = "is_setup";
    private static final String KEY_AUTH_TYPE        = "auth_type";
    private static final String KEY_CREDENTIAL_HASH  = "credential_hash";

    private static PrivacySpaceManager sInstance;

    private final SharedPreferences mPrefs;
    private volatile boolean mIsUnlocked = false;

    public static synchronized PrivacySpaceManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PrivacySpaceManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private PrivacySpaceManager(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns true if the user has already configured a lock credential. */
    public boolean isSetup() {
        return mPrefs.getBoolean(KEY_IS_SETUP, false);
    }

    /** Returns the configured auth type, defaulting to PIN. */
    public String getAuthType() {
        return mPrefs.getString(KEY_AUTH_TYPE, AUTH_TYPE_PIN);
    }

    /**
     * Persists the chosen auth type and hashed credential, then auto-unlocks.
     *
     * @param authType   one of {@link #AUTH_TYPE_PATTERN} / {@link #AUTH_TYPE_PIN}
     * @param credential raw pattern string (e.g. "0,3,6,7,8") or 4-digit PIN string
     */
    public void setup(String authType, String credential) {
        mPrefs.edit()
                .putBoolean(KEY_IS_SETUP, true)
                .putString(KEY_AUTH_TYPE, authType)
                .putString(KEY_CREDENTIAL_HASH, hash(credential))
                .apply();
        mIsUnlocked = true;
    }

    /**
     * Verifies the provided credential against the stored hash.
     * Sets the unlocked flag on success.
     */
    public boolean verify(String credential) {
        String stored = mPrefs.getString(KEY_CREDENTIAL_HASH, "");
        boolean match = hash(credential).equals(stored);
        if (match) {
            mIsUnlocked = true;
        }
        return match;
    }

    public boolean isUnlocked() {
        return mIsUnlocked;
    }

    /** Locks the privacy space (clears in-memory unlock flag). */
    public void lock() {
        mIsUnlocked = false;
    }

    /** Completely resets the privacy space setup (for change-password flow). */
    public void reset() {
        mPrefs.edit().clear().apply();
        mIsUnlocked = false;
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on Android
            return input;
        }
    }
}
