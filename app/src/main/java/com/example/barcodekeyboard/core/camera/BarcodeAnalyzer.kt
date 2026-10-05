package com.example.barcodekeyboard.core.camera

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.barcodekeyboard.data.model.ScanResult
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * ImageAnalysis.Analyzer that processes CameraX frames using Google ML Kit Barcode Scanning.
 */
class BarcodeAnalyzer(
    private val onBarcodeScanned: (ScanResult) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BarcodeAnalyzer"
        private const val DEBOUNCE_INTERVAL_SAME_CODE_MS = 1500L
        private const val MIN_INTERVAL_BETWEEN_SCANS_MS = 400L
    }

    private val scanner: BarcodeScanner

    private var lastScannedText: String = ""
    private var lastScannedTime: Long = 0L

    init {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val currentTime = System.currentTimeMillis()
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        val isSameText = (rawValue == lastScannedText)
                        val timeDiff = currentTime - lastScannedTime

                        if (isSameText && timeDiff < DEBOUNCE_INTERVAL_SAME_CODE_MS) {
                            // Suppress repeated scan of identical code
                            continue
                        }

                        if (timeDiff < MIN_INTERVAL_BETWEEN_SCANS_MS) {
                            continue
                        }

                        lastScannedText = rawValue
                        lastScannedTime = currentTime

                        val scanResult = ScanResult(
                            text = rawValue,
                            format = barcode.format,
                            timestamp = currentTime
                        )
                        Log.d(TAG, "Barcode detected: $rawValue (Format: ${barcode.format})")
                        onBarcodeScanned(scanResult)
                        break
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Barcode analysis failed", error)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
