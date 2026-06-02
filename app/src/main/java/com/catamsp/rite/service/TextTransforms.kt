package com.catamsp.rite.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TextTransforms {

    fun toUpper(text: String): String = text.uppercase()

    fun toLower(text: String): String = text.lowercase()

    fun toTitleCase(text: String): String = text.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun collapseWhitespace(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    fun joinNewlines(text: String): String = text.replace(Regex("\n+"), " ")

    fun splitAt80(text: String): String = text.chunked(80).joinToString("\n")

    fun sortLines(text: String): String = text.lines().sorted().joinToString("\n")

    fun dedupeLines(text: String): String = text.lines().distinct().joinToString("\n")

    fun toUpsideDown(text: String): String {
        val map = mapOf(
            'a' to 'ɐ', 'b' to 'q', 'c' to 'ɔ', 'd' to 'p', 'e' to 'ǝ',
            'f' to 'ɟ', 'g' to 'ƃ', 'h' to 'ɥ', 'i' to 'ᴉ', 'j' to 'ɾ',
            'k' to 'ʞ', 'l' to 'l', 'm' to 'ɯ', 'n' to 'u', 'o' to 'o',
            'p' to 'd', 'q' to 'b', 'r' to 'ɹ', 's' to 's', 't' to 'ʇ',
            'u' to 'n', 'v' to 'ʌ', 'w' to 'ʍ', 'x' to 'x', 'y' to 'ʎ', 'z' to 'z'
        )
        return text.map { map[it.lowercaseChar()] ?: it }.reversed().joinToString("")
    }

    fun mirror(text: String): String = text.reversed()

    fun toBold(text: String): String = text.map { ch ->
        when {
            ch in 'a'..'z' -> String(Character.toChars(0x1D41A + (ch - 'a')))
            ch in 'A'..'Z' -> String(Character.toChars(0x1D400 + (ch - 'A')))
            else -> ch.toString()
        }
    }.joinToString("")

    fun toItalic(text: String): String = text.map { ch ->
        when {
            ch in 'a'..'z' -> String(Character.toChars(0x1D44E + (ch - 'a')))
            ch in 'A'..'Z' -> String(Character.toChars(0x1D434 + (ch - 'A')))
            else -> ch.toString()
        }
    }.joinToString("")

    fun rot13(text: String): String = text.map { ch ->
        when {
            ch in 'a'..'z' -> ('a' + (ch - 'a' + 13) % 26)
            ch in 'A'..'Z' -> ('A' + (ch - 'A' + 13) % 26)
            else -> ch
        }
    }.joinToString("")

    fun md5Hash(text: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun reverseWords(text: String): String = text.split(" ").reversed().joinToString(" ")

    fun countWordsAndChars(text: String): Pair<Int, Int> {
        val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        return Pair(words, text.length)
    }

    fun formatDateTime(cleanText: String, stamp: String, mode: CommandMode): String = when (mode) {
        CommandMode.REPLACE -> if (cleanText.isEmpty()) stamp else "$cleanText $stamp"
        CommandMode.APPEND -> if (cleanText.isEmpty()) stamp else "$cleanText\n$stamp"
        CommandMode.PREPEND -> if (cleanText.isEmpty()) stamp else "$stamp\n$cleanText"
    }

    fun formatCount(cleanText: String, words: Int, chars: Int, mode: CommandMode): String {
        val info = "[$words words | $chars chars]"
        return when (mode) {
            CommandMode.REPLACE -> "$cleanText  $info"
            CommandMode.APPEND -> "$cleanText  $info"
            CommandMode.PREPEND -> "$info  $cleanText"
        }
    }

    fun formatMd5(cleanText: String, hash: String, mode: CommandMode): String {
        val hashText = "[md5: $hash]"
        return when (mode) {
            CommandMode.REPLACE -> "$cleanText  $hashText"
            CommandMode.APPEND -> if (cleanText.isEmpty()) hashText else "$cleanText  $hashText"
            CommandMode.PREPEND -> if (cleanText.isEmpty()) hashText else "$hashText  $cleanText"
        }
    }
}
