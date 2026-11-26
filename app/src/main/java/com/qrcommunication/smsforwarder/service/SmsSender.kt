package com.qrcommunication.smsforwarder.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.qrcommunication.smsforwarder.receiver.SmsStatusReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level SMS sending.
 *
 * Always splits with `divideMessage` (Hebrew is UCS-2: 70 chars per part, 67 when split)
 * and sends every part. When [sendSms] gets a recordId, each part carries "sent" and
 * "delivered" PendingIntents to [SmsStatusReceiver], which updates that history record
 * (FAILED + error code, or delivered time).
 */
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "SmsSender"
    }

    /** Untracked send from the default SIM (test SMS from Settings / onboarding). */
    fun sendSms(destination: String, message: String) = sendSms(destination, message, 0L, null)

    fun sendSms(destination: String, message: String, recordId: Long, subscriptionId: Int?) {
        val smsManager = smsManagerFor(subscriptionId)
        val parts = smsManager.divideMessage(message)
        val track = recordId > 0

        if (parts.size <= 1) {
            smsManager.sendTextMessage(
                destination, null, message,
                if (track) statusIntent(SmsStatusReceiver.ACTION_SENT, recordId, 0, 1) else null,
                if (track) statusIntent(SmsStatusReceiver.ACTION_DELIVERED, recordId, 0, 1) else null,
            )
        } else {
            val sent = ArrayList<PendingIntent?>(parts.size)
            val delivered = ArrayList<PendingIntent?>(parts.size)
            for (i in parts.indices) {
                sent += if (track) statusIntent(SmsStatusReceiver.ACTION_SENT, recordId, i, parts.size) else null
                // One delivery report (for the last part) is enough to know the message arrived.
                delivered += if (track && i == parts.lastIndex) {
                    statusIntent(SmsStatusReceiver.ACTION_DELIVERED, recordId, i, parts.size)
                } else {
                    null
                }
            }
            smsManager.sendMultipartTextMessage(destination, null, parts, sent, delivered)
        }
        Log.d(TAG, "SMS queued: ${parts.size} part(s), record $recordId, sub $subscriptionId")
    }

    @Suppress("DEPRECATION")
    private fun smsManagerFor(subscriptionId: Int?): SmsManager {
        val default = context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        if (subscriptionId == null) return default
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            default.createForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    private fun statusIntent(action: String, recordId: Long, part: Int, parts: Int): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_RECORD_ID, recordId)
            putExtra(SmsStatusReceiver.EXTRA_PART, part)
            putExtra(SmsStatusReceiver.EXTRA_PARTS, parts)
        }
        // Unique per record + part + action so intents never overwrite each other.
        val requestCode = (recordId * 64 + part * 2 + if (action == SmsStatusReceiver.ACTION_SENT) 0 else 1).toInt()
        // Mutable so the telephony stack can attach its error details to the result.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }
}
