package com.example.barcodekeyboard.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.barcodekeyboard.data.model.BarcodeItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository for managing non-fruit item catalog and barcodes.
 * Supports:
 * - Offline import of XLSX / XLS / CSV files
 * - Fast multi-token search for cashier/employee input
 * - Local persistence of imported and customized items
 * - Real-time notification when catalog updates
 */
class BarcodeItemRepository(private val context: Context) {

    companion object {
        private const val TAG = "BarcodeItemRepository"
        private const val CUSTOM_FILE_NAME = "items_nonbuah_custom.json"
        private const val ASSET_FILE_NAME = "items_nonbuah.json"

        private const val PREFS_NAME = "catalog_importer_prefs"
        private const val KEY_LAST_IMPORT_NAME = "last_import_filename"
        private const val KEY_LAST_IMPORT_TIME = "last_import_timestamp"
        private const val KEY_LAST_IMPORT_COUNT = "last_import_item_count"

        @Volatile
        private var instance: BarcodeItemRepository? = null

        fun getInstance(context: Context): BarcodeItemRepository {
            return instance ?: synchronized(this) {
                instance ?: BarcodeItemRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cachedItems = mutableListOf<BarcodeItem>()
    private var isLoaded = false
    private val listeners = mutableListOf<(List<BarcodeItem>) -> Unit>()

    @Synchronized
    fun addOnCatalogChangedListener(listener: (List<BarcodeItem>) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun removeOnCatalogChangedListener(listener: (List<BarcodeItem>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val snapshot = cachedItems.toList()
        for (listener in listeners.toList()) {
            try {
                listener.invoke(snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking catalog listener: ${e.message}")
            }
        }
    }

    @Synchronized
    fun getAllItems(): List<BarcodeItem> {
        ensureLoaded()
        return cachedItems.toList()
    }

    @Synchronized
    fun getItemCount(): Int {
        ensureLoaded()
        return cachedItems.size
    }

    @Synchronized
    fun searchItems(query: String): List<BarcodeItem> {
        ensureLoaded()
        val trimmed = query.trim().toLowerCase(Locale.getDefault())
        if (trimmed.isEmpty()) {
            return cachedItems.toList()
        }

        val tokens = trimmed.split(Regex("\\s+"))
        return cachedItems.filter { item ->
            val itemNameLower = item.name.toLowerCase(Locale.getDefault())
            val itemCodeLower = item.code.toLowerCase(Locale.getDefault())

            tokens.all { token ->
                itemNameLower.contains(token) || itemCodeLower.contains(token)
            }
        }
    }

    /**
     * Imports a newly parsed list of items (e.g. from an uploaded XLSX/XLS file).
     */
    @Synchronized
    fun importItems(
        newItems: List<BarcodeItem>,
        sourceFileName: String,
        replaceExisting: Boolean = true
    ): Int {
        ensureLoaded()
        if (replaceExisting) {
            cachedItems.clear()
            cachedItems.addAll(newItems)
        } else {
            // Append while avoiding exact duplicate barcode codes
            val existingCodes = cachedItems.map { it.code }.toSet()
            for (item in newItems) {
                if (!existingCodes.contains(item.code)) {
                    cachedItems.add(item)
                }
            }
        }

        persistCustomItems()

        // Update import metadata
        prefs.edit()
            .putString(KEY_LAST_IMPORT_NAME, sourceFileName)
            .putLong(KEY_LAST_IMPORT_TIME, System.currentTimeMillis())
            .putInt(KEY_LAST_IMPORT_COUNT, cachedItems.size)
            .apply()

        notifyListeners()
        return cachedItems.size
    }

    fun getLastImportSummary(): String {
        val fileName = prefs.getString(KEY_LAST_IMPORT_NAME, null)
        val time = prefs.getLong(KEY_LAST_IMPORT_TIME, 0L)
        val count = prefs.getInt(KEY_LAST_IMPORT_COUNT, cachedItems.size)

        return if (fileName != null && time > 0) {
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(time))
            "File: $fileName • $count item ($dateStr)"
        } else {
            "Database Bawaan (466 item nonbuah)"
        }
    }

    fun getLastImportFileName(): String? {
        return prefs.getString(KEY_LAST_IMPORT_NAME, null)
    }

    @Synchronized
    fun saveItem(item: BarcodeItem) {
        ensureLoaded()
        val index = cachedItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            cachedItems[index] = item
        } else {
            cachedItems.add(0, item)
        }
        persistCustomItems()
        notifyListeners()
    }

    @Synchronized
    fun deleteItem(id: Long) {
        ensureLoaded()
        val removed = cachedItems.removeAll { it.id == id }
        if (removed) {
            persistCustomItems()
            notifyListeners()
        }
    }

    @Synchronized
    fun resetToDefault() {
        try {
            val customFile = File(context.filesDir, CUSTOM_FILE_NAME)
            if (customFile.exists()) {
                customFile.delete()
            }
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete custom items file", e)
        }
        isLoaded = false
        ensureLoaded()
        notifyListeners()
    }

    @Synchronized
    fun clearAll() {
        cachedItems.clear()
        persistCustomItems()
        prefs.edit().clear().apply()
        notifyListeners()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        cachedItems.clear()

        val customFile = File(context.filesDir, CUSTOM_FILE_NAME)
        if (customFile.exists()) {
            try {
                val jsonString = customFile.readText(Charsets.UTF_8)
                parseJsonArray(jsonString)
                Log.d(TAG, "Loaded ${cachedItems.size} items from custom storage")
                isLoaded = true
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read custom items file, falling back to assets", e)
            }
        }

        // Load from assets fallback
        try {
            context.assets.open(ASSET_FILE_NAME).use { stream ->
                val reader = InputStreamReader(stream, Charsets.UTF_8)
                val jsonString = reader.readText()
                parseJsonArray(jsonString)
                Log.d(TAG, "Loaded ${cachedItems.size} items from assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default items from assets", e)
        }
        isLoaded = true
    }

    private fun parseJsonArray(jsonString: String) {
        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.optLong("id", (i + 1).toLong())
            val code = obj.optString("code", "").trim()
            val name = obj.optString("name", "").trim()
            val uom = obj.optString("uom", "PCS").trim()

            if (code.isNotEmpty() && name.isNotEmpty()) {
                cachedItems.add(BarcodeItem(id = id, code = code, name = name, uom = uom))
            }
        }
    }

    private fun persistCustomItems() {
        try {
            val array = JSONArray()
            for (item in cachedItems) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("code", item.code)
                    put("name", item.name)
                    put("uom", item.uom)
                }
                array.put(obj)
            }
            val customFile = File(context.filesDir, CUSTOM_FILE_NAME)
            customFile.writeText(array.toString(2), Charsets.UTF_8)
            Log.d(TAG, "Persisted ${cachedItems.size} custom items")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist custom items", e)
        }
    }
}
