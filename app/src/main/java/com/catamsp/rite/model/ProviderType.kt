package com.catamsp.rite.model

object ProviderType {
    const val GEMINI = "gemini"
    const val GROQ = "groq"
    const val CUSTOM = "custom"
    const val KILO = "kilo"
    const val CEREBRAS = "cerebras"
    const val OPENROUTER = "openrouter"
    const val FIREWORKS = "fireworks"
    const val STABLE_HORDE = "stable_horde"
    const val MISTRAL = "mistral"

    private val VALID = setOf(GEMINI, GROQ, CUSTOM, KILO, CEREBRAS, OPENROUTER, FIREWORKS, STABLE_HORDE, MISTRAL)
    fun sanitize(value: String?): String = if (value in VALID) value!! else GEMINI

    fun label(value: String): String = when (value) {
        GEMINI -> "Google Gemini"
        GROQ -> "Groq"
        CUSTOM -> "Custom (OpenAI)"
        KILO -> "Kilo"
        CEREBRAS -> "Cerebras"
        OPENROUTER -> "OpenRouter"
        FIREWORKS -> "Fireworks"
        STABLE_HORDE -> "StableHorde"
        MISTRAL -> "MistralAI"
        else -> value
    }
}
