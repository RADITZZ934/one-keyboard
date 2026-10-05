package com.example.barcodekeyboard.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Custom overlay view displaying a semi-transparent viewfinder reticle,
 * four corner brackets, and an animated laser beam.
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44000000")
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4400E676")
        strokeWidth = 10f
        style = Paint.Style.STROKE
    }

    private val framingRect = RectF()
    private var laserPosition = 0f
    private var laserAnimator: ValueAnimator? = null

    init {
        startLaserAnimation()
    }

    private fun startLaserAnimation() {
        laserAnimator?.cancel()
        laserAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                laserPosition = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boxWidth = (w * 0.75f).coerceAtMost(h * 1.6f)
        val boxHeight = (h * 0.75f).coerceAtMost(boxWidth * 0.65f)

        val left = (w - boxWidth) / 2f
        val top = (h - boxHeight) / 2f
        val right = left + boxWidth
        val bottom = top + boxHeight

        framingRect.set(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()

        if (framingRect.isEmpty) return

        // 1. Draw 4 mask rectangles around framing window
        canvas.drawRect(0f, 0f, width, framingRect.top, maskPaint)
        canvas.drawRect(0f, framingRect.top, framingRect.left, framingRect.bottom, maskPaint)
        canvas.drawRect(framingRect.right, framingRect.top, width, framingRect.bottom, maskPaint)
        canvas.drawRect(0f, framingRect.bottom, width, height, maskPaint)

        // 2. Draw Corner Brackets
        val cornerLength = 32f
        val rect = framingRect

        // Top Left
        canvas.drawLine(rect.left, rect.top, rect.left + cornerLength, rect.top, cornerPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + cornerLength, cornerPaint)

        // Top Right
        canvas.drawLine(rect.right, rect.top, rect.right - cornerLength, rect.top, cornerPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + cornerLength, cornerPaint)

        // Bottom Left
        canvas.drawLine(rect.left, rect.bottom, rect.left + cornerLength, rect.bottom, cornerPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - cornerLength, cornerPaint)

        // Bottom Right
        canvas.drawLine(rect.right, rect.bottom, rect.right - cornerLength, rect.bottom, cornerPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - cornerLength, cornerPaint)

        // 3. Draw Laser Line
        val laserY = rect.top + (rect.height() * laserPosition)
        canvas.drawLine(rect.left + 8f, laserY, rect.right - 8f, laserY, laserGlowPaint)
        canvas.drawLine(rect.left + 8f, laserY, rect.right - 8f, laserY, laserPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (laserAnimator?.isRunning != true) {
            startLaserAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        laserAnimator?.cancel()
    }
}
