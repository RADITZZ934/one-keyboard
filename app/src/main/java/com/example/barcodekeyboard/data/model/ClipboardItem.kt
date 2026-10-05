package com.example.barcodekeyboard.data.model

/**
 * Model representing an item in the clipboard history (Papan Klip).
 */
data class ClipboardItem(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
