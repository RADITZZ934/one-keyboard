package com.example.barcodekeyboard.service

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

import com.example.barcodekeyboard.core.camera.BarcodeAnalyzer
import com.example.barcodekeyboard.core.camera.CameraManager
import com.example.barcodekeyboard.core.clipboard.ClipboardHistoryManager
import com.example.barcodekeyboard.core.feedback.BeepSoundManager
import com.example.barcodekeyboard.core.feedback.VibrationHelper
import com.example.barcodekeyboard.core.gif.GifCommitHelper
import com.example.barcodekeyboard.core.input.InputTextHandler
import com.example.barcodekeyboard.core.voice.AiVoiceAssistantManager
import com.example.barcodekeyboard.data.model.ClipboardItem
import com.example.barcodekeyboard.data.model.tenor.TenorResult
import com.example.barcodekeyboard.data.network.RetrofitClient
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.ui.keyboard.KeyboardView
import com.example.barcodekeyboard.ui.settings.SettingsActivity
import kotlinx.coroutines.*

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
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private lateinit var aiVoiceAssistantManager: AiVoiceAssistantManager
    private lateinit var gifCommitHelper: GifCommitHelper

    private val keyboardScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var searchJob: Job? = null

    private var keyboardView: KeyboardView? = null
    private var cameraManager: CameraManager? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KeyboardPreferences.KEY_THEME) {
            val isDark = preferences.isDarkTheme(this)
            keyboardView?.applyTheme(isDark)
            updateWindowNavigationBar(isDark)
            Log.d(TAG, "Theme changed via settings, isDark: $isDark")
        } else if (key == KeyboardPreferences.KEY_BOTTOM_PADDING) {
            keyboardView?.applyBottomInsets()
            Log.d(TAG, "Bottom padding changed via settings")
        } else if (key == KeyboardPreferences.KEY_KEYBOARD_SCALE) {
            keyboardView?.applyKeyboardScale()
            Log.d(TAG, "Keyboard scale changed via settings")
        } else if (key == KeyboardPreferences.KEY_LONG_PRESS_SYMBOLS || key == KeyboardPreferences.KEY_SHOW_NUMBER_ROW) {
            keyboardView?.renderKeyboard()
            Log.d(TAG, "Keyboard layout preference changed: $key")
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        preferences = KeyboardPreferences(this)
        inputTextHandler = InputTextHandler()
        vibrationHelper = VibrationHelper(this)
        beepSoundManager = BeepSoundManager(this)
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)
        aiVoiceAssistantManager = AiVoiceAssistantManager(this)
        gifCommitHelper = GifCommitHelper(this)

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
        updateWindowNavigationBar(isDark)
        view.applyBottomInsets()

        // 1. Instantaneous Key Touch Feedback (Haptic + Native Audio Click on ACTION_DOWN)
        view.onKeyFeedback = { type, targetView ->
            if (preferences.isVibrationEnabled) {
                when (type) {
                    KeyboardView.KeyFeedbackType.STANDARD -> vibrationHelper.vibrateKeyTap(targetView)
                    KeyboardView.KeyFeedbackType.ACTION,
                    KeyboardView.KeyFeedbackType.SPACE,
                    KeyboardView.KeyFeedbackType.RETURN -> vibrationHelper.vibrateKeyAction(targetView)
                    KeyboardView.KeyFeedbackType.DELETE -> vibrationHelper.vibrateKeyTap(targetView)
                    KeyboardView.KeyFeedbackType.REPEAT_DELETE -> vibrationHelper.vibrateRepeatKey(targetView)
                }
            }
            if (preferences.isSoundEnabled) {
                when (type) {
                    KeyboardView.KeyFeedbackType.STANDARD -> beepSoundManager.playKeyClick(android.media.AudioManager.FX_KEYPRESS_STANDARD)
                    KeyboardView.KeyFeedbackType.SPACE -> beepSoundManager.playKeyClick(android.media.AudioManager.FX_KEYPRESS_SPACEBAR)
                    KeyboardView.KeyFeedbackType.DELETE,
                    KeyboardView.KeyFeedbackType.REPEAT_DELETE -> beepSoundManager.playKeyClick(android.media.AudioManager.FX_KEYPRESS_DELETE)
                    KeyboardView.KeyFeedbackType.RETURN -> beepSoundManager.playKeyClick(android.media.AudioManager.FX_KEYPRESS_RETURN)
                    KeyboardView.KeyFeedbackType.ACTION -> beepSoundManager.playKeyClick(android.media.AudioManager.FX_KEYPRESS_STANDARD)
                }
            }
        }

        // 2. Essential Keyboard Typing Callbacks (Committed on ACTION_UP)
        view.onKeyTyped = { char ->
            inputTextHandler.commitChar(currentInputConnection, char)
            if (char == " " || char == "." || char == "?" || char == "!") {
                checkAndUpdateAutoCapitalization()
            }
        }

        view.onSpaceClicked = {
            inputTextHandler.sendSpace(currentInputConnection)
            checkAndUpdateAutoCapitalization()
        }

        view.onDoubleSpaceClicked = {
            val converted = inputTextHandler.handleDoubleSpacePeriod(currentInputConnection)
            if (converted && preferences.isAutoCapitalizationEnabled) {
                keyboardView?.updateShiftState(KeyboardView.ShiftState.ONCE)
            } else {
                checkAndUpdateAutoCapitalization()
            }
        }

        view.onBackspaceClicked = {
            inputTextHandler.sendBackspace(currentInputConnection)
            checkAndUpdateAutoCapitalization()
        }

        view.onDeleteWordClicked = {
            inputTextHandler.deletePreviousWord(currentInputConnection)
            checkAndUpdateAutoCapitalization()
        }

        view.onEnterClicked = {
            inputTextHandler.sendEnter(currentInputConnection)
            if (preferences.isAutoCapitalizationEnabled) {
                keyboardView?.updateShiftState(KeyboardView.ShiftState.ONCE)
            }
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
            if (pasted) {
                triggerKeyHaptic()
                clipboardHistoryManager.captureCurrentClipboard(this)
            }
        }

        view.onClipboardItemSelected = { clip ->
            inputTextHandler.commitText(currentInputConnection, clip.text)
            triggerKeyHaptic()
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

        view.onKeyboardSizeClicked = {
            openSettingsActivity()
        }

        view.onGrantPermissionClicked = {
            openSettingsActivity()
        }

        view.onViewfinderTapped = {
            triggerKeyHaptic()
            scheduleScanTimeout(4000L, "Tidak ada barcode terdeteksi")
        }

        view.onFocusRequested = { x, y ->
            triggerKeyHaptic()
            view.setStatusText("Memfokuskan...", isAccent = true)
            cameraManager?.focusAtPoint(x, y) { success ->
                view.notifyFocusResult(success)
            }
        }

        val handleZoomToggle = {
            triggerKeyHaptic()
            val newZoom = cameraManager?.toggleQuickZoom() ?: 1.0f
            view.updateZoomDisplay(newZoom)
        }
        view.onQuickZoomClicked = handleZoomToggle
        view.onDoubleTapZoom = handleZoomToggle

        view.onPinchZoom = { scaleFactor ->
            val current = cameraManager?.getZoomRatio() ?: 1.0f
            val target = current * scaleFactor
            cameraManager?.setZoomRatio(target)
            view.updateZoomDisplay(cameraManager?.getZoomRatio() ?: target)
        }

        // 4. Advanced Tool Drawer & Text Edit Callbacks
        view.onSelectAllClicked = {
            triggerKeyHaptic()
            inputTextHandler.selectAll(currentInputConnection)
        }

        view.onCopyClicked = {
            triggerKeyHaptic()
            inputTextHandler.copySelectedText(currentInputConnection)
            clipboardHistoryManager.captureCurrentClipboard(this)
            Toast.makeText(this, "Teks disalin ke papan klip", Toast.LENGTH_SHORT).show()
        }

        view.onDpadUpClicked = {
            triggerKeyHaptic()
            inputTextHandler.moveCursorUp(currentInputConnection)
        }

        view.onDpadDownClicked = {
            triggerKeyHaptic()
            inputTextHandler.moveCursorDown(currentInputConnection)
        }

        view.onThemeToggleClicked = {
            triggerKeyHaptic()
            val newTheme = if (preferences.isDarkTheme(this)) KeyboardPreferences.THEME_LIGHT else KeyboardPreferences.THEME_DARK
            preferences.setThemeMode(newTheme)
            updateWindowNavigationBar(newTheme == KeyboardPreferences.THEME_DARK)
        }

        view.onFeedbackToggleClicked = {
            triggerKeyHaptic()
            val newVibe = !preferences.isVibrationEnabled
            val newSound = !preferences.isSoundEnabled
            preferences.setVibrationEnabled(newVibe)
            preferences.setSoundEnabled(newSound)
            val status = if (newVibe || newSound) "Aktif" else "Nonaktif"
            Toast.makeText(this, "Efek Suara & Getar: $status", Toast.LENGTH_SHORT).show()
        }

        // 5. AI Voice Assistant Controls (Speech-to-Text)
        view.onAiVoiceClicked = {
            triggerKeyHaptic()
            if (!aiVoiceAssistantManager.isListening) {
                aiVoiceAssistantManager.startListening(
                    onReady = {
                        view.showAiVoiceListening("Mendengarkan... Silakan bicara")
                    },
                    onRms = { rms ->
                        view.updateAiVoiceRms(rms)
                    },
                    onPartial = { partial ->
                        view.updateAiVoicePartialText(partial)
                    },
                    onResult = { text ->
                        view.hideAiVoiceListening()
                        inputTextHandler.commitText(currentInputConnection, text)
                        inputTextHandler.sendSpace(currentInputConnection)
                        checkAndUpdateAutoCapitalization()
                    },
                    onError = { error ->
                        view.hideAiVoiceListening()
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                aiVoiceAssistantManager.stopListening()
                view.hideAiVoiceListening()
            }
        }

        view.onAiVoiceStopClicked = {
            triggerKeyHaptic()
            aiVoiceAssistantManager.stopListening()
            view.hideAiVoiceListening()
        }

        view.onAiVoiceCancelClicked = {
            triggerKeyHaptic()
            aiVoiceAssistantManager.stopListening()
            view.hideAiVoiceListening()
        }

        // 6. Tenor GIF Callbacks
        view.onGifSearchRequested = { query ->
            fetchGifsFromTenor(query)
        }

        view.onGifSelected = { gifItem ->
            handleGifCommit(gifItem)
        }

        return view.rootView
    }

    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null

    private fun scheduleScanTimeout(delayMs: Long, reason: String) {
        cancelScanTimeout()
        scanTimeoutRunnable = Runnable {
            if (keyboardView?.isScannerOpen == true) {
                if (preferences.isSoundEnabled) {
                    beepSoundManager.playFailureBeep()
                }
                if (preferences.isVibrationEnabled) {
                    vibrationHelper.vibrateFailure()
                }
                keyboardView?.showFailedFeedback(reason)
            }
        }.also {
            scanTimeoutHandler.postDelayed(it, delayMs)
        }
    }

    private fun cancelScanTimeout() {
        scanTimeoutRunnable?.let { scanTimeoutHandler.removeCallbacks(it) }
        scanTimeoutRunnable = null
    }

    private fun triggerKeyHaptic() {
        if (preferences.isVibrationEnabled) {
            vibrationHelper.vibrateKeyAction()
        }
    }

    private fun openSettingsActivity() {
        val intent = Intent(this, com.example.barcodekeyboard.ui.home.HomeActivity::class.java).apply {
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
        updateWindowNavigationBar(isDark)
        keyboardView?.applyBottomInsets()
        keyboardView?.applyKeyboardScale()
        keyboardView?.rootView?.requestApplyInsets()

        // Capture current clipboard content and update history
        clipboardHistoryManager.captureCurrentClipboard(this)
        keyboardView?.refreshClipboardList()

        // Intelligent Auto-Capitalization (Gboard style)
        checkAndUpdateAutoCapitalization(info)

        // If scanner was previously toggled open by user, restart camera
        if (keyboardView?.isScannerOpen == true) {
            checkPermissionAndStartCamera()
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        checkAndUpdateAutoCapitalization()
    }

    private fun checkAndUpdateAutoCapitalization(info: EditorInfo? = currentInputEditorInfo) {
        if (!preferences.isAutoCapitalizationEnabled) return
        val kv = keyboardView ?: return
        if (kv.currentMode != KeyboardView.KeyboardMode.ALPHABET) return
        if (kv.shiftState == KeyboardView.ShiftState.CAPS_LOCK) return

        val shouldCap = inputTextHandler.shouldAutoCapitalize(currentInputConnection, info)
        val targetState = if (shouldCap) KeyboardView.ShiftState.ONCE else KeyboardView.ShiftState.OFF
        if (kv.shiftState != targetState) {
            kv.updateShiftState(targetState)
        }
    }

    private fun updateWindowNavigationBar(isDark: Boolean) {
        val win = window?.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val navColor = ContextCompat.getColor(
                this,
                if (isDark) com.example.barcodekeyboard.R.color.heliboard_surface else com.example.barcodekeyboard.R.color.heliboard_surface_light
            )
            win.navigationBarColor = navColor
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
            }
            var flags = win.decorView.systemUiVisibility
            if (!isDark && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            win.decorView.systemUiVisibility = flags
        }
    }

    private fun getOrCreateCameraManager(kv: KeyboardView): CameraManager {
        return cameraManager ?: run {
            kv.ensureScannerInflated()
            val preview = kv.previewView
                ?: throw IllegalStateException("previewView must be inflated before CameraManager")
            CameraManager(
                context = this,
                lifecycleOwner = this,
                previewView = preview
            ).also { cameraManager = it }
        }
    }

    private fun checkPermissionAndStartCamera() {
        val kv = keyboardView ?: return
        kv.ensureScannerInflated()

        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        kv.showPermissionPrompt(!hasCameraPermission)

        if (hasCameraPermission) {
            startCameraScanning()
        }
    }

    private fun startCameraScanning() {
        val kv = keyboardView ?: return
        val cm = getOrCreateCameraManager(kv)

        val analyzer = BarcodeAnalyzer(
            preferences = preferences,
            onBarcodeScanned = { scanResult ->
                cancelScanTimeout()

                // Audio feedback (Success)
                if (preferences.isSoundEnabled) {
                    beepSoundManager.playSuccessBeep()
                }

                // Haptic feedback (Success)
                if (preferences.isVibrationEnabled) {
                    vibrationHelper.vibrateSuccess()
                }

                // Commit scan result with prefix/suffix/auto-enter
                inputTextHandler.commitScanResult(
                    inputConnection = currentInputConnection,
                    scanResult = scanResult,
                    preferences = preferences
                )

                // Visual feedback banner & success animation
                keyboardView?.showScannedFeedback(scanResult.text)

                // Persist to scan history
                val formatStr = when (scanResult.format) {
                    com.google.mlkit.vision.barcode.Barcode.FORMAT_QR_CODE -> "QR_CODE"
                    com.google.mlkit.vision.barcode.Barcode.FORMAT_EAN_13 -> "EAN_13"
                    com.google.mlkit.vision.barcode.Barcode.FORMAT_CODE_128 -> "CODE_128"
                    else -> "BARCODE"
                }
                com.example.barcodekeyboard.core.history.ScanHistoryManager.getInstance(this@BarcodeKeyboardService)
                    .addScan(scanResult.text, formatStr)
            },
            onScanFailed = { errorReason ->
                cancelScanTimeout()

                // Audio feedback (Failure)
                if (preferences.isSoundEnabled) {
                    beepSoundManager.playFailureBeep()
                }

                // Haptic feedback (Failure)
                if (preferences.isVibrationEnabled) {
                    vibrationHelper.vibrateFailure()
                }

                // Visual feedback banner & failure animation
                keyboardView?.showFailedFeedback(errorReason)
            }
        ).apply {
            onBarcodeTracking = { isTracking ->
                keyboardView?.setBarcodeTargetLocked(isTracking)
            }
        }

        cm.startCamera(analyzer) {
            kv.updateZoomDisplay(cm.getZoomRatio())
        }
    }

    private fun stopCameraScanning() {
        cancelScanTimeout()
        cameraManager?.stopCamera()
        keyboardView?.updateFlashIcon(false)
    }

    private fun fetchGifsFromTenor(query: String) {
        searchJob?.cancel()
        searchJob = keyboardScope.launch {
            if (query.isNotBlank()) {
                delay(320) // Debounce delay while typing
            }
            try {
                val apiKey = preferences.tenorApiKey
                val response = withContext(Dispatchers.IO) {
                    if (query.isBlank()) {
                        RetrofitClient.tenorApi.getFeaturedGifs(apiKey = apiKey)
                    } else {
                        RetrofitClient.tenorApi.searchGifs(query = query, apiKey = apiKey)
                    }
                }
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.results
                    keyboardView?.displayGifs(list)
                } else {
                    keyboardView?.showGifError("Gagal memuat GIF (Kode ${response.code()})")
                }
            } catch (e: CancellationException) {
                // Ignore cancellation from newer search
            } catch (e: Exception) {
                Log.e(TAG, "Tenor fetch error", e)
                keyboardView?.showGifError("Tidak ada koneksi internet")
            }
        }
    }

    private fun handleGifCommit(gifItem: TenorResult) {
        keyboardScope.launch {
            keyboardView?.showGifSending("Menyiapkan GIF...")
            val success = gifCommitHelper.commitGif(
                inputConnection = currentInputConnection,
                editorInfo = currentInputEditorInfo,
                gifItem = gifItem,
                onStatus = { status ->
                    keyboardView?.showGifSending(status)
                }
            )

            keyboardView?.hideGifSending()

            if (success) {
                if (preferences.isVibrationEnabled) {
                    vibrationHelper.vibrateSuccess()
                }
                keyboardView?.toggleGif(false)
            } else {
                if (preferences.isVibrationEnabled) {
                    vibrationHelper.vibrateKeyAction(null)
                }
                Toast.makeText(this@BarcodeKeyboardService, "Tautan GIF disalin / ditempel", Toast.LENGTH_SHORT).show()
                keyboardView?.toggleGif(false)
            }
        }
    }



    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView")

        searchJob?.cancel()
        keyboardView?.hideGifSending()
        stopCameraScanning()
        aiVoiceAssistantManager.stopListening()
        keyboardView?.hideAiVoiceListening()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BarcodeKeyboardService onDestroy")

        keyboardScope.cancel()
        preferences.unregisterListener(prefChangeListener)
        cameraManager?.release()
        cameraManager = null
        beepSoundManager.release()
        aiVoiceAssistantManager.stopListening()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
