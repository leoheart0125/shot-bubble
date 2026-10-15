package com.kenneth.shotbubble

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object Prefs {
    private const val NAME = "shot_bubble"
    private const val KEY_VISIBLE = "bubble_visible"
    private const val KEY_ON_LEFT = "bubble_on_left"
    private const val KEY_Y_FRACTION = "bubble_y_fraction"

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
}
