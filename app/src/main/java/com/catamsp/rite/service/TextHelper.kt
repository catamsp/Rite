package com.catamsp.rite.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextHelper(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handler: Handler
) {
    suspend fun replaceText(source: AccessibilityNodeInfo, newText: String) = withContext(Dispatchers.Main) {
        source.refresh()
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        val success = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        if (!success) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val oldClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("Rite Result", newText))

            val selectAllArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, source.text?.length ?: 0)
            }
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
            source.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            handler.postDelayed({
                if (oldClip != null) clipboard.setPrimaryClip(oldClip)
            }, 500)
        }
    }

    fun replaceTextSync(source: AccessibilityNodeInfo, newText: String) {
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    fun setFieldText(source: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Rite", text))
    }

    fun pasteFromClipboard(
        source: AccessibilityNodeInfo,
        cleanText: String,
        mode: CommandMode,
        trigger: String,
        onPasteError: suspend (String) -> Unit
    ) {
        handler.post {
            try {
                val textBeforePaste = source.text?.toString() ?: ""

                if (textBeforePaste.endsWith(trigger)) {
                    val textWithoutTrigger = textBeforePaste.substring(0, textBeforePaste.length - trigger.length)
                    replaceTextSync(source, textWithoutTrigger)

                    handler.postDelayed({
                        source.refresh()
                        performPasteAction(source, mode, textWithoutTrigger, onPasteError)
                    }, 100)
                } else {
                    performPasteAction(source, mode, textBeforePaste, onPasteError)
                }
            } catch (e: Exception) {
                android.util.Log.e("Rite", "Paste failed: ${e.message}")
                scope.launch { onPasteError("Paste failed") }
            }
        }
    }

    private fun performPasteAction(
        source: AccessibilityNodeInfo,
        mode: CommandMode,
        textBeforePaste: String,
        onPasteError: suspend (String) -> Unit
    ) {
        try {
            when (mode) {
                CommandMode.REPLACE -> {
                    val textLen = source.text?.length ?: 0
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLen)
                    }
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
                CommandMode.APPEND -> {
                    val textLen = source.text?.length ?: 0
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, textLen)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLen)
                    }
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
                CommandMode.PREPEND -> {
                    val selectionArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0)
                    }
                    source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                    source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            }

            handler.postDelayed({
                source.refresh()
                val textAfter = source.text?.toString() ?: ""
                if (textAfter == textBeforePaste) {
                    scope.launch { onPasteError("Clipboard is empty") }
                }
            }, 300)
        } catch (e: Exception) {
            android.util.Log.e("Rite", "Paste action failed: ${e.message}")
            scope.launch { onPasteError("Paste failed") }
        }
    }

    fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job {
        return scope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(200)
            }
        }
    }

    private companion object {
        val SPINNER_FRAMES = arrayOf("⣷", "⣯", "⣟", "⡿", "⢿", "⣻", "⣽", "⣾")
    }
}
