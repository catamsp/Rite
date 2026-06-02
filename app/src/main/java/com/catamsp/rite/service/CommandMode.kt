package com.catamsp.rite.service

enum class CommandMode { REPLACE, APPEND, PREPEND }

fun getCommandMode(trigger: String): CommandMode = when {
    trigger.startsWith("!") -> CommandMode.APPEND
    trigger.startsWith("+") -> CommandMode.PREPEND
    else -> CommandMode.REPLACE
}

fun applyMode(original: String, result: String, mode: CommandMode): String = when (mode) {
    CommandMode.REPLACE -> result
    CommandMode.APPEND -> if (original.isEmpty()) result else "$original\n$result"
    CommandMode.PREPEND -> if (original.isEmpty()) result else "$result\n$original"
}

fun extractCmdName(trigger: String, prefix: String): String = when {
    trigger.startsWith(prefix) -> trigger.removePrefix(prefix)
    trigger.startsWith("!") -> trigger.removePrefix("!")
    trigger.startsWith("+") -> trigger.removePrefix("+")
    else -> trigger
}
