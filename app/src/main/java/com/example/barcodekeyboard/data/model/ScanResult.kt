package com.example.barcodekeyboard.data.model

/**
 * Data class representing a scanned barcode or QR code.
 *
 * @property text The raw text content decoded from the barcode.
 * @property format The barcode format constant from ML Kit (e.g., Barcode.FORMAT_QR_CODE).
 * @property timestamp Epoch time in milliseconds when the barcode was recognized.
 */
data class ScanResult(
    val text: String,
    val format: Int,
    val timestamp: Long = System.currentTimeMillis()
)
