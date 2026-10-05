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

    /**
     * Crisp, snappy single tap for success.
     */
    fun vibrateSuccess() {
        vibrate(55L)
    }

    /**
     * Distinct double-buzz pattern for failure / error.
     */
    fun vibrateFailure() {
        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 80, 70, 100)
                val amplitudes = intArrayOf(0, 220, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 80, 70, 100), -1)
            }
        } catch (e: Exception) {
            vibrate(160L)
        }
    }
}
