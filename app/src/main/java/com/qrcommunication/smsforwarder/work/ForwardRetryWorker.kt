package com.qrcommunication.smsforwarder.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.service.RetryScheduler
import com.qrcommunication.smsforwarder.service.SmsRetryManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background job (run by WorkManager, survives app kill and reboot) that retries one
 * FAILED forwarding record. On another failure it schedules the next attempt according
 * to the user's RetryPolicy, so the back-off delays come from WorkManager, not a sleep.
 */
@HiltWorker
class ForwardRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smsRepository: SmsRepository,
    private val retryManager: SmsRetryManager,
    private val retryScheduler: RetryScheduler,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ForwardRetryWorker"
        const val KEY_RECORD_ID = "record_id"

        fun inputFor(recordId: Long) = workDataOf(KEY_RECORD_ID to recordId)
    }

    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, -1L)
        val record = smsRepository.getRecordById(recordId)
        if (record == null || record.status != SmsStatus.FAILED.value) {
            // Deleted, or already sent (e.g. retried by hand from History) -> nothing to do.
            Log.d(TAG, "Record $recordId no longer needs a retry")
            return Result.success()
        }

        if (!retryManager.retryFailedSms(record)) {
            val scheduled = retryScheduler.scheduleRetry(recordId, attempt = record.retryCount + 1)
            Log.w(TAG, "Retry of record $recordId failed, next attempt scheduled=$scheduled")
        }
        return Result.success()
    }
}
