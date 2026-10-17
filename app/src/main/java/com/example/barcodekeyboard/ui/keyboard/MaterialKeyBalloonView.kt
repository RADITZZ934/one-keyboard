package com.example.barcodekeyboard.ui.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * Material You (Material 3) speech bubble / balloon key pop-up,
 * designed with smooth continuous bezier stem connecting to the pressed key
 * and an elevated rounded head displaying high-contrast bold preview typography.
 */
class MaterialKeyBalloonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var previewText: String = ""
    private var isDarkMode: Boolean = true

    private val balloonPath = Path()
    private val headRectF = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val shadowPadding = dpToPx(14f)

    init {
        // Enable software layer to render smooth Gaussian drop shadow via Paint.setShadowLayer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        visibility = GONE
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }

    private fun spToPx(sp: Float): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }

    /**
     * Shows the Material You speech balloon for [keyView].
     */
    fun show(
        keyView: View,
        text: String,
        isDark: Boolean,
        fontScale: Float = 1.0f,
        rootOverlayView: View
    ) {
        animate().cancel()
        this.previewText = text
        this.isDarkMode = isDark

        val keyLoc = IntArray(2)
        val rootLoc = IntArray(2)
        keyView.getLocationInWindow(keyLoc)
        rootOverlayView.getLocationInWindow(rootLoc)

        val keyX = (keyLoc[0] - rootLoc[0]).toFloat()
        val keyY = (keyLoc[1] - rootLoc[1]).toFloat()
        val keyW = keyView.width.toFloat()
        val keyH = keyView.height.toFloat()

        if (keyW <= 0f || keyH <= 0f) return

        // 1. Calculate balloon head dimensions
        val headW = maxOf(keyW * 1.35f, dpToPx(56f))
        val headH = dpToPx(50f)

        // Center head horizontally above key
        var headX = keyX + (keyW - headW) / 2f
        val margin = dpToPx(4f)
        val rootWidth = if (rootOverlayView.width > 0) rootOverlayView.width.toFloat() else context.resources.displayMetrics.widthPixels.toFloat()
        headX = headX.coerceIn(margin, maxOf(margin, rootWidth - headW - margin))

        // Position head above key
        val headY = maxOf(margin, keyY - headH + dpToPx(3f))

        // Calculate view bounding box (including shadow padding)
        val minX = minOf(keyX, headX) - shadowPadding
        val maxX = maxOf(keyX + keyW, headX + headW) + shadowPadding
        val minY = headY - shadowPadding
        val maxY = keyY + keyH + shadowPadding

        val viewWidth = (maxX - minX).toInt()
        val viewHeight = (maxY - minY).toInt()

        translationX = minX
        translationY = minY

        val lp = layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(viewWidth, viewHeight)
        lp.width = viewWidth
        lp.height = viewHeight
        layoutParams = lp

        // 2. Build local coordinates path
        val kL = keyX - minX
        val kR = keyX + keyW - minX
        val kT = keyY - minY
        val kB = keyY + keyH - minY

        val bL = headX - minX
        val bR = headX + headW - minX
        val bT = headY - minY
        val bB = headY + headH - minY

        val headRadius = dpToPx(16f)
        val keyRadius = dpToPx(13f)
        val transitionH = (kT - bB).coerceAtLeast(dpToPx(4f))

        balloonPath.reset()

        // Head top-left corner start
        balloonPath.moveTo(bL + headRadius, bT)
        // Head top edge
        balloonPath.lineTo(bR - headRadius, bT)
        // Head top-right corner
        balloonPath.quadTo(bR, bT, bR, bT + headRadius)
        // Head right edge down
        balloonPath.lineTo(bR, bB - dpToPx(2f))

        // Right stem transition from head down to keycap
        if (bR > kR + dpToPx(2f)) {
            balloonPath.cubicTo(
                bR, bB + transitionH * 0.45f,
                kR + dpToPx(2f), kT - transitionH * 0.35f,
                kR, kT + dpToPx(2f)
            )
        } else {
            balloonPath.lineTo(kR, kT)
        }

        // Down key right edge to bottom-right corner
        balloonPath.lineTo(kR, kB - keyRadius)
        balloonPath.quadTo(kR, kB, kR - keyRadius, kB)

        // Key bottom edge
        balloonPath.lineTo(kL + keyRadius, kB)
        balloonPath.quadTo(kL, kB, kL, kB - keyRadius)

        // Up key left edge to top of key
        balloonPath.lineTo(kL, kT + dpToPx(2f))

        // Left stem transition from keycap up to head
        if (bL < kL - dpToPx(2f)) {
            balloonPath.cubicTo(
                kL - dpToPx(2f), kT - transitionH * 0.35f,
                bL, bB + transitionH * 0.45f,
                bL, bB - dpToPx(2f)
            )
        } else {
            balloonPath.lineTo(bL, bB - dpToPx(2f))
        }

        // Up head left edge
        balloonPath.lineTo(bL, bT + headRadius)
        // Head top-left corner
        balloonPath.quadTo(bL, bT, bL + headRadius, bT)
        balloonPath.close()

        headRectF.set(bL, bT, bR, bB)

        // Setup colors & typography
        if (isDarkMode) {
            val surfaceColor = Color.parseColor("#363A42") // Material 3 Surface Container High Dark
            fillPaint.color = surfaceColor
            shadowPaint.color = surfaceColor
            shadowPaint.setShadowLayer(dpToPx(10f), 0f, dpToPx(4f), Color.argb(140, 0, 0, 0))
            textPaint.color = Color.parseColor("#F1F3F5")
        } else {
            val surfaceColor = Color.parseColor("#FFFFFF") // Material 3 Surface High Light
            fillPaint.color = surfaceColor
            shadowPaint.color = surfaceColor
            shadowPaint.setShadowLayer(dpToPx(8f), 0f, dpToPx(3f), Color.argb(70, 0, 0, 0))
            textPaint.color = Color.parseColor("#1A1C1E")
        }

        textPaint.textSize = spToPx(26f * fontScale.coerceIn(0.85f, 1.25f))

        // Pivot at key center for authentic spring expansion from touch contact
        pivotX = (keyX + keyW / 2f) - minX
        pivotY = (keyY + keyH / 2f) - minY

        alpha = 1.0f
        scaleX = 0.55f
        scaleY = 0.55f
        visibility = VISIBLE

        animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(70)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()

        invalidate()
    }

    /**
     * Dismisses the balloon with a quick shrink & fade animation.
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
        if (balloonPath.isEmpty) return

        // 1. Draw ambient drop shadow
        canvas.drawPath(balloonPath, shadowPaint)

        // 2. Draw surface fill
        canvas.drawPath(balloonPath, fillPaint)

        // 3. Draw bold preview character centered in balloon head
        val cx = headRectF.centerX()
        val cy = headRectF.centerY()
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(previewText, cx, baseline, textPaint)
    }
}
