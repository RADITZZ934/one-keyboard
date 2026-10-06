package com.example.barcodekeyboard.core.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages CameraX lifecycle, binding preview to PreviewView,
 * setting up frame analysis, and controlling torch/flash.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {

    companion object {
        private const val TAG = "CameraManager"
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

                // 1. Preview Use Case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // 2. Image Analysis Use Case
                val imageAnalysis = ImageAnalysis.Builder()
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
                Log.d(TAG, "CameraX successfully bound to lifecycle")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
            }
        }, ContextCompat.getMainExecutor(context))
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

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
