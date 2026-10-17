package com.example.barcodekeyboard.core.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.barcodekeyboard.data.model.ScanResult
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * ImageAnalysis.Analyzer that processes CameraX frames using Google ML Kit Barcode Scanning.
 * Features:
 * - Anti-duplicate LRU cache (prevents duplicate/triple scans of the same code)
 * - Configurable sensitivity threshold (multi-frame candidate confirmation to filter noise/hypersensitivity)
 * - Temporary scan pause during success animation/feedback
 * - Spatial center-distance prioritization when multiple barcodes appear in the frame
 */
class BarcodeAnalyzer(
    private val preferences: KeyboardPreferences? = null,
    private val onBarcodeScanned: (ScanResult) -> Unit,
    private val onScanFailed: ((String) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BarcodeAnalyzer"
        private const val DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS = 3500L
        private const val MIN_INTERVAL_BETWEEN_SCANS_MS = 1200L
        private const val FAILURE_DEBOUNCE_INTERVAL_MS = 1800L
        private const val SUCCESS_PAUSE_DURATION_MS = 900L
        private const val MAX_RECENT_CACHE_SIZE = 20
    }

    private val scanner: BarcodeScanner

    var onBarcodeTracking: ((Boolean) -> Unit)? = null

    // Anti-duplicate LRU cache: barcode text -> timestamp
    private val recentScans = LinkedHashMap<String, Long>()

    // Global timestamp for last scan of ANY barcode
    private var lastAnyScanTime: Long = 0L

    // Temporary pause after successful scan to let animations finish smoothly without re-triggering
    private var pauseUntilTime: Long = 0L

    // Multi-frame candidate stability tracking for sensitivity throttling
    private var candidateText: String = ""
    private var candidateCount: Int = 0

    private var lastFailureTime: Long = 0L

    init {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    /**
     * Pauses scanning processing for [durationMs] milliseconds.
     */
    fun pauseScanning(durationMs: Long = SUCCESS_PAUSE_DURATION_MS) {
        pauseUntilTime = System.currentTimeMillis() + durationMs
        candidateText = ""
        candidateCount = 0
    }

    /**
     * Resets internal debounce and candidate state (e.g. when camera is reopened or mode switches).
     */
    fun resetState() {
        recentScans.clear()
        lastAnyScanTime = 0L
        pauseUntilTime = 0L
        candidateText = ""
        candidateCount = 0
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Check if analyzer is currently in cooldown/pause after a scan
        if (currentTime < pauseUntilTime) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val now = System.currentTimeMillis()

                if (barcodes.isEmpty()) {
                    onBarcodeTracking?.invoke(false)
                    // Reset candidate if nothing is in frame
                    candidateText = ""
                    candidateCount = 0
                    return@addOnSuccessListener
                }

                onBarcodeTracking?.invoke(true)
                var hasValidBarcode = false

                // If multiple barcodes exist, sort by proximity to frame center (closest to reticle first)
                val sortedBarcodes = if (barcodes.size > 1) {
                    val cx = imageProxy.width / 2f
                    val cy = imageProxy.height / 2f
                    barcodes.sortedBy { b ->
                        val box = b.boundingBox
                        if (box != null) {
                            val dx = box.centerX() - cx
                            val dy = box.centerY() - cy
                            dx * dx + dy * dy
                        } else {
                            Float.MAX_VALUE
                        }
                    }
                } else {
                    barcodes
                }

                val debounceDelay = preferences?.scanDebounceDelayMs ?: DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS
                val requiredFrames = preferences?.requiredConsecutiveFrames ?: 2

                for (barcode in sortedBarcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        // 1. Check duplicate scan for this identical barcode
                        val lastScannedTime = recentScans[rawValue] ?: 0L
                        if (now - lastScannedTime < debounceDelay) {
                            hasValidBarcode = true
                            continue
                        }

                        // 2. Check minimum interval between ANY scans
                        if (now - lastAnyScanTime < MIN_INTERVAL_BETWEEN_SCANS_MS) {
                            hasValidBarcode = true
                            continue
                        }

                        // 3. Multi-frame candidate stability (Sensitivity Throttling)
                        if (requiredFrames > 1) {
                            if (rawValue == candidateText) {
                                candidateCount++
                            } else {
                                candidateText = rawValue
                                candidateCount = 1
                            }

                            if (candidateCount < requiredFrames) {
                                // Awaiting stable confirmation frame, do not commit yet
                                hasValidBarcode = true
                                break
                            }
                        }

                        // 4. Barcode is verified and confirmed!
                        candidateText = ""
                        candidateCount = 0

                        recentScans[rawValue] = now
                        if (recentScans.size > MAX_RECENT_CACHE_SIZE) {
                            val oldestKey = recentScans.keys.firstOrNull()
                            if (oldestKey != null) {
                                recentScans.remove(oldestKey)
                            }
                        }
                        lastAnyScanTime = now

                        // Lock analyzer temporarily during visual/haptic feedback
                        pauseUntilTime = now + SUCCESS_PAUSE_DURATION_MS
                        hasValidBarcode = true

                        val scanResult = ScanResult(
                            text = rawValue,
                            format = barcode.format,
                            timestamp = now
                        )
                        Log.d(TAG, "Barcode confirmed: $rawValue (Format: ${barcode.format})")
                        onBarcodeScanned(scanResult)
                        break
                    }
                }

                // If ML Kit found barcode bounding shapes but none yielded readable text
                if (!hasValidBarcode && barcodes.isNotEmpty()) {
                    if (now - lastFailureTime > FAILURE_DEBOUNCE_INTERVAL_MS) {
                        lastFailureTime = now
                        Log.w(TAG, "Barcode shape found but rawValue was null/unreadable")
                        onScanFailed?.invoke("Barcode tidak dapat dibaca")
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Barcode analysis failed", error)
                val now = System.currentTimeMillis()
                if (now - lastFailureTime > FAILURE_DEBOUNCE_INTERVAL_MS) {
                    lastFailureTime = now
                    onScanFailed?.invoke("Gagal menganalisis kamera")
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
