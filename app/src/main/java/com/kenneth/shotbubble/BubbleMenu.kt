package com.kenneth.shotbubble

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Insets
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.doOnLayout
import kotlin.math.roundToInt

/**
 * The square panel that expands next to the bubble, like iOS AssistiveTouch. It is laid out for
 * one-handed use: the [primary] item's cell is lined up with the bubble, so after tapping the
 * bubble the thumb only has to slide sideways.
 * It lives in its own
 * It lives in its own full-screen overlay window so a tap anywhere outside the panel closes it, and it closes by
 * itself after [AUTO_DISMISS_MS] without a choice.
 */
class BubbleMenu(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDismiss: () -> Unit,
) {
    class Item(@DrawableRes val icon: Int, @StringRes val label: Int, val action: () -> Unit)

    private var root: FrameLayout? = null
    private val autoDismiss = Runnable { dismiss() }

    val isShowing: Boolean get() = root != null

    /** [rows] are drawn top to bottom, each row left to right, the same whichever side the bubble is on. */
    fun show(anchor: View, rows: List<List<Item>>, primary: Item) {
        if (root != null) return
        val cells = HashMap<Item, View>()
        val panel = buildPanel(rows, cells).apply {
            isClickable = true // Taps on the panel's padding must not count as "outside".
            alpha = 0f
        }
        val root = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
            addView(panel, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        panel.doOnLayout { placeNextTo(anchor, root, panel, cells.getValue(primary)) }
        windowManager.addView(root, params)
        this.root = root
        root.postDelayed(autoDismiss, AUTO_DISMISS_MS)
    }

    fun dismiss() {
        val root = root ?: return
        this.root = null
        root.removeCallbacks(autoDismiss)
        if (root.isAttachedToWindow) windowManager.removeView(root)
        onDismiss()
    }

    /** Puts the panel on the screen-center side of the bubble, with [primaryCell] level with it. */
    private fun placeNextTo(anchor: View, root: View, panel: View, primaryCell: View) {
        val anchorLoc = IntArray(2).also(anchor::getLocationOnScreen)
        val rootLoc = IntArray(2).also(root::getLocationOnScreen)
        val ax = anchorLoc[0] - rootLoc[0]
        val ay = anchorLoc[1] - rootLoc[1]
        val gap = dp(8)
        val margin = dp(8)

        val bubbleOnLeft = ax + anchor.width / 2 < root.width / 2
        val x = if (bubbleOnLeft) ax + anchor.width + gap else ax - gap - panel.width
        // primaryCell sits in a row; its center relative to the panel is row top + cell center.
        val primaryRow = primaryCell.parent as View
        val primaryCenterY = primaryRow.top + primaryCell.top + primaryCell.height / 2
        val y = ay + anchor.height / 2 - primaryCenterY
        // Keep the panel clear of the status/navigation bars and cutouts, like the bubble itself.
        val bars = root.rootWindowInsets?.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        ) ?: Insets.NONE
        val minX = bars.left + margin
        val minY = bars.top + margin
        panel.x = x.coerceIn(minX, (root.width - bars.right - panel.width - margin).coerceAtLeast(minX)).toFloat()
        panel.y = y.coerceIn(minY, (root.height - bars.bottom - panel.height - margin).coerceAtLeast(minY)).toFloat()

        panel.pivotX = if (bubbleOnLeft) 0f else panel.width.toFloat()
        panel.pivotY = primaryCenterY.toFloat()
        panel.scaleX = 0.85f
        panel.scaleY = 0.85f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(140).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun buildPanel(rows: List<List<Item>>, cells: MutableMap<Item, View>) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(0xEB2B2B2E.toInt())
            cornerRadius = dp(22).toFloat()
        }
        val pad = dp(8)
        setPadding(pad, pad, pad, pad)
        rows.forEach { row ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEach { item ->
                    val cell = buildItem(item)
                    cells[item] = cell
                    addView(cell, LinearLayout.LayoutParams(dp(CELL_DP), dp(CELL_DP)))
                }
            })
        }
    }

    private fun buildItem(item: Item) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF),
            null,
            GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(12).toFloat()
            },
        )
        addView(
            ImageView(context).apply {
                setImageResource(item.icon)
                imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(30), dp(30)),
        )
        addView(
            TextView(context).apply {
                setText(item.label)
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(6) },
        )
        setOnClickListener {
            dismiss()
            item.action()
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val AUTO_DISMISS_MS = 5000L
        const val CELL_DP = 84
    }
}
