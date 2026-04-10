package com.example.appcloner.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationUtils {
    fun createChannel(context: Context, channelId: String, name: String, importance: Int = NotificationManager.IMPORTANCE_LOW) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(channelId, name, importance)
            nm.createNotificationChannel(chan)
        }
    }

    fun buildNotification(
        context: Context,
        channelId: String,
        title: String,
        content: String? = null,
        indeterminate: Boolean = false,
        ongoing: Boolean = true,
        progress: Int? = null,
        smallIcon: Int = android.R.drawable.stat_sys_download
    ): Notification {
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setSmallIcon(smallIcon)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)

        if (indeterminate) builder.setProgress(0, 0, true)
        else if (progress != null) builder.setProgress(100, progress, false)

        if (!content.isNullOrEmpty()) builder.setContentText(content)

        return builder.build()
    }

    fun updateNotification(
        context: Context,
        channelId: String,
        notifId: Int,
        progress: Int,
        content: String?,
        finalText: String? = null,
        ongoing: Boolean = true,
        smallIcon: Int = android.R.drawable.stat_sys_download
    ) {
        val notification = if (finalText != null) {
            buildNotification(context, channelId, content ?: "", finalText, indeterminate = false, ongoing = ongoing, progress = if (progress >= 100) 0 else progress, smallIcon = smallIcon)
        } else {
            buildNotification(context, channelId, "Repacking", content, indeterminate = (progress <= 0), ongoing = ongoing, progress = if (progress > 0) progress else null, smallIcon = smallIcon)
        }

        try {
            // Attempt to post the notification; on Android 13+ this may throw SecurityException
            // if the runtime `POST_NOTIFICATIONS` permission is not granted. Catch and ignore
            // as callers already use UI fallbacks.
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
