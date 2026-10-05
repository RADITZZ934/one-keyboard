package com.example.barcodekeyboard.data.model

/**
 * Represents a non-fruit product item with its associated barcode code, name, and UOM.
 */
data class BarcodeItem(
    val id: Long,
    val code: String,
    val name: String,
    val uom: String = "PCS"
)
