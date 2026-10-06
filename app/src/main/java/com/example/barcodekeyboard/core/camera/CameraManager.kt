package com.example.barcodekeyboard.core.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manages CameraX lifecycle, binding preview to PreviewView,
 * setting up high-resolution frame analysis (720p), controlling torch/flash,
 * interactive tap-to-focus with AF/AE metering, and zoom controls.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {

    companion object {
        private const val TAG = "CameraManager"
        private val OPTIMAL_ANALYSIS_SIZE = Size(1280, 720)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var isTorchEnabled: Boolean = false
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(analyzer: BarcodeAnalyzer, onReady: (() -> Unit)? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener(Runnable {
            try {
                cameraProvider = cameraProviderFuture.get()

                // 1. High-definition Preview Use Case
                val preview = Preview.Builder()
                    .setTargetResolution(OPTIMAL_ANALYSIS_SIZE)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // 2. High-definition Image Analysis Use Case (Sharp 720p for dense barcodes)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(OPTIMAL_ANALYSIS_SIZE)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                // 3. Camera Selector (Back Camera)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind previous use cases before rebinding
                cameraProvider?.unbindAll()

                // Bind to LifecycleOwner
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                onReady?.invoke()

                // Trigger initial auto-focus at center after layout
                previewView.postDelayed({
                    focusAtCenter()
                }, 400)

                Log.d(TAG, "CameraX successfully bound to lifecycle with 720p resolution")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Interactively triggers CameraX auto-focus and auto-exposure metering
     * at specified preview coordinates (x, y).
     */
    fun focusAtPoint(x: Float, y: Float, onFocusCompleted: ((Boolean) -> Unit)? = null) {
        val cam = camera ?: run {
            onFocusCompleted?.invoke(false)
            return
        }
        try {
            val factory = previewView.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            val future = cam.cameraControl.startFocusAndMetering(action)
            future.addListener(Runnable {
                try {
                    val result = future.get()
                    val success = result.isFocusSuccessful
                    Log.d(TAG, "Focus at ($x, $y) completed. Success: $success")
                    onFocusCompleted?.invoke(success)
                } catch (e: Exception) {
                    Log.w(TAG, "Focus metering exception", e)
                    onFocusCompleted?.invoke(false)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating focusAtPoint", e)
            onFocusCompleted?.invoke(false)
        }
    }

    /**
     * Triggers autofocus at the center of the camera preview.
     */
    fun focusAtCenter(onFocusCompleted: ((Boolean) -> Unit)? = null) {
        val cx = previewView.width / 2f
        val cy = previewView.height / 2f
        if (cx > 0f && cy > 0f) {
            focusAtPoint(cx, cy, onFocusCompleted)
        } else {
            onFocusCompleted?.invoke(false)
        }
    }

    /**
     * Sets optical/digital zoom ratio within camera supported range.
     */
    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        try {
            val zoomState = cam.cameraInfo.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: 1.0f
            val maxZoom = zoomState?.maxZoomRatio ?: 5.0f
            val clamped = ratio.coerceIn(minZoom, maxZoom.coerceAtMost(4.0f))
            cam.cameraControl.setZoomRatio(clamped)
            Log.d(TAG, "Zoom set to $clamped (requested $ratio)")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting zoom ratio", e)
        }
    }

    /**
     * Gets current zoom ratio.
     */
    fun getZoomRatio(): Float {
        return camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
    }

    /**
     * Toggles between standard 1.0x and comfortable 2.0x barcode reading zoom.
     */
    fun toggleQuickZoom(): Float {
        val current = getZoomRatio()
        val target = if (current >= 1.6f) 1.0f else 2.0f
        setZoomRatio(target)
        return target
    }

    fun stopCamera() {
        try {
            if (isTorchEnabled) {
                setTorch(false)
            }
            cameraProvider?.unbindAll()
            camera = null
            Log.d(TAG, "CameraX unbindAll executed")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping CameraX", e)
        }
    }

    fun toggleTorch(): Boolean {
        return setTorch(!isTorchEnabled)
    }

    fun setTorch(enable: Boolean): Boolean {
        val cam = camera
        if (cam != null && cam.cameraInfo.hasFlashUnit()) {
            try {
                cam.cameraControl.enableTorch(enable)
                isTorchEnabled = enable
                return isTorchEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set torch state", e)
            }
        }
        return false
    }

    fun isTorchOn(): Boolean = isTorchEnabled

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
