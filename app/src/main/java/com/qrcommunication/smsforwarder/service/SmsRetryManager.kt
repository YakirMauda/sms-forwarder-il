package com.qrcommunication.smsforwarder.service

import android.util.Log
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.service.sender.DestinationDispatcher
import com.qrcommunication.smsforwarder.service.sender.MessagePayload
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-sends one FAILED record to the same destination/channel it originally targeted.
 * No waiting here: back-off between attempts is handled by WorkManager
 * ([com.qrcommunication.smsforwarder.work.WorkManagerRetryScheduler]).
 */
@Singleton
class SmsRetryManager @Inject constructor(
    private val smsRepository: SmsRepository,
    private val dispatcher: DestinationDispatcher,
    private val ruleRepository: ForwardingRuleRepository,
    private val preferencesManager: PreferencesManager,
) {
    companion object {
        private const val TAG = "SmsRetryManager"
        const val MAX_RETRIES = 3
    }

    /** Automatic retry (WorkManager): gives up after [MAX_RETRIES]. */
    suspend fun retryFailedSms(record: SmsRecord): Boolean {
        if (record.retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Max retries reached for record ${record.id}")
            return false
        }
        return resend(record)
    }

    /** Manual "Retry" from History: always tries, whatever the automatic retry count. */
    suspend fun retryNow(record: SmsRecord): Boolean = resend(record)

    private suspend fun resend(record: SmsRecord): Boolean {

        val channel = Channel.entries.firstOrNull { it.name == record.channel } ?: Channel.SMS
        val payload = MessagePayload(
            sender = record.sender,
            content = record.content,
            receivedAt = record.receivedAt,
            originalDestination = preferencesManager.destinationNumber.takeIf { it.isNotBlank() },
            recordId = record.id,
            incomingSimSlot = record.incomingSimSlot,
        )

        return try {
            dispatcher.dispatch(channel, record.destination, payload)
            smsRepository.updateRecord(
                record.copy(
                    status = SmsStatus.SENT.value,
                    forwardedAt = System.currentTimeMillis(),
                    retryCount = record.retryCount + 1,
                    errorMessage = null,
                ),
            )
            preferencesManager.incrementSmsCount()
            record.ruleId?.let { ruleRepository.recordSuccess(it) }
            Log.i(TAG, "Retry successful for record ${record.id}")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Retry failed for record ${record.id}", e)
            val error = e.message ?: e.javaClass.simpleName
            smsRepository.updateRecord(
                record.copy(
                    status = SmsStatus.FAILED.value,
                    retryCount = record.retryCount + 1,
                    errorMessage = error,
                ),
            )
            record.ruleId?.let { ruleRepository.recordFailure(it, error) }
            false
        }
    }

    suspend fun retryAllFailed() {
        val failedRecords = smsRepository.getRecordsByStatus(SmsStatus.FAILED)
            .first()
            .filter { it.retryCount < MAX_RETRIES }

        Log.d(TAG, "Found ${failedRecords.size} failed records to retry")
        for (record in failedRecords) {
            retryFailedSms(record)
        }
    }
}
