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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.roundToInt

/** iOS AssistiveTouch-style floating button: tap to trigger, drag to move, snaps to the nearest edge. */
@SuppressLint("ViewConstructor")
class BubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val onTap: () -> Unit,
) : View(context) {

    private val sizePx = dp(52)
    private val edgeMarginPx = dp(4)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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

    private val dimRunnable = Runnable { animate().alpha(IDLE_ALPHA).setDuration(300).start() }

    fun attach() {
        applySavedPosition()
        windowManager.addView(this, windowParams)
        scheduleDim()
    }

    fun detach() {
        snapAnimator?.cancel()
        removeCallbacks(dimRunnable)
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
                isPressed = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                if (dragging) moveTo(startX + dx.roundToInt(), startY + dy.roundToInt())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                invalidate()
                if (dragging) {
                    snapToEdge()
                } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                scheduleDim()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    private fun wake() {
        removeCallbacks(dimRunnable)
        animate().cancel()
        alpha = 1f
    }

    private fun scheduleDim() {
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
