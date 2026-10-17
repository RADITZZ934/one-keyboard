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

    val scanFilter = BarcodeScanFilter(
        debounceDelayMs = preferences?.scanDebounceDelayMs ?: BarcodeScanFilter.DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS,
        minIntervalBetweenScansMs = BarcodeScanFilter.MIN_INTERVAL_BETWEEN_SCANS_MS,
        requiredFrames = preferences?.requiredConsecutiveFrames ?: BarcodeScanFilter.DEFAULT_REQUIRED_FRAMES,
        pauseDurationMs = BarcodeScanFilter.DEFAULT_SUCCESS_PAUSE_DURATION_MS
    )

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
    fun pauseScanning(durationMs: Long = BarcodeScanFilter.DEFAULT_SUCCESS_PAUSE_DURATION_MS) {
        scanFilter.pause(System.currentTimeMillis(), durationMs)
    }

    /**
     * Resets internal debounce and candidate state (e.g. when camera is reopened or mode switches).
     */
    fun resetState() {
        scanFilter.resetState()
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Check if analyzer is currently in cooldown/pause after a scan
        if (scanFilter.isPaused(currentTime)) {
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
                    scanFilter.processCandidate("", now)
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

                scanFilter.debounceDelayMs = preferences?.scanDebounceDelayMs ?: BarcodeScanFilter.DEFAULT_DEBOUNCE_INTERVAL_SAME_CODE_MS
                scanFilter.requiredFrames = preferences?.requiredConsecutiveFrames ?: BarcodeScanFilter.DEFAULT_REQUIRED_FRAMES

                barcodeLoop@ for (barcode in sortedBarcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        when (val result = scanFilter.processCandidate(rawValue, now)) {
                            is BarcodeScanFilter.FilterResult.Confirmed -> {
                                hasValidBarcode = true
                                val scanResult = ScanResult(
                                    text = result.text,
                                    format = barcode.format,
                                    timestamp = result.timestamp
                                )
                                Log.d(TAG, "Barcode confirmed: ${result.text} (Format: ${barcode.format})")
                                onBarcodeScanned(scanResult)
                                break@barcodeLoop
                            }
                            is BarcodeScanFilter.FilterResult.AwaitingFrames -> {
                                hasValidBarcode = true
                                break@barcodeLoop
                            }
                            is BarcodeScanFilter.FilterResult.DuplicateDebounced,
                            is BarcodeScanFilter.FilterResult.MinIntervalThrottled -> {
                                hasValidBarcode = true
                                continue@barcodeLoop
                            }
                            is BarcodeScanFilter.FilterResult.InPauseCooldown -> {
                                hasValidBarcode = true
                                break@barcodeLoop
                            }
                        }
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
