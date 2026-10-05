package com.example.barcodekeyboard.core.feedback

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.util.Log
import com.example.barcodekeyboard.R

/**
 * Manages audio beep feedback on successful barcode scanning.
 */
class BeepSoundManager(private val context: Context) {

    companion object {
        private const val TAG = "BeepSoundManager"
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator initialization failed, will use MediaPlayer fallback", e)
        }
    }

    /**
     * Plays a high-pitched, crisp confirmation chime for successful scan.
     */
    fun playSuccessBeep() {
        try {
            toneGenerator?.let {
                it.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator success beep error, using fallback", e)
        }

        playFallbackSound()
    }

    /**
     * Plays a distinct lower-pitched warning/error tone when scan fails.
     */
    fun playFailureBeep() {
        try {
            toneGenerator?.let {
                it.startTone(ToneGenerator.TONE_PROP_NACK, 240)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator failure beep error", e)
        }
    }

    /**
     * Legacy alias for playSuccessBeep.
     */
    fun playBeep() {
        playSuccessBeep()
    }

    private fun playFallbackSound() {
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play fallback sound via MediaPlayer", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ToneGenerator", e)
        }
    }
}
