package com.kenneth.shotbubble

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit

/** Where a screenshot goes. */
enum class CaptureMode { CLIPBOARD, GALLERY }

/** The gesture on the bubble that takes a screenshot without opening the menu. */
enum class QuickTrigger { DOUBLE_TAP, LONG_PRESS }

/** The bubble menu's buttons. */
enum class MenuAction(@DrawableRes val icon: Int, @StringRes val label: Int) {
    COPY(R.drawable.ic_menu_copy, R.string.menu_copy),
    GALLERY(R.drawable.ic_menu_gallery, R.string.menu_gallery),
    HIDE(R.drawable.ic_menu_hide, R.string.menu_hide),
    BACK(R.drawable.ic_menu_close, R.string.menu_back),
}

object Prefs {
    private const val NAME = "shot_bubble"
    private const val KEY_VISIBLE = "bubble_visible"
    private const val KEY_ON_LEFT = "bubble_on_left"
    private const val KEY_Y_FRACTION = "bubble_y_fraction"
    private const val KEY_MENU_SLOTS = "menu_slots"
    private const val KEY_QUICK_ENABLED = "quick_enabled"
    private const val KEY_QUICK_MODE = "quick_mode"
    private const val KEY_QUICK_TRIGGER = "quick_trigger"

    /** Menu slots in order: top-left, top-right, bottom-left, bottom-right. Copy sits nearest a right thumb. */
    val DEFAULT_MENU_SLOTS = listOf(MenuAction.HIDE, MenuAction.GALLERY, MenuAction.BACK, MenuAction.COPY)

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isBubbleVisible(context: Context): Boolean = prefs(context).getBoolean(KEY_VISIBLE, true)

    fun setBubbleVisible(context: Context, visible: Boolean) =
        prefs(context).edit { putBoolean(KEY_VISIBLE, visible) }

    fun isOnLeft(context: Context): Boolean = prefs(context).getBoolean(KEY_ON_LEFT, false)

    /** Vertical position as a fraction of the usable screen height, so it survives rotation. */
    fun yFraction(context: Context): Float = prefs(context).getFloat(KEY_Y_FRACTION, 0.4f)

    fun savePosition(context: Context, onLeft: Boolean, yFraction: Float) =
        prefs(context).edit {
            putBoolean(KEY_ON_LEFT, onLeft)
            putFloat(KEY_Y_FRACTION, yFraction)
        }

    /** Always a permutation of every [MenuAction]; anything else saved falls back to the default. */
    fun menuSlots(context: Context): List<MenuAction> {
        val saved = prefs(context).getString(KEY_MENU_SLOTS, null)
            ?.split(',')
            ?.mapNotNull { enumOrNull<MenuAction>(it) }
        return saved?.takeIf { it.toSet() == MenuAction.entries.toSet() && it.size == MenuAction.entries.size }
            ?: DEFAULT_MENU_SLOTS
    }

    fun setMenuSlots(context: Context, slots: List<MenuAction>) =
        prefs(context).edit { putString(KEY_MENU_SLOTS, slots.joinToString(",") { it.name }) }

    fun isQuickCaptureEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_QUICK_ENABLED, false)

    fun setQuickCaptureEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_QUICK_ENABLED, enabled) }

    fun quickCaptureMode(context: Context): CaptureMode =
        enumOrNull<CaptureMode>(prefs(context).getString(KEY_QUICK_MODE, null)) ?: CaptureMode.CLIPBOARD

    fun setQuickCaptureMode(context: Context, mode: CaptureMode) =
        prefs(context).edit { putString(KEY_QUICK_MODE, mode.name) }

    fun quickTrigger(context: Context): QuickTrigger =
        enumOrNull<QuickTrigger>(prefs(context).getString(KEY_QUICK_TRIGGER, null)) ?: QuickTrigger.DOUBLE_TAP

    fun setQuickTrigger(context: Context, trigger: QuickTrigger) =
        prefs(context).edit { putString(KEY_QUICK_TRIGGER, trigger.name) }

    /** The quick-capture gesture the bubble should listen for, or null when quick capture is off. */
    fun activeQuickTrigger(context: Context): QuickTrigger? =
        if (isQuickCaptureEnabled(context)) quickTrigger(context) else null

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        enumValues<T>().firstOrNull { it.name == name }
}
