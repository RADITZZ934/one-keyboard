package com.example.barcodekeyboard.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.core.clipboard.ClipboardHistoryManager
import com.example.barcodekeyboard.data.model.ClipboardItem
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.ui.keyboard.clipboard.ClipboardAdapter

/**
 * Controller and presentation manager for the HeliBoard-inspired keyboard UI.
 * Supports dynamic Dark Mode and Light Mode switching with smooth keycap styling.
 */
class KeyboardView(
    private val context: Context,
    layoutInflater: LayoutInflater
) {

    enum class KeyboardMode {
        ALPHABET,
        SYMBOLS,
        MORE_SYMBOLS
    }

    enum class ShiftState {
        OFF,
        ONCE,
        CAPS_LOCK
    }

    enum class KeyFeedbackType {
        STANDARD,
        ACTION,
        SPACE,
        DELETE,
        RETURN
    }

    val rootView: View = layoutInflater.inflate(R.layout.view_barcode_keyboard, null)

    // Views
    val keyboardRoot: LinearLayout = rootView.findViewById(R.id.keyboardRoot)
    val keyPreviewOverlay: FrameLayout = rootView.findViewById(R.id.keyPreviewOverlay)
    val keyPreviewBubble: FrameLayout = rootView.findViewById(R.id.keyPreviewBubble)
    val tvKeyPreviewChar: TextView = rootView.findViewById(R.id.tvKeyPreviewChar)
    val scannerContainer: FrameLayout = rootView.findViewById(R.id.scannerContainer)
    val previewView: PreviewView = rootView.findViewById(R.id.cameraPreviewView)
    val scannerOverlayView: ScannerOverlayView = rootView.findViewById(R.id.scannerOverlayView)
    val btnQuickZoom: TextView = rootView.findViewById(R.id.btnQuickZoom)
    val btnViewfinderFlash: ImageButton = rootView.findViewById(R.id.btnViewfinderFlash)
    private val tvStatus: TextView = rootView.findViewById(R.id.tvStatus)
    private val btnGrantPermission: Button = rootView.findViewById(R.id.btnGrantPermission)
    private val toolbarContainer: LinearLayout = rootView.findViewById(R.id.toolbarContainer)

    // Toolbar buttons
    private val btnToggleScanner: ImageButton = rootView.findViewById(R.id.btnToggleScanner)
    private val btnFlash: ImageButton = rootView.findViewById(R.id.btnFlash)
    private val btnClipboard: ImageButton = rootView.findViewById(R.id.btnClipboard)
    private val btnPaste: ImageButton = rootView.findViewById(R.id.btnPaste)
    private val btnCursorLeft: ImageButton = rootView.findViewById(R.id.btnCursorLeft)
    private val btnCursorRight: ImageButton = rootView.findViewById(R.id.btnCursorRight)
    private val btnSettings: ImageButton = rootView.findViewById(R.id.btnSettings)

    // Clipboard Panel Views
    private val clipboardContainer: LinearLayout = rootView.findViewById(R.id.clipboardContainer)
    private val tvClipboardCount: TextView = rootView.findViewById(R.id.tvClipboardCount)
    private val btnClearClipboard: Button = rootView.findViewById(R.id.btnClearClipboard)
    private val btnCloseClipboard: ImageButton = rootView.findViewById(R.id.btnCloseClipboard)
    private val rvClipboardItems: RecyclerView = rootView.findViewById(R.id.rvClipboardItems)
    private val tvClipboardEmpty: TextView = rootView.findViewById(R.id.tvClipboardEmpty)

    private val clipboardHistoryManager = ClipboardHistoryManager.getInstance(context)
    private lateinit var clipboardAdapter: ClipboardAdapter

    // Key rows and keypad container
    val keypadContainer: LinearLayout = rootView.findViewById(R.id.keypadContainer)
    private val rowNumber: LinearLayout = rootView.findViewById(R.id.rowNumber)
    private val row1: LinearLayout = rootView.findViewById(R.id.row1)
    private val row2: LinearLayout = rootView.findViewById(R.id.row2)
    private val row3: LinearLayout = rootView.findViewById(R.id.row3)
    private val row4: LinearLayout = rootView.findViewById(R.id.row4)

    // State
    var currentMode: KeyboardMode = KeyboardMode.ALPHABET
    var shiftState: ShiftState = ShiftState.OFF
    var isScannerOpen: Boolean = false
        private set
    var isClipboardOpen: Boolean = false
        private set
    var isDarkMode: Boolean = true
        private set

    // Callbacks
    var onKeyTyped: ((String) -> Unit)? = null
    var onBackspaceClicked: (() -> Unit)? = null
    var onEnterClicked: (() -> Unit)? = null
    var onSpaceClicked: (() -> Unit)? = null
    var onCursorLeftClicked: (() -> Unit)? = null
    var onCursorRightClicked: (() -> Unit)? = null
    var onPasteClicked: (() -> Unit)? = null
    var onClipboardItemSelected: ((ClipboardItem) -> Unit)? = null
    var onSettingsClicked: (() -> Unit)? = null
    var onScannerToggled: ((Boolean) -> Unit)? = null
    var onFlashClicked: (() -> Unit)? = null
    var onGrantPermissionClicked: (() -> Unit)? = null
    var onViewfinderTapped: (() -> Unit)? = null
    var onFocusRequested: ((Float, Float) -> Unit)? = null
    var onQuickZoomClicked: (() -> Unit)? = null
    var onDoubleTapZoom: (() -> Unit)? = null
    var onPinchZoom: ((Float) -> Unit)? = null
    var onKeyFeedback: ((KeyFeedbackType, View?) -> Unit)? = null

    private var shiftButton: ImageButton? = null

    // Backspace auto-repeat handler
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var lastShiftClickTime = 0L

    // Circle Key Highlight handler & animations
    private val previewHandler = Handler(Looper.getMainLooper())
    private var hidePreviewRunnable: Runnable? = null

    init {
        setupToolbar()
        setupClipboard()
        setupWindowInsets()
        applyKeyboardScale()
        scannerOverlayView.onViewfinderTapped = {
            onViewfinderTapped?.invoke()
        }
        scannerOverlayView.onFocusRequested = { x, y ->
            onFocusRequested?.invoke(x, y)
        }
        scannerOverlayView.onDoubleTapZoom = {
            onDoubleTapZoom?.invoke()
        }
        scannerOverlayView.onPinchZoom = { factor ->
            onPinchZoom?.invoke(factor)
        }
        btnQuickZoom.setOnClickListener {
            onQuickZoomClicked?.invoke()
        }
        btnViewfinderFlash.setOnClickListener {
            onFlashClicked?.invoke()
        }
        applyTheme(isDark = true)
    }

    private fun setupToolbar() {
        btnToggleScanner.setOnClickListener {
            toggleScanner()
        }

        btnFlash.setOnClickListener {
            onFlashClicked?.invoke()
        }

        btnClipboard.setOnClickListener {
            toggleClipboard()
        }

        btnPaste.setOnClickListener {
            onPasteClicked?.invoke()
        }

        btnCursorLeft.setOnClickListener {
            onCursorLeftClicked?.invoke()
        }

        btnCursorRight.setOnClickListener {
            onCursorRightClicked?.invoke()
        }

        btnSettings.setOnClickListener {
            onSettingsClicked?.invoke()
        }

        btnGrantPermission.setOnClickListener {
            onGrantPermissionClicked?.invoke()
        }
    }

    /**
     * Initializes window insets listener and fallback for 3-button navigation.
     */
    private fun setupWindowInsets() {
        applyBottomInsets(null)

        rootView.setOnApplyWindowInsetsListener { _, insets ->
            applyBottomInsets(insets)
            insets
        }
    }

    /**
     * Dynamically calculates and applies bottom padding to keyboardRoot
     * to prevent the Android 3-button navigation bar (Back, Home, Recents)
     * from overlapping the bottom keyboard buttons (Scan, Space, Enter).
     */
    fun applyBottomInsets(insets: WindowInsets? = null) {
        val bottomPadding = calculateBottomInsets(insets)
        if (keyboardRoot.paddingBottom != bottomPadding) {
            keyboardRoot.setPadding(
                keyboardRoot.paddingLeft,
                keyboardRoot.paddingTop,
                keyboardRoot.paddingRight,
                bottomPadding
            )
        }
    }

    private fun calculateBottomInsets(insets: WindowInsets?): Int {
        val prefs = KeyboardPreferences(context)
        val density = context.resources.displayMetrics.density

        // 1. User manual override from Settings
        when (prefs.bottomPaddingSetting) {
            KeyboardPreferences.BOTTOM_PADDING_NONE -> return 0
            KeyboardPreferences.BOTTOM_PADDING_LOW -> return (16 * density).toInt()
            KeyboardPreferences.BOTTOM_PADDING_MEDIUM -> return (28 * density).toInt()
            KeyboardPreferences.BOTTOM_PADDING_HIGH -> return (48 * density).toInt()
        }

        // 2. Auto mode:
        // A. Check passed WindowInsets from OS
        if (insets != null) {
            val navBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                insets.systemWindowInsetBottom
            }
            if (navBottom > 0) return navBottom
        }

        // B. Check rootWindowInsets if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val rInsets = rootView.rootWindowInsets
            if (rInsets != null) {
                val navBottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    rInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
                } else {
                    rInsets.systemWindowInsetBottom
                }
                if (navBottom > 0) return navBottom
            }
        }

        // C. Fallback: Detect if 3-button navigation mode is active
        // Settings.Secure navigation_mode: 0 = 3-button, 1 = 2-button, 2 = gestures
        val navMode = try {
            Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
        } catch (e: Exception) {
            0
        }

        if (navMode != 2) {
            val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navDimen = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
            if (navDimen > 0) return navDimen
            return (48 * density).toInt()
        }

        return 0
    }

    /**
     * Applies custom keyboard height/scale (80% to 130%).
     * Dynamically adjusts each keyboard row's height and re-renders keys.
     */
    fun applyKeyboardScale() {
        val prefs = KeyboardPreferences(context)
        val scale = prefs.keyboardScalePercent / 100f
        val density = context.resources.displayMetrics.density

        val baseNumberHeightDp = 44f
        val baseRowHeightDp = 46f

        val numberHeightPx = (baseNumberHeightDp * scale * density).toInt()
        val rowHeightPx = (baseRowHeightDp * scale * density).toInt()

        rowNumber.layoutParams = (rowNumber.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, numberHeightPx
        )).apply {
            height = numberHeightPx
        }

        row1.layoutParams = (row1.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx
        )).apply {
            height = rowHeightPx
        }

        row2.layoutParams = (row2.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx
        )).apply {
            height = rowHeightPx
        }

        row3.layoutParams = (row3.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx
        )).apply {
            height = rowHeightPx
        }

        row4.layoutParams = (row4.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx
        )).apply {
            height = rowHeightPx
        }

        renderKeyboard()
    }

    /**
     * Applies either Dark Theme or Light Theme dynamically across all UI components.
     */
    fun applyTheme(isDark: Boolean) {
        isDarkMode = isDark

        val surfaceColor = if (isDark) {
            ContextCompat.getColor(context, R.color.heliboard_surface)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_surface_light)
        }

        val toolbarColor = if (isDark) {
            ContextCompat.getColor(context, R.color.heliboard_toolbar_bg)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_toolbar_bg_light)
        }

        rootView.setBackgroundColor(surfaceColor)
        keyboardRoot.setBackgroundColor(surfaceColor)
        toolbarContainer.setBackgroundColor(toolbarColor)

        updateToolbarIconColors()
        updatePreviewBubbleStyle()
        renderKeyboard()
    }

    private fun updateToolbarIconColors() {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_accent)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_accent_light)
        }

        val subTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text_sub)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
        }

        val primaryTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        btnToggleScanner.setColorFilter(if (isScannerOpen) accentColor else subTextColor)
        btnClipboard.setColorFilter(if (isClipboardOpen) accentColor else subTextColor)
        btnPaste.setColorFilter(primaryTextColor)
        btnCursorLeft.setColorFilter(subTextColor)
        btnCursorRight.setColorFilter(subTextColor)
        btnSettings.setColorFilter(subTextColor)

        if (::clipboardAdapter.isInitialized) {
            clipboardAdapter.updateData(clipboardHistoryManager.getClips(), isDarkMode)
        }
    }

    fun toggleScanner(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        isScannerOpen = forceOpen ?: !isScannerOpen
        if (isScannerOpen && isClipboardOpen) {
            toggleClipboard(false)
        }
        scannerContainer.visibility = if (isScannerOpen) View.VISIBLE else View.GONE
        btnFlash.visibility = if (isScannerOpen) View.VISIBLE else View.GONE

        updateToolbarIconColors()
        onScannerToggled?.invoke(isScannerOpen)
    }

    private fun setupClipboard() {
        rvClipboardItems.layoutManager = LinearLayoutManager(context)
        clipboardAdapter = ClipboardAdapter(
            items = emptyList(),
            isDarkMode = isDarkMode,
            onClipClicked = { clip ->
                onClipboardItemSelected?.invoke(clip)
            },
            onPinClicked = { clip ->
                clipboardHistoryManager.togglePin(clip.id)
            },
            onDeleteClicked = { clip ->
                clipboardHistoryManager.deleteClip(clip.id)
            }
        )
        rvClipboardItems.adapter = clipboardAdapter

        btnCloseClipboard.setOnClickListener {
            toggleClipboard(false)
        }

        btnClearClipboard.setOnClickListener {
            clipboardHistoryManager.clearAll(keepPinned = true)
        }

        clipboardHistoryManager.addListener {
            rootView.post {
                refreshClipboardList()
            }
        }
    }

    fun refreshClipboardList() {
        val clips = clipboardHistoryManager.getClips()
        clipboardAdapter.updateData(clips, isDarkMode)
        tvClipboardCount.text = "(${clips.size})"
        tvClipboardEmpty.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        rvClipboardItems.visibility = if (clips.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun toggleClipboard(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        isClipboardOpen = forceOpen ?: !isClipboardOpen

        if (isClipboardOpen) {
            if (isScannerOpen) {
                toggleScanner(false)
            }
            clipboardContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
            clipboardHistoryManager.captureCurrentClipboard(context)
            refreshClipboardList()
        } else {
            clipboardContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
        }

        updateToolbarIconColors()
    }

    fun updateFlashIcon(isTorchOn: Boolean) {
        val color = if (isTorchOn) Color.parseColor("#FFD600") else Color.WHITE
        btnFlash.setColorFilter(color)
        btnViewfinderFlash.setColorFilter(color)
    }

    fun updateZoomDisplay(ratio: Float) {
        val formatted = if (ratio >= 1.95f) "2.0x" else if (ratio <= 1.05f) "1.0x" else String.format(java.util.Locale.US, "%.1fx", ratio)
        btnQuickZoom.text = formatted
        btnQuickZoom.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
            btnQuickZoom.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
        }.start()
    }

    fun setStatusText(text: String, isAccent: Boolean = false) {
        tvStatus.text = text
        tvStatus.setTextColor(if (isAccent) Color.parseColor("#00E5FF") else Color.WHITE)
    }

    fun notifyFocusResult(success: Boolean) {
        scannerOverlayView.notifyFocusResult(success)
        if (success) {
            tvStatus.text = "✓ Fokus tajam • Siap memindai"
            tvStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvStatus.text = "Ketuk layar untuk fokus • 2x zoom"
            tvStatus.setTextColor(Color.WHITE)
        }
        tvStatus.postDelayed({
            if (isScannerOpen) {
                tvStatus.text = "Ketuk layar untuk fokus • 2x zoom"
                tvStatus.setTextColor(Color.WHITE)
            }
        }, 1800)
    }

    fun setBarcodeTargetLocked(locked: Boolean) {
        scannerOverlayView.setBarcodeTargetLocked(locked)
        if (locked) {
            tvStatus.text = "🔍 Barcode terdeteksi • Tahan posisi"
            tvStatus.setTextColor(Color.parseColor("#00E676"))
        }
    }

    fun showPermissionPrompt(show: Boolean) {
        btnGrantPermission.visibility = if (show) View.VISIBLE else View.GONE
        scannerOverlayView.visibility = if (show) View.GONE else View.VISIBLE
    }

    fun showScannedFeedback(text: String) {
        scannerOverlayView.triggerSuccessAnimation()
        tvStatus.text = "✓ Berhasil: $text"
        tvStatus.setTextColor(Color.parseColor("#00E676"))
        tvStatus.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(130)
            .withEndAction {
                tvStatus.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            .start()

        tvStatus.postDelayed({
            tvStatus.text = "Arahkan kamera ke barcode/QR"
            tvStatus.setTextColor(Color.WHITE)
        }, 1900)
    }

    fun showFailedFeedback(reason: String = "") {
        scannerOverlayView.triggerFailureAnimation(reason)
        tvStatus.text = "✕ Gagal: ${reason.ifBlank { "Barcode tidak terbaca" }}"
        tvStatus.setTextColor(Color.parseColor("#FF5252"))

        // Robotic error micro-shake animation on status text
        tvStatus.animate()
            .translationXBy(14f)
            .setDuration(45)
            .withEndAction {
                tvStatus.animate()
                    .translationXBy(-28f)
                    .setDuration(45)
                    .withEndAction {
                        tvStatus.animate()
                            .translationXBy(20f)
                            .setDuration(45)
                            .withEndAction {
                                tvStatus.animate().translationX(0f).setDuration(45).start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()

        tvStatus.postDelayed({
            tvStatus.text = "Arahkan kamera ke barcode/QR"
            tvStatus.setTextColor(Color.WHITE)
        }, 1900)
    }

    fun renderKeyboard() {
        rowNumber.removeAllViews()
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()
        row4.removeAllViews()

        renderNumberRow()

        when (currentMode) {
            KeyboardMode.ALPHABET -> renderAlphabetLayout()
            KeyboardMode.SYMBOLS -> renderSymbolsLayout()
            KeyboardMode.MORE_SYMBOLS -> renderMoreSymbolsLayout()
        }
    }

    private fun renderNumberRow() {
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        for (num in numbers) {
            rowNumber.addView(createKeyButton(num, 1f) { char ->
                handleCharInput(char)
            })
        }
    }

    private fun renderAlphabetLayout() {
        val isUpper = (shiftState != ShiftState.OFF)

        // Row 1: q w e r t y u i o p
        val r1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        for (k in r1Keys) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            row1.addView(createKeyButton(label, 1f) { char ->
                handleCharInput(char)
            })
        }

        // Row 2: a s d f g h j k l
        val r2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        row2.addView(createSpacer(0.5f))
        for (k in r2Keys) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            row2.addView(createKeyButton(label, 1f) { char ->
                handleCharInput(char)
            })
        }
        row2.addView(createSpacer(0.5f))

        // Row 3: [Shift] z x c v b n m [Backspace]
        val shiftBtn = createShiftButton(1.5f)
        shiftButton = shiftBtn as? ImageButton
        row3.addView(shiftBtn)

        val r3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        for (k in r3Keys) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            row3.addView(createKeyButton(label, 1f) { char ->
                handleCharInput(char)
            })
        }

        row3.addView(createBackspaceButton(1.5f))

        // Row 4: [?123] [,] [📷 Scan (Biru)] [   Spasi   ] [.] [Enter]
        val modeBtn = createFunctionButton("?123", 1.4f, KeyFeedbackType.ACTION) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row4.addView(modeBtn)

        row4.addView(createKeyButton(",", 0.9f) { char ->
            handleCharInput(char)
        })

        // Tombol tambahan berwarna biru untuk membuka kamera scan di samping spasi
        row4.addView(createScanButton(1.3f))

        val spaceBtn = createFunctionButton("Spasi", 3.8f, KeyFeedbackType.SPACE) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 0.9f) { char ->
            handleCharInput(char)
        })

        row4.addView(createEnterButton(1.7f))
    }

    private fun renderSymbolsLayout() {
        shiftButton = null

        val r1 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
        for (k in r1) {
            row1.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        val r2 = listOf("*", "\"", "'", ":", ";", "!", "?", "/", "\\", "_")
        for (k in r2) {
            row2.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        val moreSymBtn = createFunctionButton("=\\<", 1.4f, KeyFeedbackType.ACTION) {
            currentMode = KeyboardMode.MORE_SYMBOLS
            renderKeyboard()
        }
        row3.addView(moreSymBtn)

        val r3 = listOf("~", "`", "|", "^", "=", "{", "}")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        row3.addView(createBackspaceButton(1.5f))

        val abcBtn = createFunctionButton("ABC", 1.4f, KeyFeedbackType.ACTION) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 0.9f) { char -> handleCharInput(char) })

        // Tombol tambahan berwarna biru untuk membuka kamera scan di samping spasi
        row4.addView(createScanButton(1.3f))

        val spaceBtn = createFunctionButton("Spasi", 3.8f, KeyFeedbackType.SPACE) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 0.9f) { char -> handleCharInput(char) })

        row4.addView(createEnterButton(1.7f))
    }

    private fun renderMoreSymbolsLayout() {
        shiftButton = null

        val r1 = listOf("~", "`", "|", "^", "_", "=", "{", "}", "[", "]")
        for (k in r1) {
            row1.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        val r2 = listOf("<", ">", "€", "£", "¥", "¢", "\\", "§", "°", "¿")
        for (k in r2) {
            row2.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        val symBtn = createFunctionButton("?123", 1.4f, KeyFeedbackType.ACTION) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row3.addView(symBtn)

        val r3 = listOf("«", "»", "¡", "©", "®", "™", "±")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f) { char -> handleCharInput(char) })
        }

        row3.addView(createBackspaceButton(1.5f))

        val abcBtn = createFunctionButton("ABC", 1.4f, KeyFeedbackType.ACTION) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 0.9f) { char -> handleCharInput(char) })

        // Tombol tambahan berwarna biru untuk membuka kamera scan di samping spasi
        row4.addView(createScanButton(1.3f))

        val spaceBtn = createFunctionButton("Spasi", 3.8f, KeyFeedbackType.SPACE) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 0.9f) { char -> handleCharInput(char) })

        row4.addView(createEnterButton(1.7f))
    }

    private fun handleCharInput(char: String) {
        onKeyTyped?.invoke(char)
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            updateAlphabetShiftState()
        }
    }

    /**
     * Updates Alphabet letter keycaps in-place without rebuilding the View hierarchy.
     * Eliminates GC churn and frame drops on capitalized typing.
     */
    private fun updateAlphabetShiftState() {
        if (currentMode != KeyboardMode.ALPHABET) {
            renderKeyboard()
            return
        }

        shiftButton?.let { updateShiftIcon(it) }

        val isUpper = (shiftState != ShiftState.OFF)

        fun updateRowButtons(row: LinearLayout) {
            for (i in 0 until row.childCount) {
                val v = row.getChildAt(i)
                if (v is Button) {
                    val current = v.text.toString()
                    v.text = if (isUpper) current.toUpperCase(java.util.Locale.ROOT) else current.toLowerCase(java.util.Locale.ROOT)
                }
            }
        }

        updateRowButtons(row1)
        updateRowButtons(row2)
        updateRowButtons(row3)
    }

    private fun updateShiftIcon(btn: ImageButton) {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_accent)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_accent_light)
        }

        val subTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text_sub)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
        }

        when (shiftState) {
            ShiftState.OFF -> {
                btn.setImageResource(R.drawable.ic_shift)
                btn.setColorFilter(subTextColor)
            }
            ShiftState.ONCE -> {
                btn.setImageResource(R.drawable.ic_shift)
                btn.setColorFilter(accentColor)
            }
            ShiftState.CAPS_LOCK -> {
                btn.setImageResource(R.drawable.ic_shift_caps)
                btn.setColorFilter(accentColor)
            }
        }
    }

    private fun updatePreviewBubbleStyle() {
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isDarkMode) {
                // Sleek deep dark surface with glowing vibrant blue / cyan border
                setColor(Color.parseColor("#1B2332"))
                setStroke(dpToPx(2.5f).toInt(), Color.parseColor("#00E5FF"))
            } else {
                // Crisp white surface with vibrant blue border
                setColor(Color.parseColor("#FFFFFF"))
                setStroke(dpToPx(2.5f).toInt(), Color.parseColor("#2563EB"))
            }
        }
        keyPreviewBubble.background = bgDrawable

        if (isDarkMode) {
            tvKeyPreviewChar.setTextColor(Color.WHITE)
            tvKeyPreviewChar.setShadowLayer(dpToPx(6f), 0f, 0f, Color.parseColor("#9900E5FF"))
        } else {
            tvKeyPreviewChar.setTextColor(Color.parseColor("#1D4ED8"))
            tvKeyPreviewChar.setShadowLayer(dpToPx(2f), 0f, 0f, Color.parseColor("#33000000"))
        }
    }

    private fun showCircleHighlight(keyView: View, char: String) {
        hidePreviewRunnable?.let {
            previewHandler.removeCallbacks(it)
            hidePreviewRunnable = null
        }

        tvKeyPreviewChar.text = char
        updatePreviewBubbleStyle()

        val doPosition = {
            val keyLoc = IntArray(2)
            val rootLoc = IntArray(2)
            keyView.getLocationInWindow(keyLoc)
            rootView.getLocationInWindow(rootLoc)

            val relativeX = (keyLoc[0] - rootLoc[0]).toFloat()
            val relativeY = (keyLoc[1] - rootLoc[1]).toFloat()

            val bubbleWidth = if (keyPreviewBubble.width > 0) keyPreviewBubble.width.toFloat() else dpToPx(54f)
            val bubbleHeight = if (keyPreviewBubble.height > 0) keyPreviewBubble.height.toFloat() else dpToPx(54f)

            // Center bubble horizontally above keyView
            val keyCenterX = relativeX + (keyView.width / 2f)
            var targetX = keyCenterX - (bubbleWidth / 2f)

            // Keep within horizontal bounds of keyboard
            val rootWidth = if (rootView.width > 0) rootView.width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
            val margin = dpToPx(4f)
            targetX = targetX.coerceIn(margin, maxOf(margin, rootWidth - bubbleWidth - margin))

            // Position vertically above the key by 10dp
            val targetY = maxOf(dpToPx(4f), relativeY - bubbleHeight - dpToPx(10f))

            keyPreviewBubble.translationX = targetX
            keyPreviewBubble.translationY = targetY

            keyPreviewBubble.visibility = View.VISIBLE
            keyPreviewBubble.animate().cancel()
            keyPreviewBubble.alpha = 1.0f
            keyPreviewBubble.scaleX = 0.5f
            keyPreviewBubble.scaleY = 0.5f
            keyPreviewBubble.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(70)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }

        if (keyView.width > 0 && rootView.width > 0) {
            doPosition()
        } else {
            keyView.post(doPosition)
        }
    }

    private fun hideCircleHighlight(delayMs: Long = 75L) {
        hidePreviewRunnable?.let { previewHandler.removeCallbacks(it) }

        if (delayMs <= 0) {
            keyPreviewBubble.animate().cancel()
            keyPreviewBubble.visibility = View.GONE
            return
        }

        hidePreviewRunnable = Runnable {
            keyPreviewBubble.animate().cancel()
            keyPreviewBubble.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0f)
                .setDuration(60)
                .withEndAction {
                    keyPreviewBubble.visibility = View.GONE
                }
                .start()
        }
        previewHandler.postDelayed(hidePreviewRunnable!!, delayMs)
    }

    private fun createKeyButton(
        text: String,
        weight: Float,
        onClick: (String) -> Unit
    ): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_bg)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_bg_light)
        }

        val pressedColor = if (isDarkMode) {
            Color.parseColor("#384860")
        } else {
            Color.parseColor("#BFDBFE")
        }

        val textColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        val scale = KeyboardPreferences(context).keyboardScalePercent / 100f
        val fontScale = scale.coerceIn(0.85f, 1.25f)

        val btn = Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 17f * fontScale
            setTextColor(textColor)
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        val currentChar = this.text.toString()
                        showCircleHighlight(v, currentChar)
                        onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= -v.width * 0.4f &&
                                       event.x <= v.width * 1.4f &&
                                       event.y >= -v.height * 0.4f &&
                                       event.y <= v.height * 1.4f
                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                            hideCircleHighlight(delayMs = 0)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        hideCircleHighlight(delayMs = 75)
                        if (wasPressed) {
                            v.performClick()
                            onClick(this.text.toString())
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        hideCircleHighlight(delayMs = 0)
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun createFunctionButton(
        text: String,
        weight: Float,
        feedbackType: KeyFeedbackType = KeyFeedbackType.ACTION,
        onClick: () -> Unit
    ): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_function)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_function_light)
        }

        val pressedColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed_light)
        }

        val textColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text_sub)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
        }

        val scale = KeyboardPreferences(context).keyboardScalePercent / 100f
        val fontScale = scale.coerceIn(0.85f, 1.25f)

        val btn = Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f * fontScale
            setTextColor(textColor)
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onKeyFeedback?.invoke(feedbackType, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= -v.width * 0.4f &&
                                       event.x <= v.width * 1.4f &&
                                       event.y >= -v.height * 0.4f &&
                                       event.y <= v.height * 1.4f
                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (wasPressed) {
                            v.performClick()
                            onClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun createShiftButton(weight: Float): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_function)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_function_light)
        }

        val pressedColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed_light)
        }

        val btn = ImageButton(context).apply {
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            updateShiftIcon(this)

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= -v.width * 0.4f &&
                                       event.x <= v.width * 1.4f &&
                                       event.y >= -v.height * 0.4f &&
                                       event.y <= v.height * 1.4f
                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (wasPressed) {
                            v.performClick()
                            val now = System.currentTimeMillis()
                            if (now - lastShiftClickTime < 350) {
                                shiftState = if (shiftState == ShiftState.CAPS_LOCK) ShiftState.OFF else ShiftState.CAPS_LOCK
                            } else {
                                shiftState = when (shiftState) {
                                    ShiftState.OFF -> ShiftState.ONCE
                                    ShiftState.ONCE -> ShiftState.OFF
                                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                                }
                            }
                            lastShiftClickTime = now
                            updateAlphabetShiftState()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun createBackspaceButton(weight: Float): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_function)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_function_light)
        }

        val pressedColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed_light)
        }

        val subTextColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text_sub)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
        }

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(subTextColor)
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onKeyFeedback?.invoke(KeyFeedbackType.DELETE, v)
                        onBackspaceClicked?.invoke()
                        startBackspaceRepeat(v)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopBackspaceRepeat()
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun startBackspaceRepeat(targetView: View) {
        stopBackspaceRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                onKeyFeedback?.invoke(KeyFeedbackType.DELETE, targetView)
                onBackspaceClicked?.invoke()
                repeatHandler.postDelayed(this, 60)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 350)
    }

    private fun stopBackspaceRepeat() {
        repeatRunnable?.let {
            repeatHandler.removeCallbacks(it)
            repeatRunnable = null
        }
    }

    private fun createEnterButton(weight: Float): View {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_accent)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_accent_light)
        }

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_enter)
            setColorFilter(Color.WHITE)
            background = createKeyDrawable(accentColor, 0x80FFFFFF.toInt(), 6f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onKeyFeedback?.invoke(KeyFeedbackType.RETURN, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= -v.width * 0.4f &&
                                       event.x <= v.width * 1.4f &&
                                       event.y >= -v.height * 0.4f &&
                                       event.y <= v.height * 1.4f
                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (wasPressed) {
                            v.performClick()
                            onEnterClicked?.invoke()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun createScanButton(weight: Float): View {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_accent)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_accent_light)
        }

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_scan)
            setColorFilter(Color.WHITE)
            background = createKeyDrawable(accentColor, 0x80FFFFFF.toInt(), 6f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(0, 0, 0, 0)
            contentDescription = "Buka Kamera Scan"

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val isInside = event.x >= -v.width * 0.4f &&
                                       event.x <= v.width * 1.4f &&
                                       event.y >= -v.height * 0.4f &&
                                       event.y <= v.height * 1.4f
                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (wasPressed) {
                            v.performClick()
                            toggleScanner()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }

        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        btn.layoutParams = params
        return btn
    }

    private fun createSpacer(weight: Float): View {
        val v = View(context)
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
        return v
    }

    private fun createKeyDrawable(normalColor: Int, pressedColor: Int, radiusDp: Float): Drawable {
        val radiusPx = dpToPx(radiusDp)

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(normalColor)
            cornerRadius = radiusPx
        }

        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = radiusPx
        }

        val colorStateList = ColorStateList.valueOf(pressedColor)
        return RippleDrawable(colorStateList, normalDrawable, maskDrawable)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
