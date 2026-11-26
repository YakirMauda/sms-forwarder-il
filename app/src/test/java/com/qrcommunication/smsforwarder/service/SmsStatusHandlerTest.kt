package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SmsStatusHandlerTest {

    private val repo: SmsRepository = mock()
    private val scheduler: RetryScheduler = mock()
    private val handler = SmsStatusHandler(repo, scheduler)

    private fun record(status: SmsStatus, retryCount: Int = 0) = SmsRecord(
        id = 5, sender = "Leumi", content = "x", receivedAt = 1, status = status.value,
        destination = "0501234567", retryCount = retryCount, forwardedAt = 2,
    )

    @Test
    fun sendFailed_marksFailedWithError_andSchedulesRetry() = runTest {
        whenever(repo.getRecordById(5)).thenReturn(record(SmsStatus.SENT, retryCount = 1))

        handler.onSendFailed(5, "SMS error 4 (no service)")

        val captor = argumentCaptor<SmsRecord>()
        verify(repo).updateRecord(captor.capture())
        assertEquals(SmsStatus.FAILED.value, captor.firstValue.status)
        assertEquals("SMS error 4 (no service)", captor.firstValue.errorMessage)
        verify(scheduler).scheduleRetry(5, 1)
    }

    @Test
    fun secondPartFailure_isIgnored() = runTest {
        whenever(repo.getRecordById(5)).thenReturn(record(SmsStatus.FAILED))

        handler.onSendFailed(5, "SMS error 1 (generic failure)")

        verify(repo, never()).updateRecord(any())
        verify(scheduler, never()).scheduleRetry(any(), any())
    }

    @Test
    fun delivered_setsDeliveredAt() = runTest {
        whenever(repo.getRecordById(5)).thenReturn(record(SmsStatus.SENT))

        handler.onDelivered(5)

        val captor = argumentCaptor<SmsRecord>()
        verify(repo).updateRecord(captor.capture())
        assertNotNull(captor.firstValue.deliveredAt)
    }
}
