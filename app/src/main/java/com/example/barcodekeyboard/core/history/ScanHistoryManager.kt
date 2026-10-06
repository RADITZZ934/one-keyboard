package com.example.barcodekeyboard.core.history

import android.content.Context
import android.content.SharedPreferences
import com.example.barcodekeyboard.data.model.ScanHistoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manages persistent storage and retrieval of barcode scan history
 * using SharedPreferences JSON serialization.
 */
class ScanHistoryManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "one_keyboard_scan_history"
        private const val KEY_HISTORY = "history_items_json"
        private const val MAX_ITEMS = 60

        @Volatile
        private var instance: ScanHistoryManager? = null

        fun getInstance(context: Context): ScanHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: ScanHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun getHistory(): List<ScanHistoryItem> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<ScanHistoryItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ScanHistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text", ""),
                        format = obj.optString("format", "BARCODE"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    @Synchronized
    fun addScan(text: String, format: String = "BARCODE") {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val items = getHistory().toMutableList()
        // Deduplicate if identical to recent entry
        items.removeAll { it.text == trimmed }
        items.add(0, ScanHistoryItem(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            format = format,
            timestamp = System.currentTimeMillis()
        ))

        if (items.size > MAX_ITEMS) {
            items.subList(MAX_ITEMS, items.size).clear()
        }
        saveItems(items)
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun deleteItem(id: String) {
        val items = getHistory().toMutableList()
        items.removeAll { it.id == id }
        saveItems(items)
    }

    private fun saveItems(items: List<ScanHistoryItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("format", item.format)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }
}
