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
                    textHelper.replaceText(source, TextTransforms.formatDateTime(cleanText, stamp, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "time" -> {
                    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val stamp = fmt.format(java.util.Date())
                    textHelper.replaceText(source, TextTransforms.formatDateTime(cleanText, stamp, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "count" -> {
                    if (cleanText.isEmpty()) {
                        textHelper.replaceText(source, "")
                        performHaptic(HapticFeedbackConstants.REJECT)
                        toastManager.show("No text to count")
                    } else {
                        val (words, chars) = TextTransforms.countWordsAndChars(cleanText)
                        textHelper.replaceText(source, TextTransforms.formatCount(cleanText, words, chars, mode))
                        performHaptic(HapticFeedbackConstants.CONFIRM)
                    }
                }

                // ── Text transformations ─────────────────────────────────────────
                "upper" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toUpper(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "lower" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toLower(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "title" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toTitleCase(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "trim" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.collapseWhitespace(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "join" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.joinNewlines(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "split" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.splitAt80(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "sort" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.sortLines(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "dedupe" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.dedupeLines(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }

                // ── Unicode & encoding ───────────────────────────────────────────
                "upside" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toUpsideDown(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "mirror" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.mirror(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "bold" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toBold(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "italic" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.toItalic(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "rot13" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.rot13(cleanText), mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "md5" -> {
                    val hash = TextTransforms.md5Hash(cleanText)
                    textHelper.replaceText(source, TextTransforms.formatMd5(cleanText, hash, mode))
                    performHaptic(HapticFeedbackConstants.CONFIRM)
                }
                "reverse" -> {
                    textHelper.replaceText(source, applyMode(cleanText, TextTransforms.reverseWords(cleanText), mode))
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
