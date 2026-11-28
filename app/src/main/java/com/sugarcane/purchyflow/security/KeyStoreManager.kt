package com.sugarcane.purchyflow.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sugarcane.purchyflow.util.SecureLog
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore System Integration for Database Encryption.
 *
 * Generates and stores a cryptographic AES-256 master key directly inside
 * the device's hardware-backed Trusted Execution Environment (TEE) or StrongBox Keymaster.
 *
 * The SQLCipher database passphrase is generated with cryptographically secure random bytes
 * and encrypted using AES-GCM (Galois/Counter Mode) with the hardware Master Key.
 *
 * ZERO hardcoded keys in source code.
 */
object KeyStoreManager {

    private const val TAG = "KeyStoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "PurchyFlow_Room_Master_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private const val PREFS_NAME = "purchyflow_vault_prefs"
    private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
    private const val KEY_IV = "enc_db_iv"

    @Synchronized
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        if (encryptedBase64 != null && ivBase64 != null) {
            try {
                val encryptedBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                val secretKey = getOrCreateMasterKey()

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val decryptedPassphrase = cipher.doFinal(encryptedBytes)
                return decryptedPassphrase
            } catch (e: Exception) {
                SecureLog.e(TAG, "Error decrypting database passphrase from KeyStore: ${e.message}", e)
            }
        }

        // Generate a new 256-bit (32-byte) cryptographic passphrase
        val newPassphrase = ByteArray(32)
        SecureRandom().nextBytes(newPassphrase)

        try {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(newPassphrase)

            prefs.edit()
                .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.DEFAULT))
                .apply()

            SecureLog.i(TAG, "New SQLCipher 256-bit passphrase generated and protected with Android KeyStore.")
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed to persist encrypted passphrase in KeyStore: ${e.message}", e)
        }

        return newPassphrase
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        // Create a new AES-256 key inside AndroidKeyStore hardware TEE
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
}
