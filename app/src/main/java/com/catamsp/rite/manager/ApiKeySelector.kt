package com.catamsp.rite.manager

import androidx.compose.runtime.Immutable
import java.util.concurrent.atomic.AtomicInteger

class ApiKeySelector {

    private val rateLimitedKeys = mutableMapOf<String, Long>()
    private val roundRobinIndex = AtomicInteger(0)

    fun reportRateLimit(key: String, retryAfterSeconds: Long = 60) {
        val cooldown = retryAfterSeconds.coerceIn(1, 600)
        rateLimitedKeys[key] = System.currentTimeMillis() + cooldown * 1_000
    }

    fun getNextKey(keys: List<String>): String? {
        if (keys.isEmpty()) return null

        val now = System.currentTimeMillis()
        val validKeys = keys.filter { key ->
            val limitTime = rateLimitedKeys[key] ?: 0L
            now > limitTime
        }

        if (validKeys.isEmpty()) return null

        val idx = (roundRobinIndex.getAndIncrement() and Int.MAX_VALUE) % validKeys.size
        return validKeys[idx]
    }

    fun getKeysForProvider(keys: List<String>, provider: String): List<String> {
        return keys.filter { detectProvider(it) == provider }
    }

    fun getNextKeyForProvider(keys: List<String>, provider: String): String? {
        val providerKeys = getKeysForProvider(keys, provider)
        return getNextKey(providerKeys)
    }

    fun getShortestWaitTimeMs(keys: List<String>): Long? {
        if (keys.isEmpty()) return null
        val now = System.currentTimeMillis()
        val waits = keys.mapNotNull { key ->
            val limitTime = rateLimitedKeys[key] ?: return@mapNotNull null
            val remaining = limitTime - now
            if (remaining > 0) remaining else null
        }
        return waits.minOrNull()
    }

    fun getKeyStatuses(keys: List<String>): List<KeyStatus> {
        val now = System.currentTimeMillis()
        return keys.map { key ->
            val limitTime = rateLimitedKeys[key] ?: 0L
            val remaining = if (limitTime > now) limitTime - now else 0L
            KeyStatus(
                maskedKey = key,
                isReady = limitTime <= now,
                remainingMs = if (limitTime > now) remaining else null
            )
        }
    }

    fun reset() {
        rateLimitedKeys.clear()
        roundRobinIndex.set(0)
    }

    companion object {
        fun detectProvider(key: String): String {
            return when {
                key.startsWith("AIza") -> "gemini"
                key.startsWith("gsk_") -> "groq"
                key.startsWith("csk-") -> "cerebras"
                key.startsWith("kilo_") || (key.startsWith("eyJ") && key.contains(".")) -> "kilo"
                else -> "custom"
            }
        }
    }

    @Immutable
    data class KeyStatus(val maskedKey: String, val isReady: Boolean, val remainingMs: Long?)
}
