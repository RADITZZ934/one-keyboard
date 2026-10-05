package com.example.barcodekeyboard.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.barcodekeyboard.data.model.ClipboardItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages clipboard history (Papan Klip) for One Keyboard.
 * Stores recent copied texts locally with pin/delete support.
 */
class ClipboardHistoryManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ClipboardHistoryManager"
        private const val PREFS_NAME = "clipboard_history_prefs"
        private const val KEY_CLIPS = "saved_clipboard_clips"
        private const val MAX_UNPINNED_ITEMS = 40

        @Volatile
        private var instance: ClipboardHistoryManager? = null

        fun getInstance(context: Context): ClipboardHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: ClipboardHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val clipsList = mutableListOf<ClipboardItem>()
    private var isLoaded = false
    private val listeners = mutableListOf<(List<ClipboardItem>) -> Unit>()

    @Synchronized
    fun addListener(listener: (List<ClipboardItem>) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    @Synchronized
    fun removeListener(listener: (List<ClipboardItem>) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val snapshot = clipsList.toList()
        for (listener in listeners.toList()) {
            try {
                listener.invoke(snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking clipboard listener: ${e.message}")
            }
        }
    }

    @Synchronized
    fun getClips(): List<ClipboardItem> {
        ensureLoaded()
        // Pinned items first, then most recent
        return clipsList.sortedWith(compareByDescending<ClipboardItem> { it.isPinned }.thenByDescending { it.timestamp })
    }

    @Synchronized
    fun getClipCount(): Int {
        ensureLoaded()
        return clipsList.size
    }

    /**
     * Checks the system clipboard and captures new content if available.
     */
    @Synchronized
    fun captureCurrentClipboard(context: Context): Boolean {
        ensureLoaded()
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null && cm.hasPrimaryClip()) {
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                    if (!text.isNullOrEmpty()) {
                        return addClip(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read system clipboard: ${e.message}")
        }
        return false
    }

    /**
     * Adds text to history. Returns true if newly added or updated.
     */
    @Synchronized
    fun addClip(text: String, isPinned: Boolean = false): Boolean {
        ensureLoaded()
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return false

        // Check if item already exists
        val existingIndex = clipsList.indexOfFirst { it.text == cleanText }
        if (existingIndex != -1) {
            val existing = clipsList.removeAt(existingIndex)
            val updated = existing.copy(
                timestamp = System.currentTimeMillis(),
                isPinned = if (isPinned) true else existing.isPinned
            )
            clipsList.add(0, updated)
            persistClips()
            notifyListeners()
            return true
        }

        // Add as new item
        val newItem = ClipboardItem(
            id = System.currentTimeMillis(),
            text = cleanText,
            timestamp = System.currentTimeMillis(),
            isPinned = isPinned
        )
        clipsList.add(0, newItem)

        // Prune older unpinned items if over limit
        pruneExcessItems()

        persistClips()
        notifyListeners()
        return true
    }

    @Synchronized
    fun deleteClip(id: Long) {
        ensureLoaded()
        val removed = clipsList.removeAll { it.id == id }
        if (removed) {
            persistClips()
            notifyListeners()
        }
    }

    @Synchronized
    fun togglePin(id: Long) {
        ensureLoaded()
        val index = clipsList.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = clipsList[index]
            clipsList[index] = item.copy(isPinned = !item.isPinned)
            persistClips()
            notifyListeners()
        }
    }

    @Synchronized
    fun clearAll(keepPinned: Boolean = true) {
        ensureLoaded()
        if (keepPinned) {
            clipsList.removeAll { !it.isPinned }
        } else {
            clipsList.clear()
        }
        persistClips()
        notifyListeners()
    }

    private fun pruneExcessItems() {
        val unpinned = clipsList.filter { !it.isPinned }
        if (unpinned.size > MAX_UNPINNED_ITEMS) {
            val toRemove = unpinned.drop(MAX_UNPINNED_ITEMS).map { it.id }.toSet()
            clipsList.removeAll { toRemove.contains(it.id) }
        }
    }

    @Synchronized
    private fun ensureLoaded() {
        if (isLoaded) return
        clipsList.clear()

        val jsonString = prefs.getString(KEY_CLIPS, null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id", System.currentTimeMillis() + i)
                    val text = obj.optString("text", "")
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val isPinned = obj.optBoolean("isPinned", false)

                    if (text.isNotEmpty()) {
                        clipsList.add(ClipboardItem(id = id, text = text, timestamp = timestamp, isPinned = isPinned))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse saved clipboard clips", e)
            }
        }
        isLoaded = true
    }

    private fun persistClips() {
        try {
            val array = JSONArray()
            for (item in clipsList) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("isPinned", item.isPinned)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist clipboard clips", e)
        }
    }
}
