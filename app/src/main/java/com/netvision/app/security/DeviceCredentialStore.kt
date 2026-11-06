package com.netvision.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DeviceApiCredentials(val username: String, val password: String)

/**
 * Stores per-device RouterOS API credentials encrypted with an AES key held by Android Keystore.
 * Credentials are intentionally kept outside the Room database and are not exported in plain text.
 */
class DeviceCredentialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "netvision_device_credentials",
        Context.MODE_PRIVATE
    )

    fun save(deviceId: String, username: String, password: String) {
        require(deviceId.isNotBlank() && username.isNotBlank() && password.isNotBlank())
        val plain = (username + "\u0000" + password).toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val packed = cipher.iv + encrypted
        preferences.edit().putString(keyFor(deviceId), Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun get(deviceId: String): DeviceApiCredentials? {
        val encoded = preferences.getString(keyFor(deviceId), null) ?: return null
        return runCatching {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > GCM_IV_LENGTH_BYTES)
            val iv = packed.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val encrypted = packed.copyOfRange(GCM_IV_LENGTH_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plain = String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
            val separator = plain.indexOf('\u0000')
            require(separator > 0 && separator < plain.lastIndex)
            DeviceApiCredentials(plain.substring(0, separator), plain.substring(separator + 1))
        }.getOrNull()
    }

    fun delete(deviceId: String) {
        preferences.edit().remove(keyFor(deviceId)).apply()
    }

    private fun keyFor(deviceId: String) = "mikrotik_api_$deviceId"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "netvision_mikrotik_api_credentials_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
