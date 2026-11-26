package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.AppNotificationRepository
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.model.IncomingSms
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.ResolvedTarget
import com.qrcommunication.smsforwarder.domain.rules.Target
import com.qrcommunication.smsforwarder.domain.usecase.ResolveTargetsUseCase
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ForwardingProcessorTest {

    private val smsRepository: SmsRepository = mock()
    private val preferences: PreferencesManager = mock()
    private val loopProtection: LoopProtection = mock()
    private val dispatcher: DestinationDispatcher = mock()
    private val resolveTargets: ResolveTargetsUseCase = mock()
    private val ruleRepository: ForwardingRuleRepository = mock()
    private val notificationRepository: AppNotificationRepository = mock()
    private val retryScheduler: RetryScheduler = mock()

    private lateinit var processor: ForwardingProcessor

    private val sms = IncomingSms(sender = "Leumi", body = "קוד האימות שלך: 123456", timestamp = 1_000L)
    private val me = ResolvedTarget(Target(Channel.SMS, "0501234567"), ruleId = 7L)

    @Before
    fun setUp() = runTest {
        whenever(preferences.isForwardingEnabled).thenReturn(true)
        whenever(preferences.receivingSimSlot).thenReturn(-1)
        whenever(preferences.destinationNumber).thenReturn("")
        whenever(smsRepository.insertRecord(any())).thenReturn(42L)
        whenever(resolveTargets(any(), any())).thenReturn(listOf(me))
        whenever(
            notificationRepository.notify(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()),
        ).thenReturn(1L)

        processor = ForwardingProcessor(
            smsRepository = smsRepository,
            preferencesManager = preferences,
            loopProtection = loopProtection,
            deduplicator = MessageDeduplicator(),
            dispatcher = dispatcher,
            resolveTargets = resolveTargets,
            ruleRepository = ruleRepository,
            notificationRepository = notificationRepository,
            retryScheduler = retryScheduler,
        )
    }

    @Test
    fun disabled_skipsWithoutTouchingDb() = runTest {
        whenever(preferences.isForwardingEnabled).thenReturn(false)

        assertTrue(processor.process(sms) is ForwardOutcome.Skipped)
        verify(smsRepository, never()).insertRecord(any())
    }

    @Test
    fun matchedTarget_isSentVerbatim_loggedAndCounted() = runTest {
        val outcome = processor.process(sms)

        assertEquals(ForwardOutcome.Forwarded(listOf(TargetResult.Sent(42L))), outcome)
        val record = argumentCaptor<SmsRecord>()
        verify(smsRepository).insertRecord(record.capture())
        assertEquals("0501234567", record.firstValue.destination)
        assertEquals(7L, record.firstValue.ruleId)
        assertEquals("SMS", record.firstValue.channel)
        verify(dispatcher).dispatch(eq(Channel.SMS), eq("0501234567"), any())
        verify(smsRepository).updateStatus(42L, SmsStatus.SENT, null)
        verify(ruleRepository).recordSuccess(7L)
    }

    @Test
    fun multipleTargets_eachGetsOwnRecordAndSend() = runTest {
        val mom = ResolvedTarget(Target(Channel.SMS, "0527654321"), ruleId = 7L)
        whenever(resolveTargets(any(), any())).thenReturn(listOf(me, mom))

        val outcome = processor.process(sms) as ForwardOutcome.Forwarded

        assertEquals(2, outcome.results.size)
        verify(smsRepository, times(2)).insertRecord(any())
        verify(dispatcher).dispatch(eq(Channel.SMS), eq("0527654321"), any())
    }

    @Test
    fun noTargets_skips() = runTest {
        whenever(resolveTargets(any(), any())).thenReturn(emptyList())

        assertTrue(processor.process(sms) is ForwardOutcome.Skipped)
        verify(smsRepository, never()).insertRecord(any())
    }

    @Test
    fun targetIsOwnSimNumber_isDropped() = runTest {
        whenever(loopProtection.isLoopDetected(any(), any())).thenReturn(true)

        assertTrue(processor.process(sms) is ForwardOutcome.Skipped)
        verify(dispatcher, never()).dispatch(any(), any(), any())
    }

    @Test
    fun otherSimFiltered_skips() = runTest {
        whenever(preferences.receivingSimSlot).thenReturn(0)

        assertTrue(processor.process(sms.copy(simSlot = 1)) is ForwardOutcome.Skipped)
    }

    @Test
    fun sameBroadcastTwice_secondIsDuplicate() = runTest {
        processor.process(sms)

        assertEquals(ForwardOutcome.Skipped("duplicate"), processor.process(sms))
    }

    @Test
    fun sameTextAtDifferentTime_isForwardedAgain() = runTest {
        processor.process(sms)

        assertTrue(processor.process(sms.copy(timestamp = 99_000L)) is ForwardOutcome.Forwarded)
    }

    @Test
    fun sendFails_marksFailed_andSchedulesRetry() = runTest {
        whenever(retryScheduler.scheduleRetry(42L, 0)).thenReturn(true)
        dispatcher.stub {
            onBlocking { dispatch(any(), any(), any()) } doThrow IllegalStateException("radio off")
        }

        val outcome = processor.process(sms)

        assertEquals(
            ForwardOutcome.Forwarded(listOf(TargetResult.Failed(42L, "radio off", retryScheduled = true))),
            outcome,
        )
        verify(smsRepository).updateStatus(42L, SmsStatus.FAILED, "radio off")
        verify(ruleRepository).recordFailure(7L, "radio off")
    }

    @Test
    fun sendHangs_timesOut_andSchedulesRetry() = runTest {
        whenever(retryScheduler.scheduleRetry(42L, 0)).thenReturn(true)
        dispatcher.stub {
            onBlocking { dispatch(any(), any(), any()) } doSuspendableAnswer { awaitCancellation() }
        }

        val outcome = processor.process(sms)

        assertEquals(
            ForwardOutcome.Forwarded(listOf(TargetResult.Failed(42L, "timeout", retryScheduled = true))),
            outcome,
        )
        verify(smsRepository).updateStatus(42L, SmsStatus.FAILED, "timeout")
    }
}
