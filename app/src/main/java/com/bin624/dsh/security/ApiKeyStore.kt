package com.bin624.dsh.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the API key encrypted with an AES-GCM key protected by Android Keystore.
 * The encryption key is non-exportable and never leaves this device.
 */
class ApiKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun read(): String? {
        val stored = preferences.getString(ENTRY_KEY, null) ?: return null
        val parts = stored.split(":", limit = 2)
        require(parts.size == 2) { "本地密钥数据格式错误。" }

        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun save(apiKey: String) {
        require(apiKey.isNotBlank()) { "API Key 不能为空。" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        val stored = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)

        check(preferences.edit().putString(ENTRY_KEY, stored).commit()) {
            "无法写入本地安全存储。"
        }
    }

    fun clear() {
        check(preferences.edit().remove(ENTRY_KEY).commit()) {
            "无法清除本地 API Key。"
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "deepseek_secure_preferences"
        const val ENTRY_KEY = "encrypted_api_key"
        const val KEY_ALIAS = "com.bin624.dsh.deepseek.api_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
