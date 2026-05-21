package com.catamsp.rite.model

import androidx.compose.runtime.Immutable

@Immutable
data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false
)
