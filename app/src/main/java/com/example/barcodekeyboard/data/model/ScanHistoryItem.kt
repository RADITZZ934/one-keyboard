package com.example.barcodekeyboard.data.model

/**
 * Represents a barcode or QR code item recorded in the scan history.
 */
data class ScanHistoryItem(
    val id: String,
    val text: String,
    val format: String = "BARCODE",
    val timestamp: Long = System.currentTimeMillis()
)
