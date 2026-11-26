package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.data.local.entity.DestinationType
import com.qrcommunication.smsforwarder.data.local.entity.ForwardingRule
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.AppNotificationRepository
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.model.IncomingSms
import com.qrcommunication.smsforwarder.domain.usecase.MatchForwardingRuleUseCase
import com.qrcommunication.smsforwarder.service.sender.DestinationDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ForwardingProcessorTest {

    private val smsRepository: SmsRepository = mock()
    private val preferences: PreferencesManager = mock()
    private val loopProtection: LoopProtection = mock()
    private val dispatcher: DestinationDispatcher = mock()
    private val matchRule: MatchForwardingRuleUseCase = mock()
    private val ruleRepository: ForwardingRuleRepository = mock()
    private val notificationRepository: AppNotificationRepository = mock()
    private val retryScheduler: RetryScheduler = mock()

    private lateinit var processor: ForwardingProcessor

    private val sms = IncomingSms(sender = "Leumi", body = "קוד האימות שלך: 123456", timestamp = 1_000L)

    @Before
    fun setUp() = runTest {
        whenever(preferences.isForwardingEnabled).thenReturn(true)
        whenever(preferences.receivingSimSlot).thenReturn(-1)
        whenever(preferences.destinationNumber).thenReturn("0501234567")
        whenever(smsRepository.insertRecord(any())).thenReturn(42L)
        whenever(
            notificationRepository.notify(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(1L)

        processor = ForwardingProcessor(
            smsRepository = smsRepository,
            preferencesManager = preferences,
            loopProtection = loopProtection,
            deduplicator = MessageDeduplicator(),
            dispatcher = dispatcher,
            matchRule = matchRule,
            ruleRepository = ruleRepository,
            notificationRepository = notificationRepository,
            retryScheduler = retryScheduler,
        )
    }

    @Test
    fun disabled_skipsWithoutTouchingDb() = runTest {
        whenever(preferences.isForwardingEnabled).thenReturn(false)

        val outcome = processor.process(sms)

        assertTrue(outcome is ForwardOutcome.Skipped)
        verify(smsRepository, never()).insertRecord(any())
    }

    @Test
    fun noRule_sendsToGlobalNumber_andMarksSent() = runTest {
        val outcome = processor.process(sms)

        assertEquals(ForwardOutcome.Sent(42L), outcome)
        verify(dispatcher).dispatch(eq(DestinationType.SMS), eq("0501234567"), any())
        verify(smsRepository).updateStatus(42L, SmsStatus.SENT, null)
        verify(retryScheduler, never()).scheduleRetry(any(), any())
    }

    @Test
    fun matchedRule_usesRuleDestination_andRecordsSuccess() = runTest {
        whenever(matchRule(any(), any())).thenReturn(rule(id = 7L, destination = "0529999999"))

        processor.process(sms)

        val recordCaptor = argumentCaptor<SmsRecord>()
        verify(smsRepository).insertRecord(recordCaptor.capture())
        assertEquals("0529999999", recordCaptor.firstValue.destination)
        assertEquals(7L, recordCaptor.firstValue.ruleId)
        verify(dispatcher).dispatch(eq(DestinationType.SMS), eq("0529999999"), any())
        verify(ruleRepository).recordSuccess(7L)
    }

    @Test
    fun noDestination_skips() = runTest {
        whenever(preferences.destinationNumber).thenReturn("")

        val outcome = processor.process(sms)

        assertTrue(outcome is ForwardOutcome.Skipped)
        verify(smsRepository, never()).insertRecord(any())
    }

    @Test
    fun loopDetected_skips() = runTest {
        whenever(loopProtection.isLoopDetected(any(), any())).thenReturn(true)

        val outcome = processor.process(sms)

        assertTrue(outcome is ForwardOutcome.Skipped)
        verify(dispatcher, never()).dispatch(any(), any(), any())
    }

    @Test
    fun otherSimFiltered_skips() = runTest {
        whenever(preferences.receivingSimSlot).thenReturn(0)

        val outcome = processor.process(sms.copy(simSlot = 1))

        assertTrue(outcome is ForwardOutcome.Skipped)
    }

    @Test
    fun sameBroadcastTwice_secondIsDuplicate() = runTest {
        processor.process(sms)
        val second = processor.process(sms)

        assertEquals(ForwardOutcome.Skipped("duplicate"), second)
    }

    @Test
    fun sameTextAtDifferentTime_isForwardedAgain() = runTest {
        processor.process(sms)
        val second = processor.process(sms.copy(timestamp = 99_000L))

        assertTrue(second is ForwardOutcome.Sent)
    }

    @Test
    fun sendFails_marksFailed_andSchedulesRetry() = runTest {
        whenever(retryScheduler.scheduleRetry(42L, 0)).thenReturn(true)
        dispatcher.stub {
            onBlocking { dispatch(any(), any(), any()) } doThrow IllegalStateException("radio off")
        }

        val outcome = processor.process(sms)

        assertEquals(ForwardOutcome.Failed(42L, "radio off", retryScheduled = true), outcome)
        verify(smsRepository).updateStatus(42L, SmsStatus.FAILED, "radio off")
        verify(retryScheduler).scheduleRetry(42L, 0)
    }

    @Test
    fun sendHangs_timesOut_andSchedulesRetry() = runTest {
        whenever(retryScheduler.scheduleRetry(42L, 0)).thenReturn(true)
        dispatcher.stub {
            onBlocking { dispatch(any(), any(), any()) } doSuspendableAnswer { awaitCancellation() }
        }

        val outcome = processor.process(sms)

        assertEquals(ForwardOutcome.Failed(42L, "timeout", retryScheduled = true), outcome)
        verify(smsRepository).updateStatus(42L, SmsStatus.FAILED, "timeout")
    }

    private fun rule(id: Long, destination: String) = ForwardingRule(
        id = id,
        name = "bank",
        destination = destination,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
