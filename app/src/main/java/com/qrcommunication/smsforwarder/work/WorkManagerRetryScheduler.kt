package com.qrcommunication.smsforwarder.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.service.RetryScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerRetryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) : RetryScheduler {

    override fun scheduleRetry(recordId: Long, attempt: Int): Boolean {
        val policy = preferencesManager.retryPolicy
        if (!policy.shouldRetry(attempt)) return false

        val request = OneTimeWorkRequestBuilder<ForwardRetryWorker>()
            .setInitialDelay(policy.delayFor(attempt), TimeUnit.MILLISECONDS)
            .setInputData(ForwardRetryWorker.inputFor(recordId))
            .addTag(TAG)
            .build()

        // One chain per record: a retry scheduled from inside the running worker is
        // appended after it instead of cancelling it.
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(recordId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
        return true
    }

    companion object {
        const val TAG = "forward-retry"
        fun uniqueName(recordId: Long) = "forward-retry-$recordId"
    }
}
