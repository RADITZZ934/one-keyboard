package com.example.barcodekeyboard.ui.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.example.barcodekeyboard.data.model.BarcodeItem
import com.example.barcodekeyboard.data.repository.BarcodeItemRepository
import com.example.barcodekeyboard.ui.catalog.BarcodeItemAdapter

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

    val rootView: View = layoutInflater.inflate(R.layout.view_barcode_keyboard, null)

    // Views
    val scannerContainer: FrameLayout = rootView.findViewById(R.id.scannerContainer)
    val previewView: PreviewView = rootView.findViewById(R.id.cameraPreviewView)
    val scannerOverlayView: ScannerOverlayView = rootView.findViewById(R.id.scannerOverlayView)
    private val tvStatus: TextView = rootView.findViewById(R.id.tvStatus)
    private val btnGrantPermission: Button = rootView.findViewById(R.id.btnGrantPermission)
    private val toolbarContainer: LinearLayout = rootView.findViewById(R.id.toolbarContainer)

    // Toolbar buttons
    private val btnToggleScanner: ImageButton = rootView.findViewById(R.id.btnToggleScanner)
    private val btnCatalog: ImageButton = rootView.findViewById(R.id.btnCatalog)
    private val btnFlash: ImageButton = rootView.findViewById(R.id.btnFlash)
    private val btnPaste: ImageButton = rootView.findViewById(R.id.btnPaste)
    private val btnCursorLeft: ImageButton = rootView.findViewById(R.id.btnCursorLeft)
    private val btnCursorRight: ImageButton = rootView.findViewById(R.id.btnCursorRight)
    private val btnSettings: ImageButton = rootView.findViewById(R.id.btnSettings)

    // Key rows & containers
    val keypadContainer: LinearLayout = rootView.findViewById(R.id.keypadContainer)
    val catalogContainer: LinearLayout = rootView.findViewById(R.id.catalogContainer)
    private val etCatalogSearch: EditText = rootView.findViewById(R.id.etCatalogSearch)
    private val btnClearSearch: ImageButton = rootView.findViewById(R.id.btnClearSearch)
    private val btnCloseCatalog: ImageButton = rootView.findViewById(R.id.btnCloseCatalog)
    private val tvCatalogItemCount: TextView = rootView.findViewById(R.id.tvCatalogItemCount)
    private val rvCatalogItems: RecyclerView = rootView.findViewById(R.id.rvCatalogItems)
    private val tvCatalogEmpty: TextView = rootView.findViewById(R.id.tvCatalogEmpty)

    private val barcodeItemRepository = BarcodeItemRepository.getInstance(context)
    private lateinit var catalogAdapter: BarcodeItemAdapter
    var isCatalogOpen: Boolean = false
        private set

    private val row1: LinearLayout = rootView.findViewById(R.id.row1)
    private val row2: LinearLayout = rootView.findViewById(R.id.row2)
    private val row3: LinearLayout = rootView.findViewById(R.id.row3)
    private val row4: LinearLayout = rootView.findViewById(R.id.row4)

    // State
    var currentMode: KeyboardMode = KeyboardMode.ALPHABET
    var shiftState: ShiftState = ShiftState.OFF
    var isScannerOpen: Boolean = false
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
    var onSettingsClicked: (() -> Unit)? = null
    var onScannerToggled: ((Boolean) -> Unit)? = null
    var onFlashClicked: (() -> Unit)? = null
    var onGrantPermissionClicked: (() -> Unit)? = null
    var onViewfinderTapped: (() -> Unit)? = null
    var onBarcodeItemSelected: ((BarcodeItem) -> Unit)? = null

    // Backspace auto-repeat handler
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var lastShiftClickTime = 0L

    init {
        setupToolbar()
        setupCatalog()
        scannerOverlayView.onViewfinderTapped = {
            onViewfinderTapped?.invoke()
        }
        applyTheme(isDark = true)
    }

    private fun setupToolbar() {
        btnToggleScanner.setOnClickListener {
            toggleScanner()
        }

        btnCatalog.setOnClickListener {
            toggleCatalog()
        }

        btnFlash.setOnClickListener {
            onFlashClicked?.invoke()
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

    private fun setupCatalog() {
        rvCatalogItems.layoutManager = LinearLayoutManager(context)
        catalogAdapter = BarcodeItemAdapter(
            items = emptyList(),
            isDarkMode = isDarkMode,
            onItemClicked = { item ->
                onBarcodeItemSelected?.invoke(item)
            }
        )
        rvCatalogItems.adapter = catalogAdapter

        etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterCatalog(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etCatalogSearch.text.clear()
        }

        btnCloseCatalog.setOnClickListener {
            toggleCatalog(false)
        }

        barcodeItemRepository.addOnCatalogChangedListener {
            rootView.post {
                filterCatalog(etCatalogSearch.text.toString())
            }
        }
    }

    fun filterCatalog(query: String = "") {
        val results = barcodeItemRepository.searchItems(query)
        catalogAdapter.updateData(results)
        tvCatalogItemCount.text = "${results.size} item"
        tvCatalogEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        rvCatalogItems.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun toggleCatalog(forceOpen: Boolean? = null) {
        isCatalogOpen = forceOpen ?: !isCatalogOpen

        if (isCatalogOpen) {
            if (isScannerOpen) {
                toggleScanner(false)
            }
            catalogContainer.visibility = View.VISIBLE
            keypadContainer.visibility = View.GONE
            filterCatalog(etCatalogSearch.text.toString())
        } else {
            catalogContainer.visibility = View.GONE
            keypadContainer.visibility = View.VISIBLE
        }

        updateToolbarIconColors()
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
        toolbarContainer.setBackgroundColor(toolbarColor)
        catalogContainer.setBackgroundColor(surfaceColor)
        if (::catalogAdapter.isInitialized) {
            catalogAdapter.updateTheme(isDark)
        }

        updateToolbarIconColors()
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
        btnCatalog.setColorFilter(if (isCatalogOpen) accentColor else subTextColor)
        btnPaste.setColorFilter(primaryTextColor)
        btnCursorLeft.setColorFilter(subTextColor)
        btnCursorRight.setColorFilter(subTextColor)
        btnSettings.setColorFilter(subTextColor)
    }

    fun toggleScanner(forceOpen: Boolean? = null) {
        isScannerOpen = forceOpen ?: !isScannerOpen
        if (isScannerOpen && isCatalogOpen) {
            toggleCatalog(false)
        }
        scannerContainer.visibility = if (isScannerOpen) View.VISIBLE else View.GONE
        btnFlash.visibility = if (isScannerOpen) View.VISIBLE else View.GONE

        updateToolbarIconColors()
        onScannerToggled?.invoke(isScannerOpen)
    }

    fun updateFlashIcon(isTorchOn: Boolean) {
        val color = if (isTorchOn) Color.parseColor("#FFD600") else Color.WHITE
        btnFlash.setColorFilter(color)
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
        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()
        row4.removeAllViews()

        when (currentMode) {
            KeyboardMode.ALPHABET -> renderAlphabetLayout()
            KeyboardMode.SYMBOLS -> renderSymbolsLayout()
            KeyboardMode.MORE_SYMBOLS -> renderMoreSymbolsLayout()
        }
    }

    private fun renderAlphabetLayout() {
        val isUpper = (shiftState != ShiftState.OFF)

        // Row 1: q w e r t y u i o p
        val r1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        for (k in r1Keys) {
            val label = if (isUpper) k.toUpperCase() else k
            row1.addView(createKeyButton(label, 1f) {
                handleCharInput(label)
            })
        }

        // Row 2: a s d f g h j k l
        val r2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        row2.addView(createSpacer(0.5f))
        for (k in r2Keys) {
            val label = if (isUpper) k.toUpperCase() else k
            row2.addView(createKeyButton(label, 1f) {
                handleCharInput(label)
            })
        }
        row2.addView(createSpacer(0.5f))

        // Row 3: [Shift] z x c v b n m [Backspace]
        val shiftBtn = createShiftButton(1.5f)
        row3.addView(shiftBtn)

        val r3Keys = listOf("z", "x", "c", "v", "b", "n", "m")
        for (k in r3Keys) {
            val label = if (isUpper) k.toUpperCase() else k
            row3.addView(createKeyButton(label, 1f) {
                handleCharInput(label)
            })
        }

        row3.addView(createBackspaceButton(1.5f))

        // Row 4: [?123] [,] [   Space   ] [.] [Enter]
        val modeBtn = createFunctionButton("?123", 1.5f) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row4.addView(modeBtn)

        row4.addView(createKeyButton(",", 1.0f) {
            handleCharInput(",")
        })

        val spaceBtn = createFunctionButton("Spasi", 4.3f) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 1.0f) {
            handleCharInput(".")
        })

        row4.addView(createEnterButton(1.6f))
    }

    private fun renderSymbolsLayout() {
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        for (k in r1) {
            row1.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        val r2 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")
        for (k in r2) {
            row2.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        val moreSymBtn = createFunctionButton("=\\<", 1.4f) {
            currentMode = KeyboardMode.MORE_SYMBOLS
            renderKeyboard()
        }
        row3.addView(moreSymBtn)

        val r3 = listOf("*", "\"", "'", ":", ";", "!", "?")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        row3.addView(createBackspaceButton(1.5f))

        val abcBtn = createFunctionButton("ABC", 1.5f) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 1.0f) { handleCharInput(",") })

        val spaceBtn = createFunctionButton("Spasi", 4.3f) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 1.0f) { handleCharInput(".") })

        row4.addView(createEnterButton(1.6f))
    }

    private fun renderMoreSymbolsLayout() {
        val r1 = listOf("~", "`", "|", "^", "_", "=", "{", "}", "[", "]")
        for (k in r1) {
            row1.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        val r2 = listOf("<", ">", "€", "£", "¥", "¢", "\\", "§", "°", "¿")
        for (k in r2) {
            row2.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        val symBtn = createFunctionButton("?123", 1.4f) {
            currentMode = KeyboardMode.SYMBOLS
            renderKeyboard()
        }
        row3.addView(symBtn)

        val r3 = listOf("«", "»", "¡", "©", "®", "™", "±")
        for (k in r3) {
            row3.addView(createKeyButton(k, 1f) { handleCharInput(k) })
        }

        row3.addView(createBackspaceButton(1.5f))

        val abcBtn = createFunctionButton("ABC", 1.5f) {
            currentMode = KeyboardMode.ALPHABET
            renderKeyboard()
        }
        row4.addView(abcBtn)

        row4.addView(createKeyButton(",", 1.0f) { handleCharInput(",") })

        val spaceBtn = createFunctionButton("Spasi", 4.3f) {
            onSpaceClicked?.invoke()
        }
        row4.addView(spaceBtn)

        row4.addView(createKeyButton(".", 1.0f) { handleCharInput(".") })

        row4.addView(createEnterButton(1.6f))
    }

    private fun handleCharInput(char: String) {
        onKeyTyped?.invoke(char)
        if (shiftState == ShiftState.ONCE) {
            shiftState = ShiftState.OFF
            renderKeyboard()
        }
    }

    private fun createKeyButton(
        text: String,
        weight: Float,
        onClick: () -> Unit
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

        val textColor = if (isDarkMode) {
            ContextCompat.getColor(context, R.color.heliboard_text)
        } else {
            ContextCompat.getColor(context, R.color.heliboard_text_light)
        }

        val btn = Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 17f
            setTextColor(textColor)
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
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

        val btn = Button(context).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(textColor)
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
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

        val btn = ImageButton(context).apply {
            background = createKeyDrawable(normalColor, pressedColor, 6f)
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            when (shiftState) {
                ShiftState.OFF -> {
                    setImageResource(R.drawable.ic_shift)
                    setColorFilter(subTextColor)
                }
                ShiftState.ONCE -> {
                    setImageResource(R.drawable.ic_shift)
                    setColorFilter(accentColor)
                }
                ShiftState.CAPS_LOCK -> {
                    setImageResource(R.drawable.ic_shift_caps)
                    setColorFilter(accentColor)
                }
            }

            setOnClickListener {
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
                renderKeyboard()
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

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        onBackspaceClicked?.invoke()
                        startBackspaceRepeat()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                onBackspaceClicked?.invoke()
                repeatHandler.postDelayed(this, 60)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 400)
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
            setOnClickListener {
                onEnterClicked?.invoke()
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
