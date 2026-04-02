package com.catamsp.rite.model

data class AppShortcut(
    val appName: String,
    val packageName: String?,
    val triggerCode: String,
    val intentAction: String?,
    val intentUri: String?,
    val isSystemApp: Boolean,
    val isEnabled: Boolean = true
)
