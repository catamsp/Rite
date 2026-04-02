package com.catamsp.rite.model

data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true
)
