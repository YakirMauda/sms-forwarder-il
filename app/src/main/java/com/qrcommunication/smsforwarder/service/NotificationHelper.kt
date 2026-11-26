package com.qrcommunication.smsforwarder.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.qrcommunication.smsforwarder.R
import com.qrcommunication.smsforwarder.SmsForwarderApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun createSmsNotification(sender: String, preview: String): Notification {
        return NotificationCompat.Builder(context, SmsForwarderApp.CHANNEL_ID_STATUS)
            .setContentTitle(context.getString(R.string.notification_sms_forwarded_title))
            .setContentText(context.getString(R.string.notification_from_format, sender, preview))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setAutoCancel(true)
            .build()
    }

    fun updateNotification(notificationId: Int, notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
