package com.catamsp.rite.model

data class ContactShortcut(
    val contactName: String,
    val contactId: String,
    val triggerCode: String,
    val isEnabled: Boolean = true
)
