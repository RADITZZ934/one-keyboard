package com.example.barcodekeyboard.core.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Handles fine-tuned haptic vibration feedback for both keyboard typing
 * and barcode scanning using modern Android haptic APIs.
 */
class VibrationHelper(context: Context) {

    companion object {
        private const val TAG = "VibrationHelper"
        private const val DEFAULT_DURATION_MS = 75L
    }

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /**
     * Ultra-crisp transient tap for normal keyboard typing (letters/numbers).
     * First attempts view-level KEYBOARD_TAP (manufacturer calibrated),
     * then falls back to VibrationEffect.EFFECT_CLICK or an 8ms micro-pulse.
     */
    fun vibrateKeyTap(view: View? = null) {
        if (view != null) {
            val performed = try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (e: Exception) {
                false
            }
            if (performed) return
        }

        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(8L, 85)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(8L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform key tap vibration", e)
        }
    }

    /**
     * Slightly firmer tactile click for special functional keys (Backspace, Enter, Space).
     */
    fun vibrateKeyAction(view: View? = null) {
        if (view != null) {
            val performed = try {
                view.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            } catch (e: Exception) {
                false
            }
            if (performed) return
        }

        if (vibrator == null || !vibrator.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(12L, 110)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(12L)
            }
        } catch (e: Exception) {
            vibrateKeyTap(view)
        }
    }

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
