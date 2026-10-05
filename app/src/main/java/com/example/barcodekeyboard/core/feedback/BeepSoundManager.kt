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

    fun playBeep() {
        // Fast path: ToneGenerator has lowest latency
        try {
            toneGenerator?.let {
                it.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator error, falling back to MediaPlayer", e)
        }

        // Fallback: MediaPlayer using R.raw.beep
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.beep)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play beep sound via MediaPlayer", e)
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
