package dev.quietflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Key stays in Android Keystore, ciphertext stays in private preferences; backups disabled. */
final class SettingsStore {
    private static final String KEY_ALIAS = "quietflow_jev_api_v1";
    private final SharedPreferences prefs;
    SettingsStore(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    boolean active() { return prefs.getBoolean("active", false); }
    void active(boolean value) { prefs.edit().putBoolean("active", value).apply(); }
    boolean cloudConsent() { return prefs.getBoolean("cloud_consent", false); }
    void cloudConsent(boolean value) { prefs.edit().putBoolean("cloud_consent", value).apply(); }
    boolean hasApiKey() { return !prefs.getString("api_key", "").isEmpty(); }
    void removeKey() { prefs.edit().remove("api_key").apply(); }
    void saveKey(String input) throws Exception {
        if (input.isEmpty()) { removeKey(); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        prefs.edit().putString("api_key", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)).apply();
    }
    String readKey() {
        try {
            String value = prefs.getString("api_key", "");
            if (value.isEmpty()) return "";
            String[] parts = value.split(":", 2);
            if (parts.length != 2) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ex) { return ""; } // Never send requests with invalid key state.
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
}
