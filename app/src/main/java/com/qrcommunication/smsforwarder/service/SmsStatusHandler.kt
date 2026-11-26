package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Readable text for SmsManager result codes (the History screen shows it as the error). */
object SmsErrors {
    // Values of SmsManager.RESULT_ERROR_* (kept literal so this stays pure Kotlin).
    private val names = mapOf(
        1 to "generic failure",
        2 to "radio off",
        3 to "null PDU",
        4 to "no service",
        5 to "limit exceeded",
        6 to "FDN check failure",
        7 to "short code not allowed",
        8 to "short code never allowed",
    )

    fun describe(resultCode: Int, radioErrorCode: Int = -1): String {
        val base = "SMS error $resultCode (${names[resultCode] ?: "unknown"})"
        return if (radioErrorCode >= 0) "$base, radio code $radioErrorCode" else base
    }
}

/** Applies radio callbacks to the history record; a failure is retried via WorkManager. */
@Singleton
class SmsStatusHandler @Inject constructor(
    private val smsRepository: SmsRepository,
    private val retryScheduler: RetryScheduler,
) {
    suspend fun onSendFailed(recordId: Long, error: String) {
        val record = smsRepository.getRecordById(recordId) ?: return
        // A multipart SMS reports once per part: only the first failure counts.
        if (record.status == SmsStatus.FAILED.value) return
        smsRepository.updateRecord(record.copy(status = SmsStatus.FAILED.value, errorMessage = error, forwardedAt = null))
        retryScheduler.scheduleRetry(recordId, attempt = record.retryCount)
    }

    suspend fun onDelivered(recordId: Long) {
        val record = smsRepository.getRecordById(recordId) ?: return
        smsRepository.updateRecord(record.copy(deliveredAt = System.currentTimeMillis()))
    }
}
