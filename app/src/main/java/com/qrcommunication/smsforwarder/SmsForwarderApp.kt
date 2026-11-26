package com.qrcommunication.smsforwarder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmsForwarderApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Lets WorkManager create @HiltWorker workers (default initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    companion object {
        const val CHANNEL_ID_FORWARDING = "sms_forwarding_channel"
        const val CHANNEL_ID_STATUS = "sms_status_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val forwardingChannel = NotificationChannel(
            CHANNEL_ID_FORWARDING,
            getString(R.string.channel_forwarding_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.channel_forwarding_description)
        }

        val statusChannel = NotificationChannel(
            CHANNEL_ID_STATUS,
            getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.channel_status_description)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(forwardingChannel)
        notificationManager.createNotificationChannel(statusChannel)
    }
}
