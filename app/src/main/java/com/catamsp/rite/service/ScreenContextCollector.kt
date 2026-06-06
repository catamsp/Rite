package com.catamsp.rite.service

import android.view.accessibility.AccessibilityNodeInfo

object ScreenContextCollector {

    enum class ContextMode { FULL, QUICK }

    fun collect(source: AccessibilityNodeInfo, root: AccessibilityNodeInfo, mode: ContextMode, packageToExclude: String = ""): String {
        val allTexts = mutableListOf<String>()
        val sourceText = source.text?.toString() ?: ""

        collectNode(root, allTexts, sourceText, packageToExclude)

        val deduped = deduplicateConsecutive(allTexts)

        return when (mode) {
            ContextMode.FULL -> deduped.joinToString("\n")
            ContextMode.QUICK -> deduped.takeLast(5).joinToString("\n")
        }
    }

    private fun collectNode(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        sourceText: String,
        ritePackage: String
    ) {
        if (isRitePackage(node, ritePackage)) return
        if (node.isPassword) return

        val text = node.text?.toString()?.trim() ?: ""
        if (text.isNotEmpty() && text != sourceText && isMeaningfulText(text, node)) {
            out.add(text)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNode(child, out, sourceText, ritePackage)
            child.recycle()
        }
    }

    private fun isMeaningfulText(text: String, node: AccessibilityNodeInfo): Boolean {
        if (text.length < 3) return false
        if (text in UI_LABELS) return false

        val className = node.className?.toString() ?: ""
        if (className.contains("Button") || className.contains("Image") ||
            className.contains("CheckBox") || className.contains("Switch") ||
            className.contains("RadioButton") || className.contains("Toggle")) return false

        if (node.isClickable) return false
        if (text.length < 5 && !text.contains(' ')) return false

        return true
    }

    private fun isRitePackage(node: AccessibilityNodeInfo, ritePackage: String): Boolean {
        if (ritePackage.isEmpty()) return false
        return node.packageName?.toString() == ritePackage
    }

    internal fun deduplicateConsecutive(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        val result = mutableListOf(texts.first())
        for (i in 1 until texts.size) {
            if (texts[i] != texts[i - 1]) {
                result.add(texts[i])
            }
        }
        return result
    }

    private val UI_LABELS = setOf(
        "Back", "Home", "Recents", "Send", "Attach", "Search",
        "Copy", "Paste", "Cut", "Select All", "Undo", "Redo",
        "Done", "Cancel", "OK", "Yes", "No",
        "WiFi", "Bluetooth", "Battery", "Settings",
        "Navigate up", "Close", "Expand"
    )
}
