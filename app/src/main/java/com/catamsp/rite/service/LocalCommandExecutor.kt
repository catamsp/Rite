package com.catamsp.rite.service

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalCommandExecutor(
    private val textHelper: TextHelper,
    private val toastManager: OverlayToastManager,
    private val scope: CoroutineScope,
    private val performHaptic: (Int) -> Unit
) {
    fun execute(
        source: AccessibilityNodeInfo,
        cleanText: String,
        commandName: String,
        mode: CommandMode,
        trigger: String,
        onProcessingComplete: suspend () -> Unit
    ): Job = scope.launch {
        try {
            when (commandName) {
                // ── Clipboard / meta operations ──────────────────────────────────
                "cp" -> {
                    textHelper.copyToClipboard(cleanText)
                    textHelper.replaceText(source, cleanText)
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) toastManager.show("Copied to clipboard")
                }
                "ct" -> {
                    textHelper.copyToClipboard(cleanText)
                    textHelper.replaceText(source, "")
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) toastManager.show("Cut to clipboard")
                }
                "pt" -> {
                    textHelper.pasteFromClipboard(source, cleanText, mode, trigger) { msg ->
                        toastManager.show(msg)
                    }
                }
                "del" -> {
                    textHelper.replaceText(source, "")
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }

                // ── Insert commands ──────────────────────────────────────────────
                "date" -> {
                    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val stamp = fmt.format(java.util.Date())
                    val result = when (mode) {
                        CommandMode.REPLACE -> if (cleanText.isEmpty()) stamp else "$cleanText $stamp"
                        CommandMode.APPEND -> if (cleanText.isEmpty()) stamp else "$cleanText\n$stamp"
                        CommandMode.PREPEND -> if (cleanText.isEmpty()) stamp else "$stamp\n$cleanText"
                    }
                    textHelper.replaceText(source, result)
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "time" -> {
                    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val stamp = fmt.format(java.util.Date())
                    val result = when (mode) {
                        CommandMode.REPLACE -> if (cleanText.isEmpty()) stamp else "$cleanText $stamp"
                        CommandMode.APPEND -> if (cleanText.isEmpty()) stamp else "$cleanText\n$stamp"
                        CommandMode.PREPEND -> if (cleanText.isEmpty()) stamp else "$stamp\n$cleanText"
                    }
                    textHelper.replaceText(source, result)
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "count" -> {
                    if (cleanText.isEmpty()) {
                        textHelper.replaceText(source, "")
                        performHaptic(HapticFeedbackConstants.REJECT)
                        toastManager.show("No text to count")
                    } else {
                        val words = cleanText.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                        val info = "[${words} words | ${cleanText.length} chars]"
                        val result = when (mode) {
                            CommandMode.REPLACE -> "$cleanText  $info"
                            CommandMode.APPEND -> "$cleanText  $info"
                            CommandMode.PREPEND -> "$info  $cleanText"
                        }
                        textHelper.replaceText(source, result)
                        performHaptic(HapticFeedbackConstants.CONFIRM)
                    }
                }

                // ── Text transformations ─────────────────────────────────────────
                "upper" -> {
                    textHelper.replaceText(source, applyMode(cleanText, cleanText.uppercase(), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "lower" -> {
                    textHelper.replaceText(source, applyMode(cleanText, cleanText.lowercase(), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "title" -> {
                    val result = cleanText.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "trim" -> {
                    val result = cleanText
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "join" -> {
                    val result = cleanText.replace(Regex("\n+"), " ")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "split" -> {
                    val result = cleanText.chunked(80).joinToString("\n")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "sort" -> {
                    val result = cleanText.lines().sorted().joinToString("\n")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "dedupe" -> {
                    val result = cleanText.lines().distinct().joinToString("\n")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }

                // ── Unicode & encoding ───────────────────────────────────────────
                "upside" -> {
                    val map = mapOf(
                        'a' to 'ɐ', 'b' to 'q', 'c' to 'ɔ', 'd' to 'p', 'e' to 'ǝ',
                        'f' to 'ɟ', 'g' to 'ƃ', 'h' to 'ɥ', 'i' to 'ᴉ', 'j' to 'ɾ',
                        'k' to 'ʞ', 'l' to 'l', 'm' to 'ɯ', 'n' to 'u', 'o' to 'o',
                        'p' to 'd', 'q' to 'b', 'r' to 'ɹ', 's' to 's', 't' to 'ʇ',
                        'u' to 'n', 'v' to 'ʌ', 'w' to 'ʍ', 'x' to 'x', 'y' to 'ʎ', 'z' to 'z'
                    )
                    val result = cleanText.map { map[it.lowercaseChar()] ?: it }.reversed().joinToString("")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "mirror" -> {
                    textHelper.replaceText(source, applyMode(cleanText, cleanText.reversed(), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "bold" -> {
                    val result = cleanText.map { ch ->
                        when {
                            ch in 'a'..'z' -> String(Character.toChars(0x1D41A + (ch - 'a')))
                            ch in 'A'..'Z' -> String(Character.toChars(0x1D400 + (ch - 'A')))
                            else -> ch.toString()
                        }
                    }.joinToString("")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "italic" -> {
                    val result = cleanText.map { ch ->
                        when {
                            ch in 'a'..'z' -> String(Character.toChars(0x1D44E + (ch - 'a')))
                            ch in 'A'..'Z' -> String(Character.toChars(0x1D434 + (ch - 'A')))
                            else -> ch.toString()
                        }
                    }.joinToString("")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "rot13" -> {
                    val result = cleanText.map { ch ->
                        when {
                            ch in 'a'..'z' -> ('a' + (ch - 'a' + 13) % 26)
                            ch in 'A'..'Z' -> ('A' + (ch - 'A' + 13) % 26)
                            else -> ch
                        }
                    }.joinToString("")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "md5" -> {
                    val md = java.security.MessageDigest.getInstance("MD5")
                    val hash = md.digest(cleanText.toByteArray()).joinToString("") { "%02x".format(it) }
                    val hashText = "[md5: $hash]"
                    val result = when (mode) {
                        CommandMode.REPLACE -> "$cleanText  $hashText"
                        CommandMode.APPEND -> if (cleanText.isEmpty()) hashText else "$cleanText  $hashText"
                        CommandMode.PREPEND -> if (cleanText.isEmpty()) hashText else "$hashText  $cleanText"
                    }
                    textHelper.replaceText(source, result)
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "reverse" -> {
                    val result = cleanText.split(" ").reversed().joinToString(" ")
                    textHelper.replaceText(source, applyMode(cleanText, result, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toastManager.show("Command failed")
        } finally {
            withContext(NonCancellable) {
                onProcessingComplete()
            }
        }
    }
}
