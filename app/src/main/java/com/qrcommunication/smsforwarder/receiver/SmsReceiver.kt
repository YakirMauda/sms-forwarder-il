package com.qrcommunication.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.qrcommunication.smsforwarder.domain.model.IncomingSms
import com.qrcommunication.smsforwarder.service.ForwardingProcessor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Entry point of the app: Android wakes us up for every incoming SMS, even when the app
 * is closed. No foreground service and no persistent notification are needed.
 *
 * `goAsync()` keeps the broadcast alive (~10s) while we do the DB work and the send on a
 * background thread; anything that fails is handed to WorkManager for a later retry.
 */
class SmsReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsReceiverEntryPoint {
        fun forwardingProcessor(): ForwardingProcessor
    }

    companion object {
        private const val TAG = "SmsReceiver"

        /** Safety net; ForwardingProcessor itself times out the send earlier. */
        private const val RECEIVER_BUDGET_MS = 9_000L

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w(TAG, "Received SMS intent but no messages found")
            return
        }

        // A long SMS arrives as several segments: join them into one message.
        val sms = IncomingSms(
            sender = messages[0].displayOriginatingAddress ?: "Unknown",
            body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" },
            timestamp = messages[0].timestampMillis,
            simSlot = resolveSimSlot(context, intent),
        )
        Log.d(TAG, "SMS received, length=${sms.body.length}, simSlot=${sms.simSlot}")

        val processor = EntryPointAccessors
            .fromApplication(context.applicationContext, SmsReceiverEntryPoint::class.java)
            .forwardingProcessor()

        val pendingResult = goAsync()
        scope.launch {
            try {
                val outcome = withTimeoutOrNull(RECEIVER_BUDGET_MS) { processor.process(sms) }
                Log.d(TAG, "Outcome: ${outcome ?: "receiver budget exceeded"}")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while forwarding", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun resolveSimSlot(context: Context, intent: Intent): Int {
        val subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1)
            .takeIf { it >= 0 }
            ?: intent.getIntExtra("subscription", -1)
        if (subscriptionId < 0) return -1
        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            subscriptionManager?.getActiveSubscriptionInfo(subscriptionId)?.simSlotIndex ?: -1
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot resolve SIM slot, READ_PHONE_STATE missing?", e)
            -1
        }
    }
}
