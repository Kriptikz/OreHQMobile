package com.example.orehqmobile.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.orehqmobile.MainActivity
import com.example.orehqmobile.R

internal object NotificationsHelper {

    private const val NOTIFICATION_CHANNEL_ID = "general_notification_channel"

    fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager

        // create the notification channel
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.foreground_service_sample_notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, hashpower: UInt, difficulty: UInt): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.foreground_service_notification_title))
            .setContentText("Hashpower: $hashpower H/s, Difficulty: $difficulty")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(Intent(context, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            })
            .build()
    }

    fun updateNotification(context: Context, notificationId: Int, hashpower: UInt, difficulty: UInt) {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val notification = buildNotification(context, hashpower, difficulty)
      notificationManager.notify(notificationId, notification)
  }
}
