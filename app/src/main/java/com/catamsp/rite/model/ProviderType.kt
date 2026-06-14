package com.catamsp.rite.model

object ProviderType {
    const val GEMINI = "gemini"
    const val GROQ = "groq"
    const val CUSTOM = "custom"
    const val KILO = "kilo"
    const val CEREBRAS = "cerebras"

    private val VALID = setOf(GEMINI, GROQ, CUSTOM, KILO, CEREBRAS)
    fun sanitize(value: String?): String = if (value in VALID) value!! else GEMINI

    fun label(value: String): String = when (value) {
        GEMINI -> "Google Gemini"
        GROQ -> "Groq"
        CUSTOM -> "Custom (OpenAI)"
        KILO -> "Kilo"
        CEREBRAS -> "Cerebras"
        else -> value
    }
}
