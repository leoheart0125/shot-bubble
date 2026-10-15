package com.kenneth.shotbubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import java.io.File

/**
 * Posts the "copied" notification with a "save to gallery" action. A single notification ID is
 * reused, which matches ScreenshotStore keeping only the latest file.
 */
object Notifier {
    private const val CHANNEL_ID = "captures"
    private const val NOTIFICATION_ID = 1

    fun showCaptured(context: Context, file: File, thumbnail: Bitmap) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_captures),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                // The bubble already vibrates on capture; keep the heads-up silent.
                setSound(null, null)
                enableVibration(false)
            }
        )

        val saveIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SaveToGalleryReceiver::class.java)
                .putExtra(SaveToGalleryReceiver.EXTRA_FILE_NAME, file.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text))
            .setLargeIcon(thumbnail)
            .setStyle(Notification.BigPictureStyle().bigPicture(thumbnail).bigLargeIcon(null as Icon?))
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(
                Notification.Action.Builder(null as Icon?, context.getString(R.string.notif_action_save), saveIntent)
                    .build()
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
}
