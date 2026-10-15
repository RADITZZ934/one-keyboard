package com.example.barcodekeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * Material You (Material 3) pill-shaped sub-menu popup for long-press key variants.
 * Displays horizontal accented characters / symbol options with interactive
 * slide-to-select indicator and subtle haptic feedback.
 */
class MoreKeysPillPopupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var variants: List<String> = emptyList()
    var selectedIndex: Int = 0
        private set

    private var isDarkMode: Boolean = true
    private var fontScale: Float = 1.0f

    private val itemWidth = dpToPx(38f)
    private val itemHeight = dpToPx(48f)
    private val pillPadding = dpToPx(4f)
    private val shadowPadding = dpToPx(14f)

    private val pillRectF = RectF()
    private val indicatorRectF = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val selectedTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
    }

    private fun dpToPx(dp: Float): Float = dp * context.resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * context.resources.displayMetrics.scaledDensity

    /**
     * Shows the pill popup for [keyView] with [variantsList].
     */
    fun show(
        keyView: View,
        variantsList: List<String>,
        isDark: Boolean,
        scale: Float = 1.0f,
        rootOverlayView: View
    ) {
        animate().cancel()
        this.variants = variantsList
        this.isDarkMode = isDark
        this.fontScale = scale
        // Default select first alternative if available, or base key
        this.selectedIndex = if (variantsList.size > 1) 1 else 0

        val contentWidth = variantsList.size * itemWidth + pillPadding * 2
        val contentHeight = itemHeight + pillPadding * 2

        val viewWidth = (contentWidth + shadowPadding * 2).toInt()
        val viewHeight = (contentHeight + shadowPadding * 2).toInt()

        val keyLoc = IntArray(2)
        val rootLoc = IntArray(2)
        keyView.getLocationInWindow(keyLoc)
        rootOverlayView.getLocationInWindow(rootLoc)

        val keyX = (keyLoc[0] - rootLoc[0]).toFloat()
        val keyY = (keyLoc[1] - rootLoc[1]).toFloat()
        val keyW = keyView.width.toFloat()
        val keyH = keyView.height.toFloat()

        val keyCenterX = keyX + keyW / 2f
        var targetX = keyCenterX - (contentWidth / 2f) - shadowPadding
        val rootWidth = if (rootOverlayView.width > 0) rootOverlayView.width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
        val margin = dpToPx(4f)
        targetX = targetX.coerceIn(margin - shadowPadding, maxOf(margin - shadowPadding, rootWidth - contentWidth - margin - shadowPadding))

        val targetY = maxOf(dpToPx(4f) - shadowPadding, keyY - contentHeight - dpToPx(12f) - shadowPadding)

        val lp = layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(viewWidth, viewHeight)
        lp.width = viewWidth
        lp.height = viewHeight
        layoutParams = lp

        translationX = targetX
        translationY = targetY

        pillRectF.set(
            shadowPadding,
            shadowPadding,
            shadowPadding + contentWidth,
            shadowPadding + contentHeight
        )

        // Setup colors
        if (isDarkMode) {
            val bgColor = Color.parseColor("#323742") // Material 3 Surface Container High
            bgPaint.color = bgColor
            shadowPaint.color = bgColor
            shadowPaint.setShadowLayer(dpToPx(9f), 0f, dpToPx(4f), Color.argb(140, 0, 0, 0))
            indicatorPaint.color = Color.parseColor("#2979FF") // Material 3 Accent Blue
            textPaint.color = Color.parseColor("#94A3B8")
            selectedTextPaint.color = Color.WHITE
        } else {
            val bgColor = Color.parseColor("#E6EBF4") // Material 3 Light Surface Container High
            bgPaint.color = bgColor
            shadowPaint.color = bgColor
            shadowPaint.setShadowLayer(dpToPx(8f), 0f, dpToPx(3f), Color.argb(65, 0, 0, 0))
            indicatorPaint.color = Color.parseColor("#2563EB") // Vibrant Blue
            textPaint.color = Color.parseColor("#64748B")
            selectedTextPaint.color = Color.WHITE
        }

        val baseSp = 18f * fontScale.coerceIn(0.85f, 1.25f)
        textPaint.textSize = spToPx(baseSp)
        selectedTextPaint.textSize = spToPx(baseSp + 2.5f)

        pivotX = (keyCenterX - targetX).coerceIn(0f, viewWidth.toFloat())
        pivotY = (keyY + keyH / 2f - targetY).coerceIn(0f, viewHeight.toFloat())

        alpha = 0f
        scaleX = 0.65f
        scaleY = 0.65f
        visibility = VISIBLE

        animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(90)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()

        invalidate()
    }

    /**
     * Updates selected index based on the user's touch X coordinate in root keyboard space.
     */
    fun updateTouch(rootTouchX: Float) {
        if (variants.isEmpty()) return
        val pillContentLeft = translationX + shadowPadding + pillPadding
        val relX = rootTouchX - pillContentLeft
        val newIndex = (relX / itemWidth).toInt().coerceIn(0, variants.size - 1)

        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            invalidate()
        }
    }

    /**
     * Returns the currently selected character from the sub-menu.
     */
    fun getSelectedChar(): String? = variants.getOrNull(selectedIndex)

    /**
     * Dismisses the pill popup with animation.
     */
    fun dismiss(delayMs: Long = 0L) {
        animate().cancel()
        if (delayMs <= 0L) {
            visibility = GONE
            return
        }

        postDelayed({
            animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .alpha(0f)
                .setDuration(60)
                .withEndAction {
                    visibility = GONE
                }
                .start()
        }, delayMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (variants.isEmpty()) return

        val pillRadius = pillRectF.height() / 2f

        // 1. Draw drop shadow
        canvas.drawRoundRect(pillRectF, pillRadius, pillRadius, shadowPaint)

        // 2. Draw pill container background
        canvas.drawRoundRect(pillRectF, pillRadius, pillRadius, bgPaint)

        // 3. Draw selected item indicator
        if (selectedIndex in variants.indices) {
            val itemLeft = pillRectF.left + pillPadding + selectedIndex * itemWidth
            val itemTop = pillRectF.top + pillPadding
            val itemRight = itemLeft + itemWidth
            val itemBottom = itemTop + itemHeight

            val inset = dpToPx(2f)
            indicatorRectF.set(
                itemLeft + inset,
                itemTop + inset,
                itemRight - inset,
                itemBottom - inset
            )
            val indicatorRadius = dpToPx(12f)
            canvas.drawRoundRect(indicatorRectF, indicatorRadius, indicatorRadius, indicatorPaint)
        }

        // 4. Draw character options
        val textCenterY = pillRectF.top + pillPadding + (itemHeight / 2f)
        for (i in variants.indices) {
            val char = variants[i]
            val itemCenterX = pillRectF.left + pillPadding + i * itemWidth + (itemWidth / 2f)

            val paint = if (i == selectedIndex) selectedTextPaint else textPaint
            val baseline = textCenterY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(char, itemCenterX, baseline, paint)
        }
    }
}
