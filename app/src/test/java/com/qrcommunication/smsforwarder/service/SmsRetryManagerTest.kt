package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.service.sender.DestinationDispatcher
import com.qrcommunication.smsforwarder.service.sender.MessagePayload
import com.qrcommunication.smsforwarder.service.sender.MessageSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SmsRetryManagerTest {

    private class FakeSms(var failWith: String? = null, val onSend: () -> Unit = {}) : MessageSender {
        override val channel = Channel.SMS
        val sent = mutableListOf<Pair<String, MessagePayload>>()
        override suspend fun send(address: String, payload: MessagePayload) {
            onSend()
            failWith?.let { throw IllegalStateException(it) }
            sent += address to payload
        }
    }

    private val repo: SmsRepository = mock()
    private val rules: ForwardingRuleRepository = mock()
    private val prefs: PreferencesManager = mock<PreferencesManager>().also {
        whenever(it.destinationNumber).thenReturn("")
    }

    private val failed = SmsRecord(
        id = 7, sender = "Leumi", content = "קוד 123456", receivedAt = 1, status = SmsStatus.FAILED.value,
        destination = "0501234567", errorMessage = "SMS error 4 (no service)", retryCount = 1, ruleId = 3,
        incomingSimSlot = 1,
    )

    private fun manager(sender: FakeSms) = SmsRetryManager(repo, DestinationDispatcher(setOf(sender)), rules, prefs)

    @Test
    fun success_resendsToSameTargetVerbatim_andMarksSent() = runTest {
        val sender = FakeSms()
        whenever(repo.getRecordById(7)).thenReturn(failed.copy(status = SmsStatus.PENDING.value))

        assertTrue(manager(sender).retryNow(failed))

        assertEquals("0501234567", sender.sent.single().first)
        assertEquals("קוד 123456", sender.sent.single().second.content)
        assertEquals(7L, sender.sent.single().second.recordId)
        assertEquals(1, sender.sent.single().second.incomingSimSlot)
        val saved = argumentCaptor<SmsRecord>()
        verify(repo, org.mockito.kotlin.times(2)).updateRecord(saved.capture())
        assertEquals(SmsStatus.PENDING.value, saved.firstValue.status)
        assertEquals(SmsStatus.SENT.value, saved.secondValue.status)
        assertEquals(2, saved.secondValue.retryCount)
        verify(rules).recordSuccess(3)
    }

    @Test
    fun failure_marksFailedWithError() = runTest {
        val sender = FakeSms(failWith = "radio off")

        assertFalse(manager(sender).retryNow(failed))

        val saved = argumentCaptor<SmsRecord>()
        verify(repo, org.mockito.kotlin.times(2)).updateRecord(saved.capture())
        assertEquals(SmsStatus.FAILED.value, saved.secondValue.status)
        assertEquals("radio off", saved.secondValue.errorMessage)
        verify(rules).recordFailure(3, "radio off")
    }

    @Test
    fun fastRadioFailure_isNotOverwrittenWithSent() = runTest {
        val sender = FakeSms()
        // SmsStatusHandler already marked it FAILED while we were sending.
        whenever(repo.getRecordById(7)).thenReturn(failed.copy(status = SmsStatus.FAILED.value))

        manager(sender).retryNow(failed)

        val saved = argumentCaptor<SmsRecord>()
        verify(repo).updateRecord(saved.capture()) // only the PENDING write
        assertEquals(SmsStatus.PENDING.value, saved.firstValue.status)
    }

    @Test
    fun automaticRetry_givesUpAfterMax_butManualRetryStillWorks() = runTest {
        val sender = FakeSms()
        val exhausted = failed.copy(retryCount = SmsRetryManager.MAX_RETRIES)

        assertFalse(manager(sender).retryFailedSms(exhausted))
        verify(repo, never()).updateRecord(any())

        whenever(repo.getRecordById(7)).thenReturn(exhausted.copy(status = SmsStatus.PENDING.value))
        assertTrue(manager(sender).retryNow(exhausted))
    }
}
