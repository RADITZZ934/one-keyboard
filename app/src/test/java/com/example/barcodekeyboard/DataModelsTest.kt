package com.example.barcodekeyboard

import com.example.barcodekeyboard.data.model.ClipboardItem
import com.example.barcodekeyboard.data.model.ScanHistoryItem
import com.example.barcodekeyboard.data.model.ScanResult
import org.junit.Assert.*
import org.junit.Test

class DataModelsTest {

    @Test
    fun testScanResultCreation() {
        val now = 1700000000000L
        val result = ScanResult(
            text = "8992761123456",
            format = 32, // EAN_13
            timestamp = now
        )
        assertEquals("8992761123456", result.text)
        assertEquals(32, result.format)
        assertEquals(now, result.timestamp)
    }

    @Test
    fun testClipboardItemPinning() {
        val item = ClipboardItem(
            id = 1L,
            text = "One Keyboard Test Note",
            timestamp = 1000L,
            isPinned = false
        )
        assertFalse(item.isPinned)
        val pinned = item.copy(isPinned = true)
        assertTrue(pinned.isPinned)
        assertEquals("One Keyboard Test Note", pinned.text)
    }

    @Test
    fun testScanHistoryItemCreation() {
        val historyItem = ScanHistoryItem(
            id = "item-123",
            text = "QR_DATA_HTTPS_EXAMPLE",
            format = "QR_CODE",
            timestamp = 5000L
        )
        assertEquals("item-123", historyItem.id)
        assertEquals("QR_DATA_HTTPS_EXAMPLE", historyItem.text)
        assertEquals("QR_CODE", historyItem.format)
        assertEquals(5000L, historyItem.timestamp)
    }
}
