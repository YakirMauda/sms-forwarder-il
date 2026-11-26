package com.qrcommunication.smsforwarder.service

/**
 * Schedules a later, automatic re-send of a failed forwarding record.
 * Kept as an interface so the forwarding logic does not depend on WorkManager
 * (Dependency Inversion) and can be unit-tested with a fake.
 */
interface RetryScheduler {
    /**
     * @param attempt 0-based index of the retry being scheduled (0 = first retry).
     * @return true if a retry was scheduled, false if the retry policy is exhausted.
     */
    fun scheduleRetry(recordId: Long, attempt: Int): Boolean
}
