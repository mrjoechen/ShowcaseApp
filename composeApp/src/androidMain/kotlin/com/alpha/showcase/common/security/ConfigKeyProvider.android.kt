package com.alpha.showcase.common.security

import AndroidApp
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual object ConfigKeyProvider {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_KEY_ALIAS = "showcase.config.master.v2"
    private const val PREF_NAME = "showcase_config_crypto"
    private const val PREF_WRAPPED_KEY = "wrapped_config_key_v2"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    actual fun getOrCreateKeyMaterial(): ByteArray {
        val preferences = AndroidApp.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val wrapped = preferences.getString(PREF_WRAPPED_KEY, null)

        if (!wrapped.isNullOrBlank()) {
            decryptWrappedKey(wrapped)?.let { return it }
            preferences.edit().remove(PREF_WRAPPED_KEY).apply()
            resetMasterKey()
        }

        val generatedKey = ByteArray(CONFIG_KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val encrypted = encryptKeyMaterial(generatedKey)
        preferences.edit().putString(PREF_WRAPPED_KEY, encrypted).apply()
        return generatedKey
    }

    private fun encryptKeyMaterial(keyMaterial: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(keyMaterial)
        val payload = ByteArray(cipher.iv.size + encrypted.size).also { output ->
            cipher.iv.copyInto(output, destinationOffset = 0)
            encrypted.copyInto(output, destinationOffset = cipher.iv.size)
        }
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decryptWrappedKey(wrapped: String): ByteArray? {
        return runCatching {
            val payload = Base64.decode(wrapped, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) {
                return null
            }
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateMasterKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            val decoded = cipher.doFinal(encrypted)
            if (decoded.size == CONFIG_KEY_SIZE_BYTES) decoded else null
        }.getOrNull()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = (keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existing != null) {
            return existing
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun resetMasterKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }
}
