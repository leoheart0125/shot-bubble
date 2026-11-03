package com.kenneth.shotbubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * iOS AssistiveTouch-style floating button: tap to open the menu, drag to move, snaps to the
 * nearest edge. When quick capture is on, a double tap or a long press (per [Prefs]) calls
 * [onQuickCapture] instead. A long press only counts while the finger stays within touch slop,
 * so dragging the bubble never triggers it.
 */
@SuppressLint("ViewConstructor")
class BubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val onTap: () -> Unit,
    private val onQuickCapture: () -> Unit,
) : View(context) {

    private val sizePx = dp(52)
    private val edgeMarginPx = dp(4)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    private val windowParams = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC2B2B2E.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
    private val bgRect = RectF()

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var snapAnimator: ValueAnimator? = null

    /** Read on each ACTION_DOWN so changes in the app's settings apply without a restart. */
    private var trigger: QuickTrigger? = null
    /** The gesture already fired a quick capture (long press or double tap); ignore the rest of it. */
    private var longPressFired = false
    /** A first tap is waiting to see whether a second one follows (double-tap mode only). */
    private var awaitingSecondTap = false

    private val dimRunnable = Runnable { animate().alpha(IDLE_ALPHA).setDuration(300).start() }

    private val longPressRunnable = Runnable {
        longPressFired = true
        isPressed = false
        invalidate()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onQuickCapture()
    }

    private val singleTapRunnable = Runnable {
        awaitingSecondTap = false
        performClick()
    }

    fun attach() {
        applySavedPosition()
        windowManager.addView(this, windowParams)
        scheduleDim()
    }

    fun detach() {
        snapAnimator?.cancel()
        removeCallbacks(dimRunnable)
        removeCallbacks(longPressRunnable)
        removeCallbacks(singleTapRunnable)
        if (isAttachedToWindow) windowManager.removeView(this)
    }

    /** Re-applies the saved edge/height, e.g. after rotation changed the screen size. */
    fun reposition() {
        snapAnimator?.cancel()
        applySavedPosition()
        if (isAttachedToWindow) windowManager.updateViewLayout(this, windowParams)
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(bgRect, r * 0.5f, r * 0.5f, bgPaint)
        canvas.drawCircle(r, r, r * if (isPressed) 0.7f else 0.62f, ringPaint)
        canvas.drawCircle(r, r, r * 0.38f, corePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                wake()
                downRawX = event.rawX
                downRawY = event.rawY
                startX = windowParams.x
                startY = windowParams.y
                dragging = false
                longPressFired = false
                isPressed = true
                invalidate()

                trigger = Prefs.activeQuickTrigger(context)
                if (awaitingSecondTap) {
                    // Second tap of a double tap: fire on touch-down, like GestureDetector, instead of
                    // waiting for the finger to lift. The rest of this gesture is then ignored.
                    removeCallbacks(singleTapRunnable)
                    awaitingSecondTap = false
                    longPressFired = true
                    isPressed = false
                    invalidate()
                    onQuickCapture()
                    return true
                }
                if (trigger == QuickTrigger.LONG_PRESS) postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                // The screenshot is already on its way; ignore the rest of this gesture.
                if (longPressFired) return true
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > touchSlop) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) moveTo(startX + dx.roundToInt(), startY + dy.roundToInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                isPressed = false
                invalidate()
                // Schedule before the tap handling so the menu can keep the bubble awake.
                scheduleDim()
                when {
                    longPressFired -> Unit
                    dragging -> snapToEdge()
                    event.actionMasked == MotionEvent.ACTION_CANCEL -> Unit
                    trigger == QuickTrigger.DOUBLE_TAP -> {
                        awaitingSecondTap = true
                        postDelayed(singleTapRunnable, doubleTapTimeout)
                    }
                    else -> performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    /** Full opacity until [scheduleDim] is called again. */
    fun wake() {
        removeCallbacks(dimRunnable)
        animate().cancel()
        alpha = 1f
    }

    fun scheduleDim() {
        removeCallbacks(dimRunnable)
        postDelayed(dimRunnable, IDLE_DELAY_MS)
    }

    private fun moveTo(x: Int, y: Int) {
        val area = usableArea()
        windowParams.x = x.coerceIn(area.left, area.right - sizePx)
        windowParams.y = y.coerceIn(area.top, area.bottom - sizePx)
        windowManager.updateViewLayout(this, windowParams)
    }

    private fun snapToEdge() {
        val area = usableArea()
        val onLeft = windowParams.x + sizePx / 2 < area.centerX()
        val targetX = edgeX(area, onLeft)
        val yFraction = (windowParams.y - area.top).toFloat() / (area.height() - sizePx).coerceAtLeast(1)
        Prefs.savePosition(context, onLeft, yFraction.coerceIn(0f, 1f))

        snapAnimator = ValueAnimator.ofInt(windowParams.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                if (!isAttachedToWindow) return@addUpdateListener
                windowParams.x = it.animatedValue as Int
                windowManager.updateViewLayout(this@BubbleView, windowParams)
            }
            start()
        }
    }

    private fun applySavedPosition() {
        val area = usableArea()
        windowParams.x = edgeX(area, Prefs.isOnLeft(context))
        windowParams.y = area.top + ((area.height() - sizePx) * Prefs.yFraction(context)).roundToInt()
    }

    private fun edgeX(area: Rect, onLeft: Boolean) =
        if (onLeft) area.left + edgeMarginPx else area.right - sizePx - edgeMarginPx

    /** Screen bounds minus status/navigation bars and cutouts, in window coordinates. */
    private fun usableArea(): Rect {
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        val bounds = metrics.bounds
        return Rect(insets.left, insets.top, bounds.width() - insets.right, bounds.height() - insets.bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val IDLE_ALPHA = 0.4f
        const val IDLE_DELAY_MS = 3000L
    }
}
