package com.catamsp.rite.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTransformsTest {

    // ── toUpper ──────────────────────────────────────────────
    @Test
    fun toUpper_lowercase_returnsUppercase() {
        assertEquals("HELLO", TextTransforms.toUpper("hello"))
    }

    @Test
    fun toUpper_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toUpper(""))
    }

    // ── toLower ──────────────────────────────────────────────
    @Test
    fun toLower_uppercase_returnsLowercase() {
        assertEquals("hello", TextTransforms.toLower("HELLO"))
    }

    @Test
    fun toLower_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toLower(""))
    }

    // ── toTitleCase ──────────────────────────────────────────
    @Test
    fun toTitleCase_singleWord_capitalizesFirst() {
        assertEquals("Hello", TextTransforms.toTitleCase("hello"))
    }

    @Test
    fun toTitleCase_multiWord_capitalizesAll() {
        assertEquals("Hello World", TextTransforms.toTitleCase("hello world"))
    }

    @Test
    fun toTitleCase_mixedCase_capitalizesFirstOnly() {
        assertEquals("HELLO WORLD", TextTransforms.toTitleCase("hELLO wORLD"))
    }

    @Test
    fun toTitleCase_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toTitleCase(""))
    }

    // ── collapseWhitespace ───────────────────────────────────
    @Test
    fun collapseWhitespace_multipleSpaces_collapsesToOne() {
        assertEquals("hello world", TextTransforms.collapseWhitespace("hello   world"))
    }

    @Test
    fun collapseWhitespace_leadingTrailing_trims() {
        assertEquals("hello", TextTransforms.collapseWhitespace("  hello  "))
    }

    @Test
    fun collapseWhitespace_tabsNewlines_collapses() {
        assertEquals("a b", TextTransforms.collapseWhitespace("a\t\nb"))
    }

    @Test
    fun collapseWhitespace_empty_returnsEmpty() {
        assertEquals("", TextTransforms.collapseWhitespace(""))
    }

    // ── joinNewlines ─────────────────────────────────────────
    @Test
    fun joinNewlines_singleNewline_replaces() {
        assertEquals("a b", TextTransforms.joinNewlines("a\nb"))
    }

    @Test
    fun joinNewlines_multipleNewlines_replacesAll() {
        assertEquals("a b", TextTransforms.joinNewlines("a\n\n\nb"))
    }

    @Test
    fun joinNewlines_noNewlines_unchanged() {
        assertEquals("hello", TextTransforms.joinNewlines("hello"))
    }

    // ── splitAt80 ────────────────────────────────────────────
    @Test
    fun splitAt80_under80_noSplit() {
        assertEquals("short", TextTransforms.splitAt80("short"))
    }

    @Test
    fun splitAt80_exactly80_noSplit() {
        val text = "a".repeat(80)
        assertEquals(text, TextTransforms.splitAt80(text))
    }

    @Test
    fun splitAt80_over80_splits() {
        val text = "a".repeat(100)
        val result = TextTransforms.splitAt80(text)
        assertEquals(2, result.lines().size)
        assertEquals("a".repeat(80), result.lines()[0])
        assertEquals("a".repeat(20), result.lines()[1])
    }

    // ── sortLines ────────────────────────────────────────────
    @Test
    fun sortLines_multiLine_sortsAlphabetically() {
        assertEquals("a\nb\nc", TextTransforms.sortLines("c\na\nb"))
    }

    @Test
    fun sortLines_singleLine_unchanged() {
        assertEquals("hello", TextTransforms.sortLines("hello"))
    }

    @Test
    fun sortLines_empty_returnsEmpty() {
        assertEquals("", TextTransforms.sortLines(""))
    }

    // ── dedupeLines ──────────────────────────────────────────
    @Test
    fun dedupeLines_duplicates_removesDupes() {
        assertEquals("a\nb", TextTransforms.dedupeLines("a\nb\na"))
    }

    @Test
    fun dedupeLines_noDuplicates_unchanged() {
        assertEquals("a\nb\nc", TextTransforms.dedupeLines("a\nb\nc"))
    }

    @Test
    fun dedupeLines_allSame_keepsOne() {
        assertEquals("x", TextTransforms.dedupeLines("x\nx\nx"))
    }

    // ── toUpsideDown ─────────────────────────────────────────
    @Test
    fun toUpsideDown_simpleLetters_flipsAndReverses() {
        assertEquals("ɟǝpɔqɐ", TextTransforms.toUpsideDown("abcdef"))
    }

    @Test
    fun toUpsideDown_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toUpsideDown(""))
    }

    @Test
    fun toUpsideDown_nonMappedChars_reversed() {
        assertEquals("321", TextTransforms.toUpsideDown("123"))
    }

    @Test
    fun toUpsideDown_reversesOrder() {
        assertEquals("qɐ", TextTransforms.toUpsideDown("ab"))
    }

    // ── mirror ───────────────────────────────────────────────
    @Test
    fun mirror_normal_reverses() {
        assertEquals("olleh", TextTransforms.mirror("hello"))
    }

    @Test
    fun mirror_palindrome_unchanged() {
        assertEquals("racecar", TextTransforms.mirror("racecar"))
    }

    @Test
    fun mirror_empty_returnsEmpty() {
        assertEquals("", TextTransforms.mirror(""))
    }

    // ── toBold ───────────────────────────────────────────────
    @Test
    fun toBold_lowercase_boldLetters() {
        val result = TextTransforms.toBold("ab")
        assertEquals(String(Character.toChars(0x1D41A)) + String(Character.toChars(0x1D41B)), result)
    }

    @Test
    fun toBold_uppercase_boldLetters() {
        val result = TextTransforms.toBold("AB")
        assertEquals(String(Character.toChars(0x1D400)) + String(Character.toChars(0x1D401)), result)
    }

    @Test
    fun toBold_nonAlpha_passthrough() {
        assertEquals("123", TextTransforms.toBold("123"))
    }

    @Test
    fun toBold_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toBold(""))
    }

    // ── toItalic ─────────────────────────────────────────────
    @Test
    fun toItalic_lowercase_italicLetters() {
        val result = TextTransforms.toItalic("ab")
        assertEquals(String(Character.toChars(0x1D44E)) + String(Character.toChars(0x1D44F)), result)
    }

    @Test
    fun toItalic_uppercase_italicLetters() {
        val result = TextTransforms.toItalic("AB")
        assertEquals(String(Character.toChars(0x1D434)) + String(Character.toChars(0x1D435)), result)
    }

    @Test
    fun toItalic_nonAlpha_passthrough() {
        assertEquals("!", TextTransforms.toItalic("!"))
    }

    @Test
    fun toItalic_empty_returnsEmpty() {
        assertEquals("", TextTransforms.toItalic(""))
    }

    // ── rot13 ────────────────────────────────────────────────
    @Test
    fun rot13_lowerCase_rotates() {
        assertEquals("uryyb", TextTransforms.rot13("hello"))
    }

    @Test
    fun rot13_upperCase_rotates() {
        assertEquals("URYYB", TextTransforms.rot13("HELLO"))
    }

    @Test
    fun rot13_nonAlpha_passthrough() {
        assertEquals("123!", TextTransforms.rot13("123!"))
    }

    @Test
    fun rot13_doubleRotation_original() {
        assertEquals("hello", TextTransforms.rot13(TextTransforms.rot13("hello")))
    }

    // ── md5Hash ──────────────────────────────────────────────
    @Test
    fun md5Hash_knownInput_returnsCorrectHash() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", TextTransforms.md5Hash("hello"))
    }

    @Test
    fun md5Hash_empty_returnsEmptyHash() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", TextTransforms.md5Hash(""))
    }

    @Test
    fun md5Hash_returns32HexChars() {
        val hash = TextTransforms.md5Hash("test")
        assertEquals(32, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }

    // ── reverseWords ─────────────────────────────────────────
    @Test
    fun reverseWords_multiWord_reverses() {
        assertEquals("world hello", TextTransforms.reverseWords("hello world"))
    }

    @Test
    fun reverseWords_singleWord_unchanged() {
        assertEquals("hello", TextTransforms.reverseWords("hello"))
    }

    @Test
    fun reverseWords_empty_returnsEmpty() {
        assertEquals("", TextTransforms.reverseWords(""))
    }

    @Test
    fun reverseWords_extraSpaces_preserves() {
        assertEquals("c  b  a", TextTransforms.reverseWords("a  b  c"))
    }

    // ── countWordsAndChars ───────────────────────────────────
    @Test
    fun countWordsAndChars_normalText() {
        val (words, chars) = TextTransforms.countWordsAndChars("hello world")
        assertEquals(2, words)
        assertEquals(11, chars)
    }

    @Test
    fun countWordsAndChars_empty() {
        val (words, chars) = TextTransforms.countWordsAndChars("")
        assertEquals(0, words)
        assertEquals(0, chars)
    }

    @Test
    fun countWordsAndChars_singleWord() {
        val (words, chars) = TextTransforms.countWordsAndChars("hello")
        assertEquals(1, words)
        assertEquals(5, chars)
    }

    @Test
    fun countWordsAndChars_multipleSpaces() {
        val (words, chars) = TextTransforms.countWordsAndChars("a  b  c")
        assertEquals(3, words)
        assertEquals(7, chars)
    }

    // ── formatDateTime ───────────────────────────────────────
    @Test
    fun formatDateTime_replace_appendsStamp() {
        assertEquals("text 2024-01-01 12:00", TextTransforms.formatDateTime("text", "2024-01-01 12:00", CommandMode.REPLACE))
    }

    @Test
    fun formatDateTime_append_newlineStamp() {
        assertEquals("text\n2024-01-01", TextTransforms.formatDateTime("text", "2024-01-01", CommandMode.APPEND))
    }

    @Test
    fun formatDateTime_prepend_stampFirst() {
        assertEquals("2024-01-01\ntext", TextTransforms.formatDateTime("text", "2024-01-01", CommandMode.PREPEND))
    }

    @Test
    fun formatDateTime_emptyOriginal_stampOnly() {
        assertEquals("2024-01-01", TextTransforms.formatDateTime("", "2024-01-01", CommandMode.REPLACE))
    }

    // ── formatCount ──────────────────────────────────────────
    @Test
    fun formatCount_replace_withInfo() {
        assertEquals("hello  [1 words | 5 chars]", TextTransforms.formatCount("hello", 1, 5, CommandMode.REPLACE))
    }

    @Test
    fun formatCount_prepend_infoFirst() {
        assertEquals("[2 words | 11 chars]  hello world", TextTransforms.formatCount("hello world", 2, 11, CommandMode.PREPEND))
    }

    // ── formatMd5 ────────────────────────────────────────────
    @Test
    fun formatMd5_replace_withHash() {
        val result = TextTransforms.formatMd5("hello", "abc123", CommandMode.REPLACE)
        assertEquals("hello  [md5: abc123]", result)
    }

    @Test
    fun formatMd5_append_emptyOriginal_hashOnly() {
        assertEquals("[md5: abc123]", TextTransforms.formatMd5("", "abc123", CommandMode.APPEND))
    }

    @Test
    fun formatMd5_prepend_hashFirst() {
        assertEquals("[md5: abc123]  hello", TextTransforms.formatMd5("hello", "abc123", CommandMode.PREPEND))
    }
}
