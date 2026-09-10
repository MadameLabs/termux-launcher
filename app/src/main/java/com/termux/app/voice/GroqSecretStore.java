package com.termux.app.voice;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the Groq key wrapped by an Android Keystore secret that never leaves the TEE.
 *
 * <p>What lands on disk is AES/GCM ciphertext in a private preferences file of its own, so a
 * backup, a debug dump of the ordinary preferences or a stray {@code toString()} cannot carry the
 * key out. The plaintext exists only for the duration of a request.
 */
public final class GroqSecretStore {

    private static final String PREFS_NAME = "termux_voice_secret";
    private static final String KEY_CIPHERTEXT = "groq_api_key";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "termux_voice_groq_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    @NonNull private final Context mContext;

    public GroqSecretStore(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Whether a key is configured, without decrypting or revealing it. */
    public boolean hasKey() {
        return prefs().contains(KEY_CIPHERTEXT);
    }

    /** Stores a key, or clears it when {@code apiKey} is blank. */
    public void setKey(@Nullable String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            prefs().edit().remove(KEY_CIPHERTEXT).apply();
            return;
        }
        try {
            prefs().edit().putString(KEY_CIPHERTEXT, encrypt(apiKey.trim())).apply();
        } catch (Exception e) {
            // Never echo the value or the cause: both can contain the key.
            throw new IllegalStateException("Could not store the Groq key");
        }
    }

    /**
     * @return the plaintext key, or {@code null} when none is stored or it can no longer be
     *     decrypted (the Keystore entry was invalidated, for instance)
     */
    @Nullable
    public String getKey() {
        String stored = prefs().getString(KEY_CIPHERTEXT, null);
        if (stored == null) return null;
        try {
            return decrypt(stored);
        } catch (Exception e) {
            return null;
        }
    }

    private String encrypt(@NonNull String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
        byte[] packed = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    private String decrypt(@NonNull String stored) throws Exception {
        byte[] packed = Base64.decode(stored, Base64.NO_WRAP);
        if (packed.length <= GCM_IV_BYTES) throw new IllegalStateException("Malformed ciphertext");
        byte[] iv = new byte[GCM_IV_BYTES];
        System.arraycopy(packed, 0, iv, 0, GCM_IV_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = cipher.doFinal(packed, GCM_IV_BYTES, packed.length - GCM_IV_BYTES);
        return new String(plaintext, "UTF-8");
    }

    private SecretKey secretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // No user authentication requirement: dictation has to work on a screen already unlocked.
            .build());
        return generator.generateKey();
    }
}
