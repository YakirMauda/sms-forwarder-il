package com.qrcommunication.smsforwarder.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qrcommunication.smsforwarder.service.SmsErrors
import com.qrcommunication.smsforwarder.service.SmsStatusHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the radio's "sent" / "delivered" results for forwarded SMS (internal, not exported). */
class SmsStatusReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StatusEntryPoint {
        fun statusHandler(): SmsStatusHandler
    }

    companion object {
        const val ACTION_SENT = "com.yakirmauda.smsforwarder.action.SMS_SENT"
        const val ACTION_DELIVERED = "com.yakirmauda.smsforwarder.action.SMS_DELIVERED"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_PART = "part"
        const val EXTRA_PARTS = "parts"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, 0L)
        if (recordId <= 0) return
        val code = resultCode
        val handler = EntryPointAccessors
            .fromApplication(context.applicationContext, StatusEntryPoint::class.java)
            .statusHandler()

        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_SENT -> if (code != Activity.RESULT_OK) {
                        val radioError = intent.getIntExtra("errorCode", -1)
                        handler.onSendFailed(recordId, SmsErrors.describe(code, radioError))
                    }
                    ACTION_DELIVERED -> if (code == Activity.RESULT_OK) handler.onDelivered(recordId)
                }
            } catch (e: Exception) {
                Log.e("SmsStatusReceiver", "Failed to update record $recordId", e)
            } finally {
                pending.finish()
            }
        }
    }
}
