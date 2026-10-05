package com.example.barcodekeyboard.service

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.barcodekeyboard.core.camera.BarcodeAnalyzer
import com.example.barcodekeyboard.core.camera.CameraManager
import com.example.barcodekeyboard.core.feedback.BeepSoundManager
import com.example.barcodekeyboard.core.feedback.VibrationHelper
import com.example.barcodekeyboard.core.input.InputTextHandler
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.ui.keyboard.KeyboardView
import com.example.barcodekeyboard.ui.settings.SettingsActivity

/**
 * Main InputMethodService orchestrating the HeliBoard-style keyboard,
 * dynamic theme switching (Dark/Light mode), CameraX barcode scanner,
 * and text manipulation.
 */
class BarcodeKeyboardService : InputMethodService(), LifecycleOwner {

    companion object {
        private const val TAG = "BarcodeKeyboardService"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private lateinit var preferences: KeyboardPreferences
    private lateinit var inputTextHandler: InputTextHandler
    private lateinit var vibrationHelper: VibrationHelper
    private lateinit var beepSoundManager: BeepSoundManager

    private var keyboardView: KeyboardView? = null
    private var cameraManager: CameraManager? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KeyboardPreferences.KEY_THEME) {
            val isDark = preferences.isDarkTheme(this)
            keyboardView?.applyTheme(isDark)
            Log.d(TAG, "Theme changed via settings, isDark: $isDark")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        preferences = KeyboardPreferences(this)
        inputTextHandler = InputTextHandler()
        vibrationHelper = VibrationHelper(this)
        beepSoundManager = BeepSoundManager(this)

        preferences.registerListener(prefChangeListener)

        Log.d(TAG, "BarcodeKeyboardService created")
    }

    override fun onCreateInputView(): View {
        Log.d(TAG, "onCreateInputView")
        val view = KeyboardView(this, layoutInflater)
        keyboardView = view

        // Apply theme immediately
        val isDark = preferences.isDarkTheme(this)
        view.applyTheme(isDark)

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            previewView = view.previewView
        )

        // 1. Essential Keyboard Typing Callbacks
        view.onKeyTyped = { char ->
            inputTextHandler.commitChar(currentInputConnection, char)
            triggerKeyHaptic()
        }

        view.onSpaceClicked = {
            inputTextHandler.sendSpace(currentInputConnection)
            triggerKeyHaptic()
        }

        view.onBackspaceClicked = {
            inputTextHandler.sendBackspace(currentInputConnection)
            triggerKeyHaptic()
        }

        view.onEnterClicked = {
            inputTextHandler.sendEnter(currentInputConnection)
            triggerKeyHaptic()
        }

        view.onCursorLeftClicked = {
            inputTextHandler.moveCursorLeft(currentInputConnection)
            triggerKeyHaptic()
        }

        view.onCursorRightClicked = {
            inputTextHandler.moveCursorRight(currentInputConnection)
            triggerKeyHaptic()
        }

        view.onPasteClicked = {
            val pasted = inputTextHandler.pasteFromClipboard(this, currentInputConnection)
            if (pasted) triggerKeyHaptic()
        }

        // 2. Barcode Scanner Controls
        view.onScannerToggled = { isOpen ->
            if (isOpen) {
                checkPermissionAndStartCamera()
            } else {
                stopCameraScanning()
            }
        }

        view.onFlashClicked = {
            cameraManager?.let { cm ->
                val isTorchOn = cm.toggleTorch()
                view.updateFlashIcon(isTorchOn)
            }
        }

        // 3. Navigation Shortcuts
        view.onSettingsClicked = {
            openSettingsActivity()
        }

        view.onGrantPermissionClicked = {
            openSettingsActivity()
        }

        return view.rootView
    }

    private fun triggerKeyHaptic() {
        if (preferences.isVibrationEnabled) {
            vibrationHelper.vibrate(20)
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView")

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Re-verify theme state on input view start
        val isDark = preferences.isDarkTheme(this)
        if (keyboardView?.isDarkMode != isDark) {
            keyboardView?.applyTheme(isDark)
        }

        // If scanner was previously toggled open by user, restart camera
        if (keyboardView?.isScannerOpen == true) {
            checkPermissionAndStartCamera()
        }
    }

    private fun checkPermissionAndStartCamera() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        keyboardView?.showPermissionPrompt(!hasCameraPermission)

        if (hasCameraPermission) {
            startCameraScanning()
        }
    }

    private fun startCameraScanning() {
        val analyzer = BarcodeAnalyzer { scanResult ->
            // Audio feedback
            if (preferences.isSoundEnabled) {
                beepSoundManager.playBeep()
            }

            // Haptic feedback
            if (preferences.isVibrationEnabled) {
                vibrationHelper.vibrate(80)
            }

            // Commit scan result with prefix/suffix/auto-enter
            inputTextHandler.commitScanResult(
                inputConnection = currentInputConnection,
                scanResult = scanResult,
                preferences = preferences
            )

            // Visual feedback banner
            keyboardView?.showScannedFeedback(scanResult.text)
        }

        cameraManager?.startCamera(analyzer)
    }

    private fun stopCameraScanning() {
        cameraManager?.stopCamera()
        keyboardView?.updateFlashIcon(false)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView")

        stopCameraScanning()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BarcodeKeyboardService onDestroy")

        preferences.unregisterListener(prefChangeListener)
        cameraManager?.release()
        beepSoundManager.release()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
