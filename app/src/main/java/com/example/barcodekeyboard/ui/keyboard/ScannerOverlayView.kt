package com.example.barcodekeyboard.ui.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Modern, futuristic HUD Scanner Overlay.
 * Features:
 * - Holographic laser curtain with dynamic trailing light flare
 * - Rounded glowing corner brackets with breathing idle animation
 * - Center aiming crosshairs
 * - State-of-the-art Success animation (emerald flash, ripple burst, elastic snap, checkmark badge)
 * - Catchy Failure animation (crimson alert flash, robotic micro-shake, error cross badge)
 * - Interactive tap-to-focus / scan attempt support
 */
class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ScanVisualState {
        SCANNING,
        SUCCESS,
        FAILURE
    }

    var visualState: ScanVisualState = ScanVisualState.SCANNING
        private set

    var onViewfinderTapped: (() -> Unit)? = null

    // Visual Palette
    private val colorBlue = Color.parseColor("#2979FF")
    private val colorCyan = Color.parseColor("#00E5FF")
    private val colorSuccess = Color.parseColor("#00E676")
    private val colorFailure = Color.parseColor("#FF1744")

    // Paints
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val laserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 16f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val curtainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val badgeIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Geometry
    private val framingRect = RectF()
    private val checkPath = Path()
    private val crossPath = Path()

    // Animation values
    private var laserPosition = 0f
    private var laserDirectionDown = true
    private var idlePulseAlpha = 1.0f
    private var feedbackProgress = 0f
    private var shakeOffsetX = 0f

    // Animators
    private var laserAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var feedbackAnimator: ValueAnimator? = null

    init {
        startIdleAnimations()
    }

    private fun startIdleAnimations() {
        // 1. Holographic Laser Sweep
        laserAnimator?.cancel()
        laserAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val prev = laserPosition
                laserPosition = animator.animatedValue as Float
                laserDirectionDown = laserPosition >= prev
                if (visualState == ScanVisualState.SCANNING) {
                    invalidate()
                }
            }
            start()
        }

        // 2. Subtle Reticle Pulse
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.7f, 1.0f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                idlePulseAlpha = animator.animatedValue as Float
                if (visualState == ScanVisualState.SCANNING) {
                    invalidate()
                }
            }
            start()
        }
    }

    /**
     * Triggers dynamic Success Animation:
     * - Emerald flash wash
     * - Corner bracket inward elastic snap
     * - Expanding radar ripple
     * - Spring-loaded checkmark badge
     */
    fun triggerSuccessAnimation(onComplete: (() -> Unit)? = null) {
        visualState = ScanVisualState.SUCCESS
        shakeOffsetX = 0f

        feedbackAnimator?.cancel()
        feedbackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { anim ->
                feedbackProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        postDelayed({
            visualState = ScanVisualState.SCANNING
            feedbackProgress = 0f
            invalidate()
            onComplete?.invoke()
        }, 850)
    }

    /**
     * Triggers dynamic Failure Animation:
     * - Crimson alert flash
     * - Robotic micro-shake of brackets
     * - Alert error cross badge
     */
    @Suppress("UNUSED_PARAMETER")
    fun triggerFailureAnimation(reason: String = "", onComplete: (() -> Unit)? = null) {
        visualState = ScanVisualState.FAILURE

        feedbackAnimator?.cancel()
        feedbackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            addUpdateListener { anim ->
                feedbackProgress = anim.animatedValue as Float
                // Decaying sinusoidal robotic shake
                val decay = 1f - feedbackProgress
                shakeOffsetX = (sin(feedbackProgress * 6.0 * PI) * decay * 22f).toFloat()
                invalidate()
            }
            start()
        }

        postDelayed({
            visualState = ScanVisualState.SCANNING
            shakeOffsetX = 0f
            feedbackProgress = 0f
            invalidate()
            onComplete?.invoke()
        }, 750)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boxWidth = (w * 0.78f).coerceAtMost(h * 1.65f)
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

        // 1. Draw Dimmed Mask outside the Viewfinder
        canvas.drawRect(0f, 0f, width, framingRect.top, maskPaint)
        canvas.drawRect(0f, framingRect.top, framingRect.left, framingRect.bottom, maskPaint)
        canvas.drawRect(framingRect.right, framingRect.top, width, framingRect.bottom, maskPaint)
        canvas.drawRect(0f, framingRect.bottom, width, height, maskPaint)

        // Calculate dynamic framing with shake offset & success snap
        val snapScale = if (visualState == ScanVisualState.SUCCESS) {
            1f - 0.05f * sin(feedbackProgress * PI.toFloat()).coerceAtLeast(0f)
        } else {
            1f
        }

        val cx = framingRect.centerX() + shakeOffsetX
        val cy = framingRect.centerY()
        val halfW = (framingRect.width() * snapScale) / 2f
        val halfH = (framingRect.height() * snapScale) / 2f

        val activeLeft = cx - halfW
        val activeTop = cy - halfH
        val activeRight = cx + halfW
        val activeBottom = cy + halfH

        // 2. Draw State-Specific Backing Flash / Glow
        when (visualState) {
            ScanVisualState.SUCCESS -> {
                val alpha = ((1f - feedbackProgress) * 0.35f * 255).toInt().coerceIn(0, 255)
                flashPaint.color = Color.argb(alpha, 0, 230, 118)
                canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, 16f, 16f, flashPaint)

                // Expanding Ripple Wave
                val maxRipple = halfW * 1.1f
                val currentRadius = maxRipple * feedbackProgress.coerceAtMost(1f)
                val rippleAlpha = ((1f - feedbackProgress) * 220).toInt().coerceIn(0, 255)
                ripplePaint.color = Color.argb(rippleAlpha, 0, 230, 118)
                canvas.drawCircle(cx, cy, currentRadius, ripplePaint)
            }
            ScanVisualState.FAILURE -> {
                val alpha = ((1f - feedbackProgress) * 0.40f * 255).toInt().coerceIn(0, 255)
                flashPaint.color = Color.argb(alpha, 255, 23, 68)
                canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, 16f, 16f, flashPaint)
            }
            ScanVisualState.SCANNING -> {
                // Subtle scan window border
                reticlePaint.color = Color.argb((40 * idlePulseAlpha).toInt(), 41, 121, 255)
                canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, 12f, 12f, reticlePaint)
            }
        }

        // 3. Draw Laser Line & Holographic Curtain (Only in SCANNING state)
        if (visualState == ScanVisualState.SCANNING) {
            val laserY = activeTop + ((activeBottom - activeTop) * laserPosition)
            val curtainHeight = 40f

            // Holographic curtain gradient behind laser
            val curtainTop = if (laserDirectionDown) (laserY - curtainHeight).coerceAtLeast(activeTop) else laserY
            val curtainBottom = if (laserDirectionDown) laserY else (laserY + curtainHeight).coerceAtMost(activeBottom)

            val cColorStart = if (laserDirectionDown) 0x002979FF else 0x4500E5FF
            val cColorEnd = if (laserDirectionDown) 0x4500E5FF else 0x002979FF

            curtainPaint.shader = LinearGradient(
                cx, curtainTop, cx, curtainBottom,
                cColorStart, cColorEnd,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(activeLeft + 6f, curtainTop, activeRight - 6f, curtainBottom, curtainPaint)

            // Laser Glowing core and main beam
            laserGlowPaint.color = Color.argb(120, 0, 229, 255)
            canvas.drawLine(activeLeft + 10f, laserY, activeRight - 10f, laserY, laserGlowPaint)

            laserPaint.color = Color.parseColor("#00E5FF")
            canvas.drawLine(activeLeft + 8f, laserY, activeRight - 8f, laserY, laserPaint)
        }

        // 4. Draw Center Aiming Crosshairs (in SCANNING state)
        if (visualState == ScanVisualState.SCANNING) {
            reticlePaint.color = Color.argb((120 * idlePulseAlpha).toInt(), 0, 229, 255)
            val crossSize = 14f
            // Left tick
            canvas.drawLine(cx - crossSize - 6f, cy, cx - 6f, cy, reticlePaint)
            // Right tick
            canvas.drawLine(cx + 6f, cy, cx + crossSize + 6f, cy, reticlePaint)
            // Top tick
            canvas.drawLine(cx, cy - crossSize - 6f, cx, cy - 6f, reticlePaint)
            // Bottom tick
            canvas.drawLine(cx, cy + 6f, cx, cy + crossSize + 6f, reticlePaint)
            // Center Dot
            canvas.drawCircle(cx, cy, 2.5f, reticlePaint)
        }

        // 5. Draw 4 Rounded Corner Brackets
        val cornerColor = when (visualState) {
            ScanVisualState.SUCCESS -> colorSuccess
            ScanVisualState.FAILURE -> colorFailure
            ScanVisualState.SCANNING -> {
                val alpha = (255 * idlePulseAlpha).toInt().coerceIn(120, 255)
                Color.argb(alpha, 41, 121, 255)
            }
        }
        cornerPaint.color = cornerColor
        drawCornerBrackets(canvas, activeLeft, activeTop, activeRight, activeBottom)

        // 6. Draw Feedback Center Badges (Checkmark or Cross)
        if (visualState == ScanVisualState.SUCCESS) {
            drawSuccessBadge(canvas, cx, cy)
        } else if (visualState == ScanVisualState.FAILURE) {
            drawFailureBadge(canvas, cx, cy)
        }
    }

    private fun drawCornerBrackets(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val cornerLen = 32f
        val radius = 10f

        // Top-Left
        canvas.drawLine(left + radius, top, left + cornerLen, top, cornerPaint)
        canvas.drawArc(RectF(left, top, left + radius * 2, top + radius * 2), 180f, 90f, false, cornerPaint)
        canvas.drawLine(left, top + radius, left, top + cornerLen, cornerPaint)

        // Top-Right
        canvas.drawLine(right - cornerLen, top, right - radius, top, cornerPaint)
        canvas.drawArc(RectF(right - radius * 2, top, right, top + radius * 2), 270f, 90f, false, cornerPaint)
        canvas.drawLine(right, top + radius, right, top + cornerLen, cornerPaint)

        // Bottom-Left
        canvas.drawLine(left + radius, bottom, left + cornerLen, bottom, cornerPaint)
        canvas.drawArc(RectF(left, bottom - radius * 2, left + radius * 2, bottom), 90f, 90f, false, cornerPaint)
        canvas.drawLine(left, bottom - cornerLen, left, bottom - radius, cornerPaint)

        // Bottom-Right
        canvas.drawLine(right - cornerLen, bottom, right - radius, bottom, cornerPaint)
        canvas.drawArc(RectF(right - radius * 2, bottom - radius * 2, right, bottom), 0f, 90f, false, cornerPaint)
        canvas.drawLine(right, bottom - cornerLen, right, bottom - radius, cornerPaint)
    }

    private fun drawSuccessBadge(canvas: Canvas, cx: Float, cy: Float) {
        val scale = feedbackProgress.coerceIn(0f, 1.2f)
        val badgeRadius = 28f * scale

        // Glowing circle background
        badgeFillPaint.color = Color.parseColor("#00E676")
        canvas.drawCircle(cx, cy, badgeRadius, badgeFillPaint)

        // Glowing border ring
        reticlePaint.color = Color.WHITE
        reticlePaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, badgeRadius, reticlePaint)

        // White Checkmark
        if (scale > 0.4f) {
            checkPath.reset()
            val iconScale = scale.coerceAtMost(1f)
            val p1x = cx - 12f * iconScale
            val p1y = cy
            val p2x = cx - 3f * iconScale
            val p2y = cy + 9f * iconScale
            val p3x = cx + 13f * iconScale
            val p3y = cy - 8f * iconScale

            checkPath.moveTo(p1x, p1y)
            checkPath.lineTo(p2x, p2y)
            checkPath.lineTo(p3x, p3y)

            badgeIconPaint.strokeWidth = 5f * iconScale
            canvas.drawPath(checkPath, badgeIconPaint)
        }
    }

    private fun drawFailureBadge(canvas: Canvas, cx: Float, cy: Float) {
        val scale = (feedbackProgress * 1.3f).coerceIn(0f, 1f)
        val badgeRadius = 28f * scale

        // Red alert circle background
        badgeFillPaint.color = Color.parseColor("#FF1744")
        canvas.drawCircle(cx, cy, badgeRadius, badgeFillPaint)

        // Border ring
        reticlePaint.color = Color.WHITE
        reticlePaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, badgeRadius, reticlePaint)

        // White Cross ✕
        if (scale > 0.3f) {
            crossPath.reset()
            val iconScale = scale.coerceAtMost(1f)
            val offset = 10f * iconScale

            crossPath.moveTo(cx - offset, cy - offset)
            crossPath.lineTo(cx + offset, cy + offset)
            crossPath.moveTo(cx + offset, cy - offset)
            crossPath.lineTo(cx - offset, cy + offset)

            badgeIconPaint.strokeWidth = 5f * iconScale
            canvas.drawPath(crossPath, badgeIconPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            if (framingRect.contains(event.x, event.y)) {
                onViewfinderTapped?.invoke()
                return true
            }
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (laserAnimator?.isRunning != true) {
            startIdleAnimations()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        laserAnimator?.cancel()
        pulseAnimator?.cancel()
        feedbackAnimator?.cancel()
    }
}
