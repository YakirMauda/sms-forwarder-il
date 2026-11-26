package com.qrcommunication.smsforwarder.service

import android.util.Log
import com.qrcommunication.smsforwarder.data.local.entity.DestinationType
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
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

    suspend fun retryFailedSms(record: SmsRecord): Boolean {
        if (record.retryCount >= MAX_RETRIES) {
            Log.w(TAG, "Max retries reached for record ${record.id}")
            return false
        }

        val rule = record.ruleId?.let { ruleRepository.getById(it) }
        val type = rule?.let { DestinationType.fromValue(it.destinationType) } ?: DestinationType.SMS
        val payload = MessagePayload(
            sender = record.sender,
            content = record.content,
            receivedAt = record.receivedAt,
            originalDestination = preferencesManager.destinationNumber.takeIf { it.isNotBlank() },
        )

        return try {
            dispatcher.dispatch(type, record.destination, payload)
            smsRepository.updateRecord(
                record.copy(
                    status = SmsStatus.SENT.value,
                    forwardedAt = System.currentTimeMillis(),
                    retryCount = record.retryCount + 1,
                    errorMessage = null,
                ),
            )
            preferencesManager.incrementSmsCount()
            rule?.let { ruleRepository.recordSuccess(it.id) }
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
            rule?.let { ruleRepository.recordFailure(it.id, error) }
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
