package com.kenneth.shotbubble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile that shows or hides the bubble. */
class BubbleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val service = ShotAccessibilityService.instance
        if (service == null) {
            openAccessibilitySettings()
            return
        }
        val visible = !Prefs.isBubbleVisible(this)
        Prefs.setBubbleVisible(this, visible)
        service.setBubbleVisible(visible)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val serviceOn = ShotAccessibilityService.instance != null
        tile.state = if (serviceOn && Prefs.isBubbleVisible(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (serviceOn) null else getString(R.string.tile_service_off)
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
