package net.rcdevgames.simpletunnel.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encrypted credential store backed by Android Keystore.
 */
class EncryptedCredentialStore(private val context: Context) {
    private val PREFS_NAME = "encrypted_tunnel_creds"
    private val KEY_ALIAS = "tunnel_keeper_aes_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    private val IV_SIZE = 12
    private val TAG_SIZE = 128

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val secretKey: SecretKey by lazy {
        getOrCreateKey()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
    }

    private fun decrypt(encrypted: String): String? {
        return try {
            val parts = encrypted.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun savePassword(tunnelId: Long, password: String) {
        prefs.edit { putString("password_$tunnelId", encrypt(password)) }
    }

    fun getPassword(tunnelId: Long): String? {
        val encrypted = prefs.getString("password_$tunnelId", null) ?: return null
        return decrypt(encrypted)
    }

    fun deletePassword(tunnelId: Long) {
        prefs.edit { remove("password_$tunnelId") }
    }

    fun savePrivateKey(tunnelId: Long, privateKey: String) {
        prefs.edit { putString("privatekey_$tunnelId", encrypt(privateKey)) }
    }

    fun getPrivateKey(tunnelId: Long): String? {
        val encrypted = prefs.getString("privatekey_$tunnelId", null) ?: return null
        return decrypt(encrypted)
    }

    fun deletePrivateKey(tunnelId: Long) {
        prefs.edit { remove("privatekey_$tunnelId") }
    }

    fun deleteAllCredentials(tunnelId: Long) {
        prefs.edit()
            .remove("password_$tunnelId")
            .remove("privatekey_$tunnelId")
            .apply()
    }
}
