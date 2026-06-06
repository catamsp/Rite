package com.catamsp.rite.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenContextCollectorTest {

    @Test
    fun deduplicateConsecutive_emptyList_returnsEmpty() {
        val result = ScreenContextCollector.deduplicateConsecutive(emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun deduplicateConsecutive_singleItem_returnsSame() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("hello"))
        assertEquals(listOf("hello"), result)
    }

    @Test
    fun deduplicateConsecutive_noDuplicates_returnsAll() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun deduplicateConsecutive_consecutiveDuplicates_removesDupes() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("a", "a", "b", "b", "b", "c"))
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun deduplicateConsecutive_nonConsecutiveDuplicates_keepsBoth() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("a", "b", "a"))
        assertEquals(listOf("a", "b", "a"), result)
    }

    @Test
    fun deduplicateConsecutive_allSame_returnsSingle() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("x", "x", "x", "x"))
        assertEquals(listOf("x"), result)
    }

    @Test
    fun deduplicateConsecutive_emptyStrings_removesConsecutiveEmpty() {
        val result = ScreenContextCollector.deduplicateConsecutive(listOf("", "", "hello", "", "world", "world"))
        assertEquals(listOf("", "hello", "", "world"), result)
    }
}
