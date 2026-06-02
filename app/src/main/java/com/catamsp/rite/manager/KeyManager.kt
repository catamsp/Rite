package com.catamsp.rite.manager

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure_keys_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "rite_secure_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"
        private const val PREF_KEY_ARRAY = "keys_array"
    }

    private val selector = ApiKeySelector()
    @Volatile private var cachedKeys: List<String>? = null
    @Volatile private var lastEncryptedSnapshot: String? = null
    @Volatile private var cachedSecretKey: SecretKey? = null

    /** Whether the device's keystore is available for encryption/decryption. */
    var isKeystoreAvailable: Boolean = false
        private set

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
            cachedSecretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            isKeystoreAvailable = cachedSecretKey != null
        } catch (e: Exception) {
            isKeystoreAvailable = false
            android.util.Log.e("Rite", "Keystore unavailable: ${e.message}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        cachedSecretKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.also { cachedSecretKey = it }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun encrypt(plainText: String): String {
        return try {
            val secretKey = getSecretKey()
                ?: throw SecurityException("Android Keystore unavailable — cannot encrypt API keys")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherTextString = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$ivString$IV_SEPARATOR$cipherTextString"
        } catch (e: Exception) {
            android.util.Log.e("Rite", "Key encryption failed: ${e.message}")
            throw e
        }
    }

    private fun decrypt(encryptedString: String): String {
        return try {
            if (!encryptedString.contains(IV_SEPARATOR)) {
                return encryptedString // Assume it's plain text fallback or unencrypted legacy data
            }
            val parts = encryptedString.split(IV_SEPARATOR)
            if (parts.size != 2) return encryptedString

            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val secretKey = getSecretKey()
                ?: throw SecurityException("Android Keystore unavailable — cannot decrypt API keys")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainTextBytes = cipher.doFinal(cipherText)
            String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("Rite", "Key decryption failed: ${e.message}")
            throw e
        }
    }

    fun getKeys(): List<String> {
        val encryptedStr = prefs.getString(PREF_KEY_ARRAY, null) ?: return emptyList()
        if (encryptedStr == lastEncryptedSnapshot) {
            cachedKeys?.let { return it }
        }
        lastEncryptedSnapshot = encryptedStr
        val jsonStr = decrypt(encryptedStr)
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedKeys = list
        return list
    }

    private fun saveKeys(keys: List<String>) {
        val arr = JSONArray(keys)
        val encryptedStr = encrypt(arr.toString())
        prefs.edit().putString(PREF_KEY_ARRAY, encryptedStr).apply()
        cachedKeys = null
        lastEncryptedSnapshot = null
    }

    fun addKey(key: String): Result<Unit> {
        if (!isKeystoreAvailable) {
            return Result.failure(SecurityException("Device security chip unavailable. API keys cannot be stored safely on this device."))
        }
        val keys = getKeys().toMutableList()
        if (!keys.contains(key)) {
            keys.add(key)
            saveKeys(keys)
        }
        selector.clearInvalid(key)
        return Result.success(Unit)
    }

    fun removeKey(key: String) {
        val keys = getKeys().toMutableList()
        keys.remove(key)
        saveKeys(keys)
        selector.markInvalid(key)
    }

    fun getNextKey(): String? = selector.getNextKey(getKeys())

    fun reportRateLimit(key: String, retryAfterSeconds: Long = 60) =
        selector.reportRateLimit(key, retryAfterSeconds)

    fun markInvalid(key: String) = selector.markInvalid(key)

    fun getShortestWaitTimeMs(): Long? = selector.getShortestWaitTimeMs(getKeys())

    @Immutable
    data class KeyStatus(val maskedKey: String, val isReady: Boolean, val remainingMs: Long?)

    fun getKeyStatuses(): List<KeyStatus> = selector.getKeyStatuses(getKeys()).map {
        KeyStatus(it.maskedKey, it.isReady, it.remainingMs)
    }
}
