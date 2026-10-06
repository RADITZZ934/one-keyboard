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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Modern, futuristic HUD Scanner Overlay with interactive Tap-to-Focus,
 * pinch-to-zoom gestures, live barcode target lock tracking,
 * and holographic viewfinder animations.
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

    // Callbacks
    var onViewfinderTapped: (() -> Unit)? = null
    var onFocusRequested: ((Float, Float) -> Unit)? = null
    var onDoubleTapZoom: (() -> Unit)? = null
    var onPinchZoom: ((Float) -> Unit)? = null

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

    // Tap-to-Focus HUD Reticle Paints
    private val focusRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val focusCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
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

    // Tap-to-Focus Reticle State
    private var isFocusRingVisible = false
    private var focusPointX = 0f
    private var focusPointY = 0f
    private var focusRingScale = 1.0f
    private var focusRingAlpha = 1.0f
    private var focusSuccessState: Boolean? = null // null: focusing, true: locked, false: failed
    private var isBarcodeTargetLocked = false

    // Animators
    private var laserAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var feedbackAnimator: ValueAnimator? = null
    private var focusRingAnimator: ValueAnimator? = null
    private var focusFadeAnimator: ValueAnimator? = null

    // Gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            triggerFocusAt(e.x, e.y)
            onViewfinderTapped?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTapZoom?.invoke()
            triggerFocusAt(e.x, e.y)
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onPinchZoom?.invoke(detector.scaleFactor)
            return true
        }
    })

    init {
        startIdleAnimations()
    }

    private fun startIdleAnimations() {
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
     * Interactively triggers the visual focus ring at coordinates (x, y)
     * and notifies listeners to execute camera AF metering.
     */
    fun triggerFocusAt(x: Float, y: Float) {
        focusPointX = x
        focusPointY = y
        isFocusRingVisible = true
        focusSuccessState = null
        focusRingAlpha = 1.0f

        focusFadeAnimator?.cancel()
        focusRingAnimator?.cancel()
        focusRingAnimator = ValueAnimator.ofFloat(1.5f, 1.0f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.3f)
            addUpdateListener { anim ->
                focusRingScale = anim.animatedValue as Float
                invalidate()
            }
            start()
        }

        onFocusRequested?.invoke(x, y)
    }

    /**
     * Called when CameraX reports focus result for the active reticle.
     */
    fun notifyFocusResult(success: Boolean) {
        if (!isFocusRingVisible) return
        focusSuccessState = success
        invalidate()

        // Fade out smoothly after displaying focus confirmation
        focusFadeAnimator?.cancel()
        focusFadeAnimator = ValueAnimator.ofFloat(1.0f, 0f).apply {
            startDelay = 600
            duration = 350
            addUpdateListener { anim ->
                focusRingAlpha = anim.animatedValue as Float
                if (focusRingAlpha <= 0.05f) {
                    isFocusRingVisible = false
                }
                invalidate()
            }
            start()
        }
    }

    /**
     * Updates live target lock state when a barcode is in view.
     */
    fun setBarcodeTargetLocked(locked: Boolean) {
        if (isBarcodeTargetLocked != locked) {
            isBarcodeTargetLocked = locked
            invalidate()
        }
    }

    fun triggerSuccessAnimation(onComplete: (() -> Unit)? = null) {
        visualState = ScanVisualState.SUCCESS
        shakeOffsetX = 0f
        isFocusRingVisible = false

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

    @Suppress("UNUSED_PARAMETER")
    fun triggerFailureAnimation(reason: String = "", onComplete: (() -> Unit)? = null) {
        visualState = ScanVisualState.FAILURE
        isFocusRingVisible = false

        feedbackAnimator?.cancel()
        feedbackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650
            addUpdateListener { anim ->
                feedbackProgress = anim.animatedValue as Float
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
                val targetBorderAlpha = if (isBarcodeTargetLocked) (160 * idlePulseAlpha).toInt() else (40 * idlePulseAlpha).toInt()
                val targetBorderColor = if (isBarcodeTargetLocked) Color.argb(targetBorderAlpha, 0, 230, 118) else Color.argb(targetBorderAlpha, 41, 121, 255)
                reticlePaint.color = targetBorderColor
                canvas.drawRoundRect(activeLeft, activeTop, activeRight, activeBottom, 12f, 12f, reticlePaint)
            }
        }

        // 3. Draw Laser Line & Holographic Curtain (Only in SCANNING state)
        if (visualState == ScanVisualState.SCANNING) {
            val laserY = activeTop + ((activeBottom - activeTop) * laserPosition)
            val curtainHeight = 40f

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

            laserGlowPaint.color = if (isBarcodeTargetLocked) Color.argb(140, 0, 230, 118) else Color.argb(120, 0, 229, 255)
            canvas.drawLine(activeLeft + 10f, laserY, activeRight - 10f, laserY, laserGlowPaint)

            laserPaint.color = if (isBarcodeTargetLocked) Color.parseColor("#00E676") else Color.parseColor("#00E5FF")
            canvas.drawLine(activeLeft + 8f, laserY, activeRight - 8f, laserY, laserPaint)
        }

        // 4. Draw Center Aiming Crosshairs
        if (visualState == ScanVisualState.SCANNING) {
            reticlePaint.color = if (isBarcodeTargetLocked) Color.argb((180 * idlePulseAlpha).toInt(), 0, 230, 118) else Color.argb((120 * idlePulseAlpha).toInt(), 0, 229, 255)
            val crossSize = 14f
            canvas.drawLine(cx - crossSize - 6f, cy, cx - 6f, cy, reticlePaint)
            canvas.drawLine(cx + 6f, cy, cx + crossSize + 6f, cy, reticlePaint)
            canvas.drawLine(cx, cy - crossSize - 6f, cx, cy - 6f, reticlePaint)
            canvas.drawLine(cx, cy + 6f, cx, cy + crossSize + 6f, reticlePaint)
            canvas.drawCircle(cx, cy, 2.5f, reticlePaint)
        }

        // 5. Draw 4 Rounded Corner Brackets
        val cornerColor = when (visualState) {
            ScanVisualState.SUCCESS -> colorSuccess
            ScanVisualState.FAILURE -> colorFailure
            ScanVisualState.SCANNING -> {
                if (isBarcodeTargetLocked) {
                    Color.argb((255 * idlePulseAlpha).toInt().coerceIn(160, 255), 0, 230, 118)
                } else {
                    val alpha = (255 * idlePulseAlpha).toInt().coerceIn(120, 255)
                    Color.argb(alpha, 41, 121, 255)
                }
            }
        }
        cornerPaint.color = cornerColor
        drawCornerBrackets(canvas, activeLeft, activeTop, activeRight, activeBottom)

        // 6. Draw Feedback Center Badges
        if (visualState == ScanVisualState.SUCCESS) {
            drawSuccessBadge(canvas, cx, cy)
        } else if (visualState == ScanVisualState.FAILURE) {
            drawFailureBadge(canvas, cx, cy)
        }

        // 7. Draw Interactive Tap-to-Focus HUD Reticle
        if (isFocusRingVisible && visualState == ScanVisualState.SCANNING) {
            drawFocusReticle(canvas)
        }
    }

    private fun drawFocusReticle(canvas: Canvas) {
        val baseRadius = 26f * focusRingScale
        val ringColor = when (focusSuccessState) {
            true -> Color.argb((255 * focusRingAlpha).toInt(), 0, 230, 118) // Emerald locked
            false -> Color.argb((220 * focusRingAlpha).toInt(), 255, 82, 82) // Red failed
            null -> Color.argb((240 * focusRingAlpha).toInt(), 0, 229, 255) // Cyan focusing
        }

        focusRingPaint.color = ringColor
        focusCornerPaint.color = ringColor

        // Draw central focus circle
        canvas.drawCircle(focusPointX, focusPointY, baseRadius, focusRingPaint)

        // Draw 4 focus corner target brackets
        val bracketOffset = baseRadius + 7f
        val bracketLen = 9f

        // Top-Left bracket
        canvas.drawLine(focusPointX - bracketOffset, focusPointY - bracketOffset, focusPointX - bracketOffset + bracketLen, focusPointY - bracketOffset, focusCornerPaint)
        canvas.drawLine(focusPointX - bracketOffset, focusPointY - bracketOffset, focusPointX - bracketOffset, focusPointY - bracketOffset + bracketLen, focusCornerPaint)

        // Top-Right bracket
        canvas.drawLine(focusPointX + bracketOffset, focusPointY - bracketOffset, focusPointX + bracketOffset - bracketLen, focusPointY - bracketOffset, focusCornerPaint)
        canvas.drawLine(focusPointX + bracketOffset, focusPointY - bracketOffset, focusPointX + bracketOffset, focusPointY - bracketOffset + bracketLen, focusCornerPaint)

        // Bottom-Left bracket
        canvas.drawLine(focusPointX - bracketOffset, focusPointY + bracketOffset, focusPointX - bracketOffset + bracketLen, focusPointY + bracketOffset, focusCornerPaint)
        canvas.drawLine(focusPointX - bracketOffset, focusPointY + bracketOffset, focusPointX - bracketOffset, focusPointY + bracketOffset - bracketLen, focusCornerPaint)

        // Bottom-Right bracket
        canvas.drawLine(focusPointX + bracketOffset, focusPointY + bracketOffset, focusPointX + bracketOffset - bracketLen, focusPointY + bracketOffset, focusCornerPaint)
        canvas.drawLine(focusPointX + bracketOffset, focusPointY + bracketOffset, focusPointX + bracketOffset, focusPointY + bracketOffset - bracketLen, focusCornerPaint)

        // Center dot
        canvas.drawCircle(focusPointX, focusPointY, 3f, focusRingPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val cornerLen = 32f
        val radius = 10f

        canvas.drawLine(left + radius, top, left + cornerLen, top, cornerPaint)
        canvas.drawArc(RectF(left, top, left + radius * 2, top + radius * 2), 180f, 90f, false, cornerPaint)
        canvas.drawLine(left, top + radius, left, top + cornerLen, cornerPaint)

        canvas.drawLine(right - cornerLen, top, right - radius, top, cornerPaint)
        canvas.drawArc(RectF(right - radius * 2, top, right, top + radius * 2), 270f, 90f, false, cornerPaint)
        canvas.drawLine(right, top + radius, right, top + cornerLen, cornerPaint)

        canvas.drawLine(left + radius, bottom, left + cornerLen, bottom, cornerPaint)
        canvas.drawArc(RectF(left, bottom - radius * 2, left + radius * 2, bottom), 90f, 90f, false, cornerPaint)
        canvas.drawLine(left, bottom - cornerLen, left, bottom - radius, cornerPaint)

        canvas.drawLine(right - cornerLen, bottom, right - radius, bottom, cornerPaint)
        canvas.drawArc(RectF(right - radius * 2, bottom - radius * 2, right, bottom), 0f, 90f, false, cornerPaint)
        canvas.drawLine(right, bottom - cornerLen, right, bottom - radius, cornerPaint)
    }

    private fun drawSuccessBadge(canvas: Canvas, cx: Float, cy: Float) {
        val scale = feedbackProgress.coerceIn(0f, 1.2f)
        val badgeRadius = 28f * scale

        badgeFillPaint.color = Color.parseColor("#00E676")
        canvas.drawCircle(cx, cy, badgeRadius, badgeFillPaint)

        reticlePaint.color = Color.WHITE
        reticlePaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, badgeRadius, reticlePaint)

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

        badgeFillPaint.color = Color.parseColor("#FF1744")
        canvas.drawCircle(cx, cy, badgeRadius, badgeFillPaint)

        reticlePaint.color = Color.WHITE
        reticlePaint.strokeWidth = 3f
        canvas.drawCircle(cx, cy, badgeRadius, reticlePaint)

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
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
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
        focusRingAnimator?.cancel()
        focusFadeAnimator?.cancel()
    }
}
