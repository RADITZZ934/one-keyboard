package com.example.barcodekeyboard.core.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Handles haptic vibration feedback when a barcode is scanned.
 */
class VibrationHelper(context: Context) {

    companion object {
        private const val TAG = "VibrationHelper"
        private const val DEFAULT_DURATION_MS = 75L
    }

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    fun vibrate(durationMs: Long = DEFAULT_DURATION_MS) {
        if (vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform vibration feedback", e)
        }
    }
}
