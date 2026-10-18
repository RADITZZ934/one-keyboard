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
import android.view.ViewStub
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import android.widget.EditText
import android.widget.ProgressBar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.core.clipboard.ClipboardHistoryManager
import com.example.barcodekeyboard.data.model.ClipboardItem
import com.example.barcodekeyboard.data.model.tenor.TenorResult
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.ui.keyboard.clipboard.ClipboardAdapter
import com.example.barcodekeyboard.ui.keyboard.gif.TenorGifAdapter

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
        RETURN,
        REPEAT_DELETE
    }

    val rootView: View = layoutInflater.inflate(R.layout.view_barcode_keyboard, null)

    // Views
    val keyboardRoot: LinearLayout = rootView.findViewById(R.id.keyboardRoot)
    val keyPreviewOverlay: FrameLayout = rootView.findViewById(R.id.keyPreviewOverlay)
    val keyPreviewBubble: FrameLayout = rootView.findViewById(R.id.keyPreviewBubble)
    val tvKeyPreviewChar: TextView = rootView.findViewById(R.id.tvKeyPreviewChar)
    val materialKeyBalloonView: MaterialKeyBalloonView = rootView.findViewById(R.id.materialKeyBalloonView)
    val moreKeysPillPopupView: MoreKeysPillPopupView = rootView.findViewById(R.id.moreKeysPillPopupView)
    val scannerViewStub: ViewStub = rootView.findViewById(R.id.scannerViewStub)
    var scannerContainer: View? = null
        private set
    var previewView: PreviewView? = null
        private set
    var scannerOverlayView: ScannerOverlayView? = null
        private set
    var btnQuickZoom: TextView? = null
        private set
    var btnViewfinderFlash: ImageButton? = null
        private set
    private var tvStatus: TextView? = null
    private var btnGrantPermission: Button? = null
    private val toolbarContainer: LinearLayout = rootView.findViewById(R.id.toolbarContainer)

    // Toolbar buttons (Image 1: 7 action buttons)
    val btnToggleScanner: View = rootView.findViewById(R.id.btnToggleScanner)
    private val ivScanIcon: ImageView = rootView.findViewById(R.id.ivScanIcon)
    private val tvScanLabel: TextView = rootView.findViewById(R.id.tvScanLabel)
    private val btnFlash: ImageButton = rootView.findViewById(R.id.btnFlash)
    private val btnClipboard: ImageButton = rootView.findViewById(R.id.btnClipboard)
    private val btnGif: ImageButton = rootView.findViewById(R.id.btnGif)
    private val btnCursorLeft: ImageButton = rootView.findViewById(R.id.btnCursorLeft)
    private val btnCursorRight: ImageButton = rootView.findViewById(R.id.btnCursorRight)
    private val btnToggleTools: ImageButton = rootView.findViewById(R.id.btnToggleTools)
    private val btnAiVoice: ImageButton = rootView.findViewById(R.id.btnAiVoice)

    // AI Voice Assistant Bar Views
    val aiVoiceContainer: LinearLayout = rootView.findViewById(R.id.aiVoiceContainer)
    private val ivAiVoiceWave: ImageView = rootView.findViewById(R.id.ivAiVoiceWave)
    private val tvAiVoiceStatus: TextView = rootView.findViewById(R.id.tvAiVoiceStatus)
    private val tvAiVoiceLiveText: TextView = rootView.findViewById(R.id.tvAiVoiceLiveText)
    private val btnAiVoiceStop: Button = rootView.findViewById(R.id.btnAiVoiceStop)
    private val btnAiVoiceClose: ImageButton = rootView.findViewById(R.id.btnAiVoiceClose)
    var isAiVoiceListening: Boolean = false
        private set

    // Expandable Bento Tools Drawer Views (Image 2)
    val toolsDrawerContainer: LinearLayout = rootView.findViewById(R.id.toolsDrawerContainer)
    private val btnDrawerClose: ImageButton = rootView.findViewById(R.id.btnDrawerClose)
    private val tvDrawerTitle: TextView = rootView.findViewById(R.id.tvDrawerTitle)
    private val btnDrawerShortcutGif: ImageButton = rootView.findViewById(R.id.btnDrawerShortcutGif)
    private val btnDrawerShortcutClipboard: ImageButton = rootView.findViewById(R.id.btnDrawerShortcutClipboard)
    private val btnDrawerShortcutSettings: ImageButton = rootView.findViewById(R.id.btnDrawerShortcutSettings)

    private val btnCardScanner: LinearLayout = rootView.findViewById(R.id.btnCardScanner)
    private val btnCardClipboard: LinearLayout = rootView.findViewById(R.id.btnCardClipboard)
    private val btnCardTextEdit: LinearLayout = rootView.findViewById(R.id.btnCardTextEdit)
    private val btnCardScale: LinearLayout = rootView.findViewById(R.id.btnCardScale)
    private val btnCardTheme: LinearLayout = rootView.findViewById(R.id.btnCardTheme)
    private val btnCardSettings: LinearLayout = rootView.findViewById(R.id.btnCardSettings)
    private val btnCardZoom: LinearLayout = rootView.findViewById(R.id.btnCardZoom)
    private val btnCardFeedback: LinearLayout = rootView.findViewById(R.id.btnCardFeedback)
    private val tvCardFeedback: TextView = rootView.findViewById(R.id.tvCardFeedback)
    var isToolsDrawerOpen: Boolean = false
        private set

    // Clipboard Panel Views
    private val clipboardContainer: LinearLayout = rootView.findViewById(R.id.clipboardContainer)
    private val tvClipboardCount: TextView = rootView.findViewById(R.id.tvClipboardCount)
    private val btnClearClipboard: Button = rootView.findViewById(R.id.btnClearClipboard)
    private val btnCloseClipboard: ImageButton = rootView.findViewById(R.id.btnCloseClipboard)
    private val rvClipboardItems: RecyclerView = rootView.findViewById(R.id.rvClipboardItems)
    private val tvClipboardEmpty: TextView = rootView.findViewById(R.id.tvClipboardEmpty)

    // GIF Panel Views
    val gifContainer: LinearLayout = rootView.findViewById(R.id.gifContainer)
    private val btnCloseGif: ImageButton = rootView.findViewById(R.id.btnCloseGif)
    private val llGifSearchBox: LinearLayout = rootView.findViewById(R.id.llGifSearchBox)
    private val ivGifSearchIcon: ImageView = rootView.findViewById(R.id.ivGifSearchIcon)
    val etGifSearch: EditText = rootView.findViewById(R.id.etGifSearch)
    private val btnClearGifSearch: ImageButton = rootView.findViewById(R.id.btnClearGifSearch)
    private val ivTenorBadge: ImageView = rootView.findViewById(R.id.ivTenorBadge)
    private val dividerGif: View = rootView.findViewById(R.id.dividerGif)
    private val rvGifList: RecyclerView = rootView.findViewById(R.id.rvGifList)
    private val pbGifLoading: ProgressBar = rootView.findViewById(R.id.pbGifLoading)
    private val llGifEmptyState: LinearLayout = rootView.findViewById(R.id.llGifEmptyState)
    private val tvGifEmptyTitle: TextView = rootView.findViewById(R.id.tvGifEmptyTitle)
    private val btnGifRetry: Button = rootView.findViewById(R.id.btnGifRetry)
    private val llGifSendingOverlay: LinearLayout = rootView.findViewById(R.id.llGifSendingOverlay)
    private val tvGifSendingStatus: TextView = rootView.findViewById(R.id.tvGifSendingStatus)
    private lateinit var gifAdapter: TenorGifAdapter
    var isGifOpen: Boolean = false
        private set

    // Text Edit Panel Views
    val textEditContainer: LinearLayout = rootView.findViewById(R.id.textEditContainer)
    private val btnCloseTextEdit: ImageButton = rootView.findViewById(R.id.btnCloseTextEdit)
    private val btnTextSelectAll: TextView = rootView.findViewById(R.id.btnTextSelectAll)
    private val btnTextCopy: TextView = rootView.findViewById(R.id.btnTextCopy)
    private val btnTextPaste: TextView = rootView.findViewById(R.id.btnTextPaste)
    private val btnDpadUp: ImageButton = rootView.findViewById(R.id.btnDpadUp)
    private val btnDpadDown: ImageButton = rootView.findViewById(R.id.btnDpadDown)
    private val btnDpadLeft: ImageButton = rootView.findViewById(R.id.btnDpadLeft)
    private val btnDpadRight: ImageButton = rootView.findViewById(R.id.btnDpadRight)
    var isTextEditOpen: Boolean = false
        private set

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
    var onDoubleSpaceClicked: (() -> Unit)? = null
    var onDeleteWordClicked: (() -> Unit)? = null
    var onKeyboardSizeClicked: (() -> Unit)? = null
    var onAiVoiceClicked: (() -> Unit)? = null
    var onAiVoiceStopClicked: (() -> Unit)? = null
    var onAiVoiceCancelClicked: (() -> Unit)? = null
    var onSelectAllClicked: (() -> Unit)? = null
    var onCopyClicked: (() -> Unit)? = null
    var onDpadUpClicked: (() -> Unit)? = null
    var onDpadDownClicked: (() -> Unit)? = null
    var onThemeToggleClicked: (() -> Unit)? = null
    var onFeedbackToggleClicked: (() -> Unit)? = null
    var onGifSearchRequested: ((String) -> Unit)? = null
    var onGifSelected: ((TenorResult) -> Unit)? = null

    private var shiftButton: ImageButton? = null
    private var lastSpaceTapTime = 0L

    private val alphabetHintMap = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
        "h" to "*", "j" to "-", "k" to "+", "l" to "(",
        "z" to ")", "x" to "\"", "c" to "'", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?"
    )

    private class KeyCell(
        context: Context,
        val mainTextView: TextView,
        val hintTextView: TextView?
    ) : FrameLayout(context)

    // Backspace auto-repeat handler
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var lastShiftClickTime = 0L

    // Circle Key Highlight handler & animations
    private val previewHandler = Handler(Looper.getMainLooper())
    private var hidePreviewRunnable: Runnable? = null

    init {
        setupToolbar()
        setupToolsDrawer()
        setupClipboard()
        setupGifs()
        setupTextEdit()
        setupAiVoiceBar()
        setupWindowInsets()
        applyKeyboardScale()
        applyTheme(isDark = true)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun setupToolbar() {
        btnToggleScanner.setOnClickListener {
            toggleScanner()
        }

        btnClipboard.setOnClickListener {
            toggleClipboard()
        }

        btnGif.setOnClickListener {
            toggleGif()
        }

        btnCursorLeft.setOnClickListener {
            onCursorLeftClicked?.invoke()
        }

        btnCursorRight.setOnClickListener {
            onCursorRightClicked?.invoke()
        }

        btnToggleTools.setOnClickListener {
            toggleToolsDrawer()
        }

        btnAiVoice.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onAiVoiceClicked?.invoke()
        }

        btnFlash.setOnClickListener {
            onFlashClicked?.invoke()
        }
    }

    private fun setupToolsDrawer() {
        btnDrawerClose.setOnClickListener {
            toggleToolsDrawer(false)
        }

        // Header shortcuts
        btnDrawerShortcutGif.setOnClickListener {
            toggleGif()
        }
        btnDrawerShortcutClipboard.setOnClickListener {
            toggleClipboard()
        }
        btnDrawerShortcutSettings.setOnClickListener {
            toggleToolsDrawer(false)
            onSettingsClicked?.invoke()
        }

        // Feature cards (Image 2)
        btnCardScanner.setOnClickListener {
            toggleToolsDrawer(false)
            toggleScanner(true)
        }
        btnCardClipboard.setOnClickListener {
            toggleClipboard(true)
        }
        btnCardTextEdit.setOnClickListener {
            toggleTextEdit(true)
        }
        btnCardScale.setOnClickListener {
            toggleToolsDrawer(false)
            onKeyboardSizeClicked?.invoke()
        }
        btnCardTheme.setOnClickListener {
            onThemeToggleClicked?.invoke()
            applyTheme(!isDarkMode)
        }
        btnCardSettings.setOnClickListener {
            toggleToolsDrawer(false)
            onSettingsClicked?.invoke()
        }
        btnCardZoom.setOnClickListener {
            onQuickZoomClicked?.invoke()
        }
        btnCardFeedback.setOnClickListener {
            onFeedbackToggleClicked?.invoke()
        }
    }

    private fun setupGifs() {
        btnCloseGif.setOnClickListener {
            toggleGif(false)
        }

        gifAdapter = TenorGifAdapter { gifItem, clickedView ->
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, clickedView)
            onGifSelected?.invoke(gifItem)
        }

        val gridLayoutManager = GridLayoutManager(context, 2)
        rvGifList.layoutManager = gridLayoutManager
        rvGifList.adapter = gifAdapter

        etGifSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                btnClearGifSearch.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                showGifLoading(true)
                onGifSearchRequested?.invoke(text)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnClearGifSearch.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            etGifSearch.setText("")
            btnClearGifSearch.visibility = View.GONE
            showGifLoading(true)
            onGifSearchRequested?.invoke("")
        }

        btnGifRetry.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            showGifLoading(true)
            onGifSearchRequested?.invoke(etGifSearch.text.toString().trim())
        }
    }

    private fun setupTextEdit() {
        btnCloseTextEdit.setOnClickListener {
            toggleTextEdit(false)
        }

        btnTextSelectAll.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onSelectAllClicked?.invoke()
        }

        btnTextCopy.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onCopyClicked?.invoke()
        }

        btnTextPaste.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onPasteClicked?.invoke()
        }

        btnDpadUp.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            onDpadUpClicked?.invoke()
        }

        btnDpadDown.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            onDpadDownClicked?.invoke()
        }

        btnDpadLeft.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            onCursorLeftClicked?.invoke()
        }

        btnDpadRight.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, it)
            onCursorRightClicked?.invoke()
        }
    }

    private fun setupAiVoiceBar() {
        btnAiVoiceStop.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onAiVoiceStopClicked?.invoke()
            hideAiVoiceListening()
        }

        btnAiVoiceClose.setOnClickListener {
            onKeyFeedback?.invoke(KeyFeedbackType.ACTION, it)
            onAiVoiceCancelClicked?.invoke()
            hideAiVoiceListening()
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
        val baseRowHeightDp = 52f

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
        setupGifs()
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

        // 1. Scan Trigger Pill Styling (Image 1)
        if (isScannerOpen) {
            btnToggleScanner.setBackgroundResource(R.drawable.bg_bento_scan_pill)
            ivScanIcon.setColorFilter(Color.WHITE)
            tvScanLabel.setTextColor(Color.WHITE)
        } else {
            if (isDarkMode) {
                btnToggleScanner.setBackgroundResource(R.drawable.bg_scan_pill_dark)
                ivScanIcon.setColorFilter(Color.parseColor("#CAD1DC"))
                tvScanLabel.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                btnToggleScanner.setBackgroundResource(R.drawable.bg_scan_pill_light)
                ivScanIcon.setColorFilter(Color.WHITE)
                tvScanLabel.setTextColor(Color.WHITE)
            }
        }

        // 2. Toolbar Icons (Image 1: 7 action buttons)
        btnClipboard.setColorFilter(if (isClipboardOpen) accentColor else subTextColor)
        btnGif.setColorFilter(if (isGifOpen) accentColor else subTextColor)
        btnCursorLeft.setColorFilter(subTextColor)
        btnCursorRight.setColorFilter(subTextColor)
        btnToggleTools.setColorFilter(if (isToolsDrawerOpen) accentColor else subTextColor)
        btnAiVoice.setColorFilter(if (isAiVoiceListening) accentColor else (if (isDarkMode) Color.parseColor("#CAD1DC") else Color.parseColor("#1B64BA")))

        // Drawer header shortcuts
        btnDrawerClose.setColorFilter(subTextColor)
        btnDrawerShortcutGif.setColorFilter(subTextColor)
        btnDrawerShortcutClipboard.setColorFilter(subTextColor)
        btnDrawerShortcutSettings.setColorFilter(subTextColor)

        // 3. Feature Cards Background (Image 2)
        val cardBg = if (isDarkMode) R.drawable.bg_feature_card_dark else R.drawable.bg_feature_card_light
        btnCardScanner.setBackgroundResource(cardBg)
        btnCardClipboard.setBackgroundResource(cardBg)
        btnCardTextEdit.setBackgroundResource(cardBg)
        btnCardScale.setBackgroundResource(cardBg)
        btnCardTheme.setBackgroundResource(cardBg)
        btnCardSettings.setBackgroundResource(cardBg)
        btnCardZoom.setBackgroundResource(cardBg)
        btnCardFeedback.setBackgroundResource(cardBg)

        // 4. Text Edit Navigation Pill Styling
        val pillBg = if (isDarkMode) R.drawable.bg_bento_pill_dark else R.drawable.bg_bento_pill_cream
        btnTextSelectAll.setBackgroundResource(pillBg)
        btnTextCopy.setBackgroundResource(pillBg)
        btnTextPaste.setBackgroundResource(pillBg)
        btnDpadUp.setBackgroundResource(pillBg)
        btnDpadDown.setBackgroundResource(pillBg)
        btnDpadLeft.setBackgroundResource(pillBg)
        btnDpadRight.setBackgroundResource(pillBg)

        btnTextSelectAll.setTextColor(primaryTextColor)
        btnTextCopy.setTextColor(primaryTextColor)
        btnTextPaste.setTextColor(primaryTextColor)
        btnDpadUp.setColorFilter(primaryTextColor)
        btnDpadDown.setColorFilter(primaryTextColor)
        btnDpadLeft.setColorFilter(primaryTextColor)
        btnDpadRight.setColorFilter(primaryTextColor)

        if (::clipboardAdapter.isInitialized) {
            clipboardAdapter.updateData(clipboardHistoryManager.getClips(), isDarkMode)
        }

        // 5. GIF Panel Theme Styling
        val searchBoxBg = if (isDarkMode) R.drawable.bg_bento_pill_dark else R.drawable.bg_bento_pill_cream
        llGifSearchBox.setBackgroundResource(searchBoxBg)
        etGifSearch.setTextColor(primaryTextColor)
        etGifSearch.setHintTextColor(subTextColor)
        ivGifSearchIcon.setColorFilter(subTextColor)
        btnClearGifSearch.setColorFilter(subTextColor)
        btnCloseGif.setColorFilter(subTextColor)
        ivTenorBadge.setColorFilter(accentColor)
        tvGifEmptyTitle.setTextColor(subTextColor)
        btnGifRetry.setTextColor(accentColor)
    }

    fun closeAllSubPanels() {
        isClipboardOpen = false
        isToolsDrawerOpen = false
        isGifOpen = false
        isTextEditOpen = false

        clipboardContainer.visibility = View.GONE
        toolsDrawerContainer.visibility = View.GONE
        gifContainer.visibility = View.GONE
        textEditContainer.visibility = View.GONE

        keypadContainer.visibility = View.VISIBLE
        updateToolbarIconColors()
    }

    fun toggleToolsDrawer(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        val target = forceOpen ?: !isToolsDrawerOpen
        if (target) {
            closeAllSubPanels()
            isToolsDrawerOpen = true
            toolsDrawerContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
        } else {
            isToolsDrawerOpen = false
            toolsDrawerContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
        }
        updateToolbarIconColors()
        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, btnToggleTools)
    }

    fun toggleGif(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        val target = forceOpen ?: !isGifOpen
        if (target) {
            closeAllSubPanels()
            isGifOpen = true
            gifContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
            if (::gifAdapter.isInitialized && gifAdapter.itemCount == 0) {
                showGifLoading(true)
                onGifSearchRequested?.invoke(etGifSearch.text.toString().trim())
            }
        } else {
            isGifOpen = false
            gifContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
            hideGifSending()
        }
        updateToolbarIconColors()
        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, btnGif)
    }

    fun displayGifs(gifs: List<TenorResult>) {
        pbGifLoading.visibility = View.GONE
        if (gifs.isEmpty()) {
            llGifEmptyState.visibility = View.VISIBLE
            tvGifEmptyTitle.text = "Tidak ada GIF ditemukan"
            rvGifList.visibility = View.GONE
        } else {
            llGifEmptyState.visibility = View.GONE
            rvGifList.visibility = View.VISIBLE
            if (::gifAdapter.isInitialized) {
                gifAdapter.submitList(gifs)
            }
        }
    }

    fun showGifLoading(isLoading: Boolean) {
        if (isLoading) {
            pbGifLoading.visibility = View.VISIBLE
            llGifEmptyState.visibility = View.GONE
        } else {
            pbGifLoading.visibility = View.GONE
        }
    }

    fun showGifError(errorMessage: String = "Gagal memuat GIF. Periksa koneksi internet.") {
        pbGifLoading.visibility = View.GONE
        llGifEmptyState.visibility = View.VISIBLE
        tvGifEmptyTitle.text = errorMessage
        rvGifList.visibility = View.GONE
        if (::gifAdapter.isInitialized) {
            gifAdapter.clearLoadingState()
        }
    }

    fun showGifSending(statusText: String? = null) {
        llGifSendingOverlay.visibility = View.VISIBLE
        tvGifSendingStatus.text = statusText ?: "Mengirim GIF..."
    }

    fun hideGifSending() {
        llGifSendingOverlay.visibility = View.GONE
        if (::gifAdapter.isInitialized) {
            gifAdapter.clearLoadingState()
        }
    }

    fun toggleTextEdit(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        val target = forceOpen ?: !isTextEditOpen
        if (target) {
            closeAllSubPanels()
            isTextEditOpen = true
            textEditContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
        } else {
            isTextEditOpen = false
            textEditContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
        }
        updateToolbarIconColors()
    }

    fun showAiVoiceListening(statusText: String = "Mendengarkan... Silakan bicara") {
        aiVoiceContainer.visibility = View.VISIBLE
        toolbarContainer.visibility = View.GONE
        tvAiVoiceStatus.text = statusText
        tvAiVoiceLiveText.text = "Katakan teks yang ingin diketik..."
        isAiVoiceListening = true
    }

    fun updateAiVoicePartialText(text: String) {
        if (text.isNotEmpty()) {
            tvAiVoiceLiveText.text = text
        }
    }

    fun updateAiVoiceRms(rmsdB: Float) {
        val scale = 1.0f + (rmsdB.coerceIn(0f, 10f) / 10f) * 0.3f
        ivAiVoiceWave.scaleX = scale
        ivAiVoiceWave.scaleY = scale
    }

    fun hideAiVoiceListening() {
        aiVoiceContainer.visibility = View.GONE
        toolbarContainer.visibility = View.VISIBLE
        isAiVoiceListening = false
        ivAiVoiceWave.scaleX = 1.0f
        ivAiVoiceWave.scaleY = 1.0f
    }

    fun ensureScannerInflated(): View {
        if (scannerContainer == null) {
            val inflated = scannerViewStub.inflate()
            scannerContainer = inflated
            previewView = inflated.findViewById(R.id.cameraPreviewView)
            scannerOverlayView = inflated.findViewById(R.id.scannerOverlayView)
            btnQuickZoom = inflated.findViewById(R.id.btnQuickZoom)
            btnViewfinderFlash = inflated.findViewById(R.id.btnViewfinderFlash)
            tvStatus = inflated.findViewById(R.id.tvStatus)
            btnGrantPermission = inflated.findViewById(R.id.btnGrantPermission)

            scannerOverlayView?.onViewfinderTapped = {
                onViewfinderTapped?.invoke()
            }
            scannerOverlayView?.onFocusRequested = { x, y ->
                onFocusRequested?.invoke(x, y)
            }
            scannerOverlayView?.onDoubleTapZoom = {
                onDoubleTapZoom?.invoke()
            }
            scannerOverlayView?.onPinchZoom = { factor ->
                onPinchZoom?.invoke(factor)
            }
            btnQuickZoom?.setOnClickListener {
                onQuickZoomClicked?.invoke()
            }
            btnViewfinderFlash?.setOnClickListener {
                onFlashClicked?.invoke()
            }
            btnGrantPermission?.setOnClickListener {
                onGrantPermissionClicked?.invoke()
            }
        }
        return scannerContainer!!
    }

    fun toggleScanner(forceOpen: Boolean? = null) {
        hideCircleHighlight(0)
        val shouldOpen = forceOpen ?: !isScannerOpen
        if (shouldOpen) {
            ensureScannerInflated()
            closeAllSubPanels()
        }
        isScannerOpen = shouldOpen
        scannerContainer?.visibility = if (isScannerOpen) View.VISIBLE else View.GONE
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
        val target = forceOpen ?: !isClipboardOpen

        if (target) {
            closeAllSubPanels()
            isClipboardOpen = true
            clipboardContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
            clipboardHistoryManager.captureCurrentClipboard(context)
            refreshClipboardList()
        } else {
            isClipboardOpen = false
            clipboardContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
        }

        updateToolbarIconColors()
        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, btnClipboard)
    }

    fun updateFlashIcon(isTorchOn: Boolean) {
        val color = if (isTorchOn) Color.parseColor("#FFD600") else Color.WHITE
        btnFlash.setColorFilter(color)
        btnViewfinderFlash?.setColorFilter(color)
    }

    fun updateZoomDisplay(ratio: Float) {
        val formatted = if (ratio >= 1.95f) "2.0x" else if (ratio <= 1.05f) "1.0x" else String.format(java.util.Locale.US, "%.1fx", ratio)
        val zoomBtn = btnQuickZoom ?: return
        zoomBtn.text = formatted
        zoomBtn.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).withEndAction {
            zoomBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
        }.start()
    }

    private fun updateStatusIcon(drawableResId: Int?, tintColor: Int? = null) {
        val status = tvStatus ?: return
        if (drawableResId != null) {
            val d = ContextCompat.getDrawable(context, drawableResId)?.mutate()?.apply {
                val size = dpToPx(13)
                setBounds(0, 0, size, size)
                if (tintColor != null) setTint(tintColor)
            }
            status.setCompoundDrawables(d, null, null, null)
            status.compoundDrawablePadding = dpToPx(6)
        } else {
            status.setCompoundDrawables(null, null, null, null)
        }
    }

    fun setStatusText(text: String, isAccent: Boolean = false) {
        val status = tvStatus ?: return
        status.text = text
        updateStatusIcon(null)
        status.setTextColor(if (isAccent) Color.parseColor("#00E5FF") else Color.WHITE)
    }

    fun notifyFocusResult(success: Boolean) {
        scannerOverlayView?.notifyFocusResult(success)
        val status = tvStatus ?: return
        if (success) {
            status.text = "Fokus tajam • Siap memindai"
            updateStatusIcon(R.drawable.ic_check_circle, Color.parseColor("#00E676"))
            status.setTextColor(Color.parseColor("#00E676"))
        } else {
            status.text = "Ketuk layar untuk fokus • 2x zoom"
            updateStatusIcon(null)
            status.setTextColor(Color.WHITE)
        }
        status.postDelayed({
            if (isScannerOpen) {
                status.text = "Ketuk layar untuk fokus • 2x zoom"
                updateStatusIcon(null)
                status.setTextColor(Color.WHITE)
            }
        }, 1800)
    }

    fun setBarcodeTargetLocked(locked: Boolean) {
        scannerOverlayView?.setBarcodeTargetLocked(locked)
        val status = tvStatus ?: return
        if (locked) {
            status.text = "Barcode terdeteksi • Tahan posisi"
            updateStatusIcon(R.drawable.ic_scan, Color.parseColor("#00E676"))
            status.setTextColor(Color.parseColor("#00E676"))
        }
    }

    fun showPermissionPrompt(show: Boolean) {
        btnGrantPermission?.visibility = if (show) View.VISIBLE else View.GONE
        scannerOverlayView?.visibility = if (show) View.GONE else View.VISIBLE
    }

    fun showScannedFeedback(text: String) {
        scannerOverlayView?.triggerSuccessAnimation()
        val status = tvStatus ?: return
        status.text = "Berhasil: $text"
        updateStatusIcon(R.drawable.ic_check_circle, Color.parseColor("#00E676"))
        status.setTextColor(Color.parseColor("#00E676"))
        status.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(130)
            .withEndAction {
                status.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            .start()

        status.postDelayed({
            status.text = "Arahkan kamera ke barcode/QR"
            updateStatusIcon(null)
            status.setTextColor(Color.WHITE)
        }, 1900)
    }

    fun showFailedFeedback(reason: String = "") {
        scannerOverlayView?.triggerFailureAnimation(reason)
        val status = tvStatus ?: return
        status.text = "Gagal: ${reason.ifBlank { "Barcode tidak terbaca" }}"
        updateStatusIcon(R.drawable.ic_close, Color.parseColor("#FF5252"))
        status.setTextColor(Color.parseColor("#FF5252"))

        // Robotic error micro-shake animation on status text
        status.animate()
            .translationXBy(14f)
            .setDuration(45)
            .withEndAction {
                status.animate()
                    .translationXBy(-28f)
                    .setDuration(45)
                    .withEndAction {
                        status.animate()
                            .translationXBy(20f)
                            .setDuration(45)
                            .withEndAction {
                                status.animate().translationX(0f).setDuration(45).start()
                            }
                            .start()
                    }
                    .start()
            }
            .start()

        status.postDelayed({
            status.text = "Arahkan kamera ke barcode/QR"
            status.setTextColor(Color.WHITE)
        }, 1900)
    }

    fun renderKeyboard() {
        rowNumber.removeAllViews()
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()
        row4.removeAllViews()

        val prefs = KeyboardPreferences(context)
        if (prefs.isNumberRowEnabled) {
            rowNumber.visibility = View.VISIBLE
            renderNumberRow()
        } else {
            rowNumber.visibility = View.GONE
        }

        when (currentMode) {
            KeyboardMode.ALPHABET -> renderAlphabetLayout()
            KeyboardMode.SYMBOLS -> renderSymbolsLayout()
            KeyboardMode.MORE_SYMBOLS -> renderMoreSymbolsLayout()
        }
    }

    private fun renderNumberRow() {
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        for ((index, num) in numbers.withIndex()) {
            val isLeft = (index == 0)
            val isRight = (index == numbers.lastIndex)
            rowNumber.addView(createKeyButton(num, 1f, null, isLeftEdge = isLeft, isRightEdge = isRight) { char ->
                handleCharInput(char)
            })
        }
    }

    private fun renderAlphabetLayout() {
        val isUpper = (shiftState != ShiftState.OFF)

        // Row 1: q w e r t y u i o p
        val r1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        for ((index, k) in r1Keys.withIndex()) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            val hint = if (rowNumber.visibility == View.VISIBLE) null else alphabetHintMap[k]
            val isLeft = (index == 0)
            val isRight = (index == r1Keys.lastIndex)
            row1.addView(createKeyButton(label, 1f, hint, isLeftEdge = isLeft, isRightEdge = isRight) { char ->
                handleCharInput(char)
            })
        }

        // Row 2: a s d f g h j k l
        val r2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        row2.addView(createSpacer(0.5f))
        for ((index, k) in r2Keys.withIndex()) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            val hint = alphabetHintMap[k]
            val isLeft = (index == 0)
            val isRight = (index == r2Keys.lastIndex)
            row2.addView(createKeyButton(label, 1f, hint, isLeftEdge = isLeft, isRightEdge = isRight) { char ->
                handleCharInput(char)
            })
        }
        row2.addView(createSpacer(0.5f))

        // Row 3: [Shift] z x c v b n m [Backspace]
        val shiftBtn = createShiftButton(1.5f, isLeftEdge = true)
        shiftButton = shiftBtn as? ImageButton
        row3.addView(shiftBtn)

        val r3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        for (k in r3Keys) {
            val label = if (isUpper) k.toUpperCase(java.util.Locale.ROOT) else k
            val hint = alphabetHintMap[k]
            row3.addView(createKeyButton(label, 1f, hint, isBottomRow = true) { char ->
                handleCharInput(char)
            })
        }

        row3.addView(createBackspaceButton(1.5f, isRightEdge = true))

        // Row 4: [?123] [,] [   Spacebar Luas (PixelBoard Gboard Style)   ] [.] [Enter]
        val modeBtn = createFunctionButton("?123", 1.4f, KeyFeedbackType.ACTION, isLeftEdge = true, isBottomRow = true) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row4.addView(modeBtn)

        row4.addView(createKeyButton(",", 1.0f, "<", isBottomRow = true) { char ->
            handleCharInput(char)
        })

        row4.addView(createSpaceButton(5.6f, isBottomRow = true))

        row4.addView(createKeyButton(".", 1.0f, ">", isBottomRow = true) { char ->
            handleCharInput(char)
        })

        row4.addView(createEnterButton(1.8f, isRightEdge = true, isBottomRow = true))
    }

    private fun renderSymbolsLayout() {
        shiftButton = null

        val r1 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
        for ((index, k) in r1.withIndex()) {
            val isLeft = (index == 0)
            val isRight = (index == r1.lastIndex)
            row1.addView(createKeyButton(k, 1f, null, isLeftEdge = isLeft, isRightEdge = isRight) { char -> handleCharInput(char) })
        }

        val r2 = listOf("*", "\"", "'", ":", ";", "!", "?", "/", "\\", "_")
        for ((index, k) in r2.withIndex()) {
            val isLeft = (index == 0)
            val isRight = (index == r2.lastIndex)
            row2.addView(createKeyButton(k, 1f, null, isLeftEdge = isLeft, isRightEdge = isRight) { char -> handleCharInput(char) })
        }

        val moreSymBtn = createFunctionButton("=\\<", 1.4f, KeyFeedbackType.ACTION, isLeftEdge = true) {
            currentMode = KeyboardMode.MORE_SYMBOLS
            renderKeyboard()
        }
        row3.addView(moreSymBtn)

        val r3 = listOf("~", "`", "|", "^", "=", "{", "}")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f, null, isBottomRow = true) { char -> handleCharInput(char) })
        }

        row3.addView(createBackspaceButton(1.5f, isRightEdge = true))

        val abcBtn = createFunctionButton("ABC", 1.4f, KeyFeedbackType.ACTION, isLeftEdge = true, isBottomRow = true) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 1.0f, null, isBottomRow = true) { char -> handleCharInput(char) })

        row4.addView(createSpaceButton(5.6f, isBottomRow = true))

        row4.addView(createKeyButton(".", 1.0f, null, isBottomRow = true) { char -> handleCharInput(char) })

        row4.addView(createEnterButton(1.8f, isRightEdge = true, isBottomRow = true))
    }

    private fun renderMoreSymbolsLayout() {
        shiftButton = null

        val r1 = listOf("~", "`", "|", "^", "_", "=", "{", "}", "[", "]")
        for ((index, k) in r1.withIndex()) {
            val isLeft = (index == 0)
            val isRight = (index == r1.lastIndex)
            row1.addView(createKeyButton(k, 1f, null, isLeftEdge = isLeft, isRightEdge = isRight) { char -> handleCharInput(char) })
        }

        val r2 = listOf("<", ">", "€", "£", "¥", "¢", "\\", "§", "°", "¿")
        for ((index, k) in r2.withIndex()) {
            val isLeft = (index == 0)
            val isRight = (index == r2.lastIndex)
            row2.addView(createKeyButton(k, 1f, null, isLeftEdge = isLeft, isRightEdge = isRight) { char -> handleCharInput(char) })
        }

        val symBtn = createFunctionButton("?123", 1.4f, KeyFeedbackType.ACTION, isLeftEdge = true) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row3.addView(symBtn)

        val r3 = listOf("«", "»", "¡", "©", "®", "™", "±")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f, null, isBottomRow = true) { char -> handleCharInput(char) })
        }

        row3.addView(createBackspaceButton(1.5f, isRightEdge = true))

        val abcBtn = createFunctionButton("ABC", 1.4f, KeyFeedbackType.ACTION, isLeftEdge = true, isBottomRow = true) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 1.0f, null, isBottomRow = true) { char -> handleCharInput(char) })

        row4.addView(createSpaceButton(5.6f, isBottomRow = true))

        row4.addView(createKeyButton(".", 1.0f, null, isBottomRow = true) { char -> handleCharInput(char) })

        row4.addView(createEnterButton(1.8f, isRightEdge = true, isBottomRow = true))
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
                if (v is KeyCell) {
                    val current = v.mainTextView.text.toString()
                    v.mainTextView.text = if (isUpper) current.toUpperCase(java.util.Locale.ROOT) else current.toLowerCase(java.util.Locale.ROOT)
                } else if (v is Button) {
                    val current = v.text.toString()
                    v.text = if (isUpper) current.toUpperCase(java.util.Locale.ROOT) else current.toLowerCase(java.util.Locale.ROOT)
                }
            }
        }

        updateRowButtons(row1)
        updateRowButtons(row2)
        updateRowButtons(row3)
    }

    fun updateShiftState(state: ShiftState) {
        if (shiftState != state) {
            shiftState = state
            updateAlphabetShiftState()
        }
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

        val prefs = KeyboardPreferences(context)
        val fontScale = (prefs.keyboardScalePercent / 100f).coerceIn(0.85f, 1.25f)
        materialKeyBalloonView.show(keyView, char, isDarkMode, fontScale, keyPreviewOverlay)
    }

    private fun hideCircleHighlight(delayMs: Long = 75L) {
        hidePreviewRunnable?.let { previewHandler.removeCallbacks(it) }

        if (delayMs <= 0) {
            materialKeyBalloonView.dismiss(0L)
            return
        }

        materialKeyBalloonView.dismiss(delayMs)
    }

    private fun createKeyButton(
        text: String,
        weight: Float,
        hint: String? = null,
        isLeftEdge: Boolean = false,
        isRightEdge: Boolean = false,
        isBottomRow: Boolean = false,
        onClick: (String) -> Unit
    ): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_bg)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_bg_light)
        }

        val pressedColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed_light)
        }

        val strokeColor = if (isDarkMode) {
            Color.TRANSPARENT
        } else {
            ContextCompat.getColor(context, R.color.pixelboard_key_stroke_light)
        }

        val textColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        val hintColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text_sub)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
        }

        val prefs = KeyboardPreferences(context)
        val scale = prefs.keyboardScalePercent / 100f
        val fontScale = scale.coerceIn(0.85f, 1.25f)
        val showHint = hint != null && prefs.isLongPressSymbolsEnabled

        val mainTv = TextView(context).apply {
            this.text = text
            textSize = 17.5f * fontScale
            setTextColor(textColor)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val hintTv = if (showHint) {
            TextView(context).apply {
                this.text = hint
                textSize = 9.5f * fontScale
                setTextColor(hintColor)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dpToPx(3f).toInt()
                    rightMargin = dpToPx(4.5f).toInt()
                }
            }
        } else null

        val cell = KeyCell(context, mainTv, hintTv).apply {
            background = createKeyDrawable(normalColor, pressedColor, 13f, strokeColor, if (isDarkMode) 0f else 1f)
            addView(mainTv)
            if (hintTv != null) addView(hintTv)

            var isLongPressTriggered = false
            var isFlickTriggered = false
            var downX = 0f
            var downY = 0f
            var downTime = 0L
            var longPressRunnable: Runnable? = null
            val longPressHandler = Handler(Looper.getMainLooper())

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        isLongPressTriggered = false
                        isFlickTriggered = false
                        downX = event.x
                        downY = event.y
                        downTime = System.currentTimeMillis()

                        val currentChar = mainTv.text.toString()
                        showCircleHighlight(v, currentChar)
                        onKeyFeedback?.invoke(KeyFeedbackType.STANDARD, v)

                        if (prefs.isLongPressSymbolsEnabled) {
                            val variants = KeyVariants.getVariants(currentChar, hint, shiftState != ShiftState.OFF)
                            if (variants.isNotEmpty()) {
                                longPressRunnable = Runnable {
                                    isLongPressTriggered = true
                                    materialKeyBalloonView.dismiss(0L)
                                    moreKeysPillPopupView.show(v, variants, isDarkMode, fontScale, keyPreviewOverlay)
                                    onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                                }
                                longPressHandler.postDelayed(longPressRunnable!!, 260L)
                            } else if (hint != null) {
                                longPressRunnable = Runnable {
                                    isLongPressTriggered = true
                                    showCircleHighlight(v, hint)
                                    onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                                    onClick(hint)
                                }
                                longPressHandler.postDelayed(longPressRunnable!!, 260L)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        val distance = kotlin.math.hypot(dx, dy)

                        if (isLongPressTriggered && moreKeysPillPopupView.visibility == View.VISIBLE) {
                            val loc = IntArray(2)
                            keyPreviewOverlay.getLocationInWindow(loc)
                            val rootTouchX = event.rawX - loc[0]
                            moreKeysPillPopupView.updateTouch(rootTouchX)
                            return@setOnTouchListener true
                        }

                        // Pilar 5: Quick Flick UP to directly type secondary hint/number (FUTO flick gesture)
                        if (hint != null && prefs.isLongPressSymbolsEnabled && !isLongPressTriggered && !isFlickTriggered) {
                            val elapsed = System.currentTimeMillis() - downTime
                            if (dy < -dpToPx(16f) && elapsed < 260L) {
                                isFlickTriggered = true
                                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                                showCircleHighlight(v, hint)
                                onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                            }
                        }

                        // Pilar 1 & 6: Edge key hitbox extension + Proximity vertical extension
                        val minX = if (isLeftEdge) -1000f else -v.width * 0.45f
                        val maxX = if (isRightEdge) 1000f else v.width * 1.45f
                        val maxY = if (isBottomRow) v.height * 1.75f else v.height * 1.45f
                        val minY = -v.height * 0.45f

                        // Pilar 2: Key Hysteresis Anti-Jitter (distance <= 14dp keeps key held)
                        val isInside = (distance <= dpToPx(14f)) || (event.x in minX..maxX && event.y in minY..maxY)

                        if (!isInside && v.isPressed) {
                            v.isPressed = false
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            hideCircleHighlight(delayMs = 0)
                            moreKeysPillPopupView.dismiss(0L)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        val wasPressed = v.isPressed
                        v.isPressed = false

                        if (isLongPressTriggered && moreKeysPillPopupView.visibility == View.VISIBLE) {
                            val selectedChar = moreKeysPillPopupView.getSelectedChar()
                            moreKeysPillPopupView.dismiss(0L)
                            if (selectedChar != null) {
                                onClick(selectedChar)
                            }
                        } else {
                            hideCircleHighlight(delayMs = 75)
                            if (wasPressed && !isLongPressTriggered) {
                                v.performClick()
                                if (isFlickTriggered && hint != null) {
                                    onClick(hint)
                                } else {
                                    onClick(mainTv.text.toString())
                                }
                            }
                        }
                        isFlickTriggered = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        v.isPressed = false
                        hideCircleHighlight(delayMs = 0)
                        moreKeysPillPopupView.dismiss(0L)
                        isFlickTriggered = false
                        true
                    }
                    else -> false
                }
            }
        }

        cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        return cell
    }

    private fun createSpaceButton(weight: Float, isBottomRow: Boolean = true): View {
        val normalColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_bg)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_bg_light)
        }

        val pressedColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_key_pressed_light)
        }

        val strokeColor = if (isDarkMode) {
            Color.TRANSPARENT
        } else {
            ContextCompat.getColor(context, R.color.pixelboard_key_stroke_light)
        }

        val textColor = if (isDarkMode) {
            Color.parseColor("#8E939A")
        } else {
            Color.parseColor("#70757A")
        }

        val prefs = KeyboardPreferences(context)
        val scale = prefs.keyboardScalePercent / 100f
        val fontScale = scale.coerceIn(0.85f, 1.25f)

        val btn = Button(context).apply {
            text = "One Keyboard"
            isAllCaps = false
            textSize = 12f * fontScale
            setTextColor(textColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                letterSpacing = 0.04f
            }
            background = createKeyDrawable(normalColor, pressedColor, 16f, strokeColor, if (isDarkMode) 0f else 1f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER

            var startX = 0f
            var lastStepX = 0f
            var downTime = 0L
            var isGliding = false

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        startX = event.x
                        lastStepX = event.x
                        downTime = System.currentTimeMillis()
                        isGliding = false
                        onKeyFeedback?.invoke(KeyFeedbackType.SPACE, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (prefs.isSpacebarCursorEnabled) {
                            val elapsed = System.currentTimeMillis() - downTime
                            val distFromStart = kotlin.math.abs(event.x - startX)
                            // Pilar 3: FUTO debounce delay (90ms & 12dp) to prevent accidental glides
                            if (elapsed > 90L && distFromStart > dpToPx(12f)) {
                                isGliding = true
                            }
                            if (isGliding) {
                                val pointerStep = dpToPx(16f) // FUTO sPointerStep = 16dp
                                val diff = event.x - lastStepX
                                val steps = (diff / pointerStep).toInt()
                                if (steps != 0) {
                                    lastStepX += steps * pointerStep
                                    if (steps > 0) {
                                        repeat(steps) { onCursorRightClicked?.invoke() }
                                    } else {
                                        repeat(-steps) { onCursorLeftClicked?.invoke() }
                                    }
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasPressed = v.isPressed
                        v.isPressed = false
                        if (wasPressed) {
                            if (!isGliding) {
                                v.performClick()
                                val now = System.currentTimeMillis()
                                if (prefs.isDoubleSpacePeriodEnabled && (now - lastSpaceTapTime < 320L)) {
                                    lastSpaceTapTime = 0L
                                    onDoubleSpaceClicked?.invoke()
                                } else {
                                    lastSpaceTapTime = now
                                    onSpaceClicked?.invoke()
                                }
                            }
                        }
                        isGliding = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        isGliding = false
                        true
                    }
                    else -> false
                }
            }
        }

        btn.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(3, 0, 3, 0)
        }
        return btn
    }

    private fun createFunctionButton(
        text: String,
        weight: Float,
        feedbackType: KeyFeedbackType = KeyFeedbackType.ACTION,
        isLeftEdge: Boolean = false,
        isRightEdge: Boolean = false,
        isBottomRow: Boolean = false,
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
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        val scale = KeyboardPreferences(context).keyboardScalePercent / 100f
        val fontScale = scale.coerceIn(0.85f, 1.25f)

        val btn = Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 13.5f * fontScale
            setTextColor(textColor)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            background = createKeyDrawable(normalColor, pressedColor, 14f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER

            var downX = 0f
            var downY = 0f

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        downX = event.x
                        downY = event.y
                        onKeyFeedback?.invoke(feedbackType, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        val minX = if (isLeftEdge) -1000f else -v.width * 0.45f
                        val maxX = if (isRightEdge) 1000f else v.width * 1.45f
                        val maxY = if (isBottomRow) v.height * 1.75f else v.height * 1.45f
                        val minY = -v.height * 0.45f
                        val isInside = (distance <= dpToPx(14f)) || (event.x in minX..maxX && event.y in minY..maxY)
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
    private fun createShiftButton(weight: Float, isLeftEdge: Boolean = true): View {
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
            background = createKeyDrawable(normalColor, pressedColor, 14f)
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            updateShiftIcon(this)

            var downX = 0f
            var downY = 0f

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        downX = event.x
                        downY = event.y
                        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        val minX = if (isLeftEdge) -1000f else -v.width * 0.45f
                        val maxX = v.width * 1.45f
                        val maxY = v.height * 1.45f
                        val minY = -v.height * 0.45f
                        val isInside = (distance <= dpToPx(14f)) || (event.x in minX..maxX && event.y in minY..maxY)
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

    private fun createBackspaceButton(weight: Float, isRightEdge: Boolean = true): View {
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

        val iconColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        val prefs = KeyboardPreferences(context)

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_backspace)
            setColorFilter(iconColor)
            background = createKeyDrawable(normalColor, pressedColor, 14f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            var downX = 0f
            var downY = 0f
            var isSwipingWord = false

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        downX = event.x
                        downY = event.y
                        isSwipingWord = false
                        onKeyFeedback?.invoke(KeyFeedbackType.DELETE, v)
                        onBackspaceClicked?.invoke()
                        startBackspaceRepeat(v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (prefs.isSwipeDeleteEnabled && v.isPressed) {
                            val swipeLeftDist = downX - event.x
                            // Pilar 3: FUTO sPointerBigStep = 32dp per word
                            if (swipeLeftDist > dpToPx(32f)) {
                                isSwipingWord = true
                                stopBackspaceRepeat()
                                onDeleteWordClicked?.invoke()
                                onKeyFeedback?.invoke(KeyFeedbackType.DELETE, v)
                                downX = event.x
                            }
                        }
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        val minX = -v.width * 0.45f
                        val maxX = if (isRightEdge) 1000f else v.width * 1.45f
                        val maxY = v.height * 1.45f
                        val minY = -v.height * 0.45f
                        val isInside = (distance <= dpToPx(14f)) || (event.x in minX..maxX && event.y in minY..maxY)
                        if (!isInside && v.isPressed && !isSwipingWord) {
                            v.isPressed = false
                            stopBackspaceRepeat()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopBackspaceRepeat()
                        if (isSwipingWord) {
                            isSwipingWord = false
                        }
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
        var repeatCount = 0
        repeatRunnable = object : Runnable {
            override fun run() {
                repeatCount++
                // Pilar 4: Damped micro-tick vibration during repeated Backspace deletion
                onKeyFeedback?.invoke(KeyFeedbackType.REPEAT_DELETE, targetView)
                onBackspaceClicked?.invoke()
                val nextDelay = if (repeatCount > 10) 35L else 55L
                repeatHandler.postDelayed(this, nextDelay)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 320)
    }

    private fun stopBackspaceRepeat() {
        repeatRunnable?.let {
            repeatHandler.removeCallbacks(it)
            repeatRunnable = null
        }
    }

    private fun createEnterButton(
        weight: Float,
        isRightEdge: Boolean = true,
        isBottomRow: Boolean = true
    ): View {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.pixelboard_enter_bg_dark)
        } else {
            ContextCompat.getColor(context, R.color.pixelboard_enter_bg_light)
        }

        val pressedColor = if (isDarkMode) {
            Color.parseColor("#7CACF8")
        } else {
            Color.parseColor("#0842A0")
        }

        val iconColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.pixelboard_enter_text_dark)
        } else {
            ContextCompat.getColor(context, R.color.pixelboard_enter_text_light)
        }

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_enter)
            setColorFilter(iconColor)
            background = createKeyDrawable(accentColor, pressedColor, 18f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            var downX = 0f
            var downY = 0f

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        downX = event.x
                        downY = event.y
                        onKeyFeedback?.invoke(KeyFeedbackType.RETURN, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        val minX = -v.width * 0.45f
                        val maxX = if (isRightEdge) 1000f else v.width * 1.45f
                        val maxY = if (isBottomRow) v.height * 1.75f else v.height * 1.45f
                        val minY = -v.height * 0.45f
                        val isInside = (distance <= dpToPx(14f)) || (event.x in minX..maxX && event.y in minY..maxY)
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

    private fun createScanButton(weight: Float, isBottomRow: Boolean = true): View {
        val accentColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_accent)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_accent_light)
        }

        val pressedColor = if (isDarkMode) {
            Color.parseColor("#1557B0")
        } else {
            Color.parseColor("#0842A0")
        }

        val btn = ImageButton(context).apply {
            setImageResource(R.drawable.ic_scan)
            setColorFilter(Color.WHITE)
            background = createKeyDrawable(accentColor, pressedColor, 18f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(0, 0, 0, 0)
            contentDescription = "Buka Kamera Scan"

            var downX = 0f
            var downY = 0f

            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        downX = event.x
                        downY = event.y
                        onKeyFeedback?.invoke(KeyFeedbackType.ACTION, v)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val distance = kotlin.math.hypot(event.x - downX, event.y - downY)
                        val maxY = if (isBottomRow) v.height * 1.75f else v.height * 1.45f
                        val isInside = (distance <= dpToPx(14f)) || (event.x >= -v.width * 0.45f && event.x <= v.width * 1.45f && event.y >= -v.height * 0.45f && event.y <= maxY)
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

    private fun createKeyDrawable(
        normalColor: Int,
        pressedColor: Int,
        radiusDp: Float,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Float = 0f
    ): Drawable {
        val radiusPx = dpToPx(radiusDp)

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(normalColor)
            cornerRadius = radiusPx
            if (strokeWidthDp > 0f && strokeColor != Color.TRANSPARENT) {
                setStroke(dpToPx(strokeWidthDp).toInt(), strokeColor)
            }
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
