package com.catamsp.rite.service

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandModeTest {

    @Test
    fun getCommandMode_questionMark_returnsReplace() {
        assertEquals(CommandMode.REPLACE, getCommandMode("?fix"))
    }

    @Test
    fun getCommandMode_exclamation_returnsAppend() {
        assertEquals(CommandMode.APPEND, getCommandMode("!fix"))
    }

    @Test
    fun getCommandMode_plus_returnsPrepend() {
        assertEquals(CommandMode.PREPEND, getCommandMode("+fix"))
    }

    @Test
    fun applyMode_replace_returnsResult() {
        assertEquals("corrected", applyMode("orginal", "corrected", CommandMode.REPLACE))
    }

    @Test
    fun applyMode_append_appendsWithNewline() {
        assertEquals("hello\nworld", applyMode("hello", "world", CommandMode.APPEND))
    }

    @Test
    fun applyMode_prepend_prependsWithNewline() {
        assertEquals("world\nhello", applyMode("hello", "world", CommandMode.PREPEND))
    }

    @Test
    fun applyMode_append_emptyOriginal_returnsResult() {
        assertEquals("world", applyMode("", "world", CommandMode.APPEND))
    }

    @Test
    fun applyMode_prepend_emptyOriginal_returnsResult() {
        assertEquals("world", applyMode("", "world", CommandMode.PREPEND))
    }

    @Test
    fun extractCmdName_withPrefix_stripsPrefix() {
        assertEquals("fix", extractCmdName("?fix", "?"))
    }

    @Test
    fun extractCmdName_modePrefix_stripsModePrefix() {
        assertEquals("fix", extractCmdName("!fix", "?"))
        assertEquals("fix", extractCmdName("+fix", "?"))
    }
}
