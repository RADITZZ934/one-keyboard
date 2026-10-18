package com.example.barcodekeyboard.core.gif

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.example.barcodekeyboard.data.model.tenor.TenorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloading, caching, and committing animated GIFs to target applications
 * via InputConnectionCompat (Rich Content) with graceful fallback to plain text URL.
 */
class GifCommitHelper(private val context: Context) {

    companion object {
        private const val TAG = "GifCommitHelper"
        private const val MIME_TYPE_GIF = "image/gif"
        private const val MAX_CACHE_SIZE_BYTES = 25 * 1024 * 1024L // 25 MB max cache
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "gif_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Checks whether the focused input field in the active application supports GIF insertion.
     */
    fun isGifContentSupported(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
        for (type in mimeTypes) {
            if (ClipDescription.compareMimeTypes(type, MIME_TYPE_GIF)) {
                return true
            }
        }
        return false
    }

    /**
     * Downloads GIF to cache and commits it into the input connection.
     * Returns true if committed as rich media, or false if fallen back to plain text URL.
     */
    suspend fun commitGif(
        inputConnection: InputConnection?,
        editorInfo: EditorInfo?,
        gifItem: TenorResult,
        onStatus: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputConnection == null) return@withContext false

        val fullUrl = gifItem.fullGifUrl
        if (fullUrl.isBlank()) return@withContext false

        val supportsRichContent = isGifContentSupported(editorInfo)

        if (supportsRichContent && editorInfo != null) {
            try {
                onStatus?.invoke("Menyiapkan GIF...")
                val localFile = downloadOrGetFromCache(gifItem.id, fullUrl)
                if (localFile != null && localFile.exists() && localFile.length() > 0) {
                    val authority = "${context.packageName}.fileprovider"
                    val contentUri = FileProvider.getUriForFile(context, authority, localFile)
                    val description = ClipDescription(
                        gifItem.title ?: "Tenor GIF",
                        arrayOf(MIME_TYPE_GIF)
                    )
                    val linkUri = Uri.parse(fullUrl)
                    val contentInfo = InputContentInfoCompat(contentUri, description, linkUri)

                    var flags = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    }

                    val committed = withContext(Dispatchers.Main) {
                        InputConnectionCompat.commitContent(
                            inputConnection,
                            editorInfo,
                            contentInfo,
                            flags,
                            null
                        )
                    }

                    if (committed) {
                        Log.d(TAG, "GIF committed successfully via rich content")
                        return@withContext true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error committing rich GIF content, falling back to text URL", e)
            }
        }

        // Fallback: commit direct URL as text if app does not support rich content
        withContext(Dispatchers.Main) {
            inputConnection.commitText(fullUrl, 1)
        }
        Log.d(TAG, "GIF inserted as text URL (fallback)")
        return@withContext false
    }

    private fun downloadOrGetFromCache(id: String, downloadUrl: String): File? {
        val safeId = if (id.isNotBlank()) id.replace(Regex("[^a-zA-Z0-9_-]"), "_") else downloadUrl.hashCode().toString()
        val targetFile = File(cacheDir, "tenor_$safeId.gif")

        if (targetFile.exists() && targetFile.length() > 0) {
            return targetFile
        }

        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val url = URL(downloadUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
            }
            connection.connect()

            if (connection.responseCode in 200..299) {
                inputStream = connection.inputStream
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()

                cleanCacheIfNeeded()
                return targetFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download GIF: $downloadUrl", e)
            if (targetFile.exists()) targetFile.delete()
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            connection?.disconnect()
        }

        return null
    }

    private fun cleanCacheIfNeeded() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = 0L
            for (f in files) {
                totalSize += f.length()
            }
            if (totalSize > MAX_CACHE_SIZE_BYTES) {
                // Delete oldest files first
                files.sortBy { it.lastModified() }
                for (file in files) {
                    val size = file.length()
                    if (file.delete()) {
                        totalSize -= size
                        if (totalSize < MAX_CACHE_SIZE_BYTES / 2) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache cleanup error", e)
        }
    }
}
