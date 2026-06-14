package com.catamsp.rite.manager

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiKeySelectorTest {

    private lateinit var selector: ApiKeySelector

    @Before
    fun setup() {
        selector = ApiKeySelector()
    }

    // ── reportRateLimit ──────────────────────────────────────
    @Test
    fun reportRateLimit_basic_cooldownApplied() {
        selector.reportRateLimit("key1", 60)
        val keys = listOf("key1")
        assertNull(selector.getNextKey(keys))
    }

    @Test
    fun reportRateLimit_coerceIn_minimum1Second() {
        selector.reportRateLimit("key1", 0)
        val keys = listOf("key1")
        assertNull(selector.getNextKey(keys))
    }

    @Test
    fun reportRateLimit_coerceIn_maximum600Seconds() {
        selector.reportRateLimit("key1", 9999)
        val keys = listOf("key1")
        assertNull(selector.getNextKey(keys))
    }

    // ── getNextKey ───────────────────────────────────────────
    @Test
    fun getNextKey_emptyList_returnsNull() {
        assertNull(selector.getNextKey(emptyList()))
    }

    @Test
    fun getNextKey_singleKey_returnsIt() {
        assertEquals("key1", selector.getNextKey(listOf("key1")))
    }

    @Test
    fun getNextKey_multipleKeys_roundRobin() {
        val keys = listOf("key1", "key2", "key3")
        val first = selector.getNextKey(keys)
        val second = selector.getNextKey(keys)
        val third = selector.getNextKey(keys)
        val fourth = selector.getNextKey(keys)
        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)
        assertEquals(first, fourth)
    }

    @Test
    fun getNextKey_allRateLimited_returnsNull() {
        selector.reportRateLimit("key1", 60)
        selector.reportRateLimit("key2", 60)
        assertNull(selector.getNextKey(listOf("key1", "key2")))
    }

    // ── getShortestWaitTimeMs ────────────────────────────────
    @Test
    fun getShortestWaitTimeMs_emptyList_returnsNull() {
        assertNull(selector.getShortestWaitTimeMs(emptyList()))
    }

    @Test
    fun getShortestWaitTimeMs_noRateLimits_returnsNull() {
        assertNull(selector.getShortestWaitTimeMs(listOf("key1", "key2")))
    }

    @Test
    fun getShortestWaitTimeMs_withRateLimit_returnsRemaining() {
        selector.reportRateLimit("key1", 60)
        val wait = selector.getShortestWaitTimeMs(listOf("key1"))
        assertNotNull(wait)
        assertTrue(wait!! > 0)
        assertTrue(wait <= 60_000)
    }

    @Test
    fun getShortestWaitTimeMs_shortestWins() {
        selector.reportRateLimit("key1", 120)
        selector.reportRateLimit("key2", 30)
        val wait = selector.getShortestWaitTimeMs(listOf("key1", "key2"))
        assertNotNull(wait)
        assertTrue(wait!! <= 30_000)
    }

    // ── getKeyStatuses ───────────────────────────────────────
    @Test
    fun getKeyStatuses_readyKey_isReady() {
        val statuses = selector.getKeyStatuses(listOf("key1"))
        assertEquals(1, statuses.size)
        assertTrue(statuses[0].isReady)
        assertNull(statuses[0].remainingMs)
    }

    @Test
    fun getKeyStatuses_rateLimitedKey_notReady() {
        selector.reportRateLimit("key1", 60)
        val statuses = selector.getKeyStatuses(listOf("key1"))
        assertEquals(1, statuses.size)
        assertFalse(statuses[0].isReady)
        assertNotNull(statuses[0].remainingMs)
    }

    // ── reset ────────────────────────────────────────────────
    @Test
    fun reset_clearsAll() {
        selector.reportRateLimit("key1", 60)
        selector.reset()
        val keys = listOf("key1", "key2")
        assertEquals("key1", selector.getNextKey(keys))
    }

    // ── detectProvider ───────────────────────────────────────
    @Test
    fun detectProvider_geminiKey() {
        assertEquals("gemini", ApiKeySelector.detectProvider("AIzaSytest123"))
    }

    @Test
    fun detectProvider_groqKey() {
        assertEquals("groq", ApiKeySelector.detectProvider("gsk_test123"))
    }

    @Test
    fun detectProvider_cerebrasKey() {
        assertEquals("cerebras", ApiKeySelector.detectProvider("csk-test123"))
    }

    @Test
    fun detectProvider_kiloKey() {
        assertEquals("kilo", ApiKeySelector.detectProvider("kilo_test123"))
    }

    @Test
    fun detectProvider_kiloJwtKey() {
        assertEquals("kilo", ApiKeySelector.detectProvider("eyJhbGciOiJIUzI1NiJ9.test"))
    }

    @Test
    fun detectProvider_unknownKey() {
        assertEquals("custom", ApiKeySelector.detectProvider("sk-test123"))
    }

    // ── getKeysForProvider ───────────────────────────────────
    @Test
    fun getKeysForProvider_filtersCorrectly() {
        val keys = listOf("AIzaSy123", "gsk_456", "csk-789", "AIzaSy000")
        val geminiKeys = selector.getKeysForProvider(keys, "gemini")
        val groqKeys = selector.getKeysForProvider(keys, "groq")
        val cerebrasKeys = selector.getKeysForProvider(keys, "cerebras")
        assertEquals(2, geminiKeys.size)
        assertEquals(1, groqKeys.size)
        assertEquals(1, cerebrasKeys.size)
    }

    @Test
    fun getKeysForProvider_noMatch_returnsEmpty() {
        val keys = listOf("AIzaSy123")
        val groqKeys = selector.getKeysForProvider(keys, "groq")
        assertTrue(groqKeys.isEmpty())
    }

    @Test
    fun getNextKeyForProvider_returnsCorrectKey() {
        val keys = listOf("AIzaSy123", "gsk_456")
        val geminiKey = selector.getNextKeyForProvider(keys, "gemini")
        assertEquals("AIzaSy123", geminiKey)
    }

    @Test
    fun getNextKeyForProvider_allRateLimited_returnsNull() {
        val keys = listOf("AIzaSy123", "AIzaSy456")
        selector.reportRateLimit("AIzaSy123", 60)
        selector.reportRateLimit("AIzaSy456", 60)
        assertNull(selector.getNextKeyForProvider(keys, "gemini"))
    }
}
