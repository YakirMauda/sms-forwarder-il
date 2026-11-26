package com.qrcommunication.smsforwarder.service

import android.util.Log
import com.qrcommunication.smsforwarder.data.local.entity.DestinationType
import com.qrcommunication.smsforwarder.data.local.entity.NotificationSeverity
import com.qrcommunication.smsforwarder.data.local.entity.NotificationType
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.AppNotificationRepository
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.model.IncomingSms
import com.qrcommunication.smsforwarder.domain.usecase.MatchForwardingRuleUseCase
import com.qrcommunication.smsforwarder.service.sender.DestinationDispatcher
import com.qrcommunication.smsforwarder.service.sender.MessagePayload
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed class ForwardOutcome {
    data class Skipped(val reason: String) : ForwardOutcome()
    data class Sent(val recordId: Long) : ForwardOutcome()
    data class Failed(val recordId: Long, val error: String, val retryScheduled: Boolean) : ForwardOutcome()
}

/**
 * Handles one incoming SMS end-to-end: filter -> match rule -> log -> send.
 *
 * Event-driven replacement for the old foreground service: it is called from
 * [com.qrcommunication.smsforwarder.receiver.SmsReceiver] inside `goAsync()`, so it must
 * finish quickly. A send that fails (or takes longer than [SEND_TIMEOUT_MS]) is marked
 * FAILED and handed to [RetryScheduler] (WorkManager) instead of blocking the receiver.
 */
@Singleton
class ForwardingProcessor @Inject constructor(
    private val smsRepository: SmsRepository,
    private val preferencesManager: PreferencesManager,
    private val loopProtection: LoopProtection,
    private val deduplicator: MessageDeduplicator,
    private val dispatcher: DestinationDispatcher,
    private val matchRule: MatchForwardingRuleUseCase,
    private val ruleRepository: ForwardingRuleRepository,
    private val notificationRepository: AppNotificationRepository,
    private val retryScheduler: RetryScheduler,
) {
    companion object {
        private const val TAG = "ForwardingProcessor"

        /** Must stay well below the ~10s budget Android gives a `goAsync()` receiver. */
        const val SEND_TIMEOUT_MS = 7_000L
    }

    suspend fun process(sms: IncomingSms): ForwardOutcome {
        if (!preferencesManager.isForwardingEnabled) return skip("forwarding disabled")
        if (!passesSimFilter(sms.simSlot)) return skip("SIM ${sms.simSlot} filtered out")
        if (!deduplicator.shouldProcess(sms.sender, sms.body, sms.timestamp)) return skip("duplicate")

        val matchedRule = matchRule(sms.sender, sms.body)
        val destinationType = matchedRule?.let { DestinationType.fromValue(it.destinationType) }
            ?: DestinationType.SMS
        val destination = matchedRule?.destination ?: preferencesManager.destinationNumber

        if (destination.isBlank()) return skip("no destination (no rule + no global number)")

        // Loop guard: only for SMS (a webhook cannot create an SMS back to us).
        if (destinationType == DestinationType.SMS && loopProtection.isLoopDetected(sms.sender, destination)) {
            return skip("loop: sender matches destination")
        }

        val recordId = smsRepository.insertRecord(
            SmsRecord(
                sender = sms.sender,
                content = sms.body,
                receivedAt = sms.timestamp,
                status = SmsStatus.PENDING.value,
                destination = destination,
                ruleId = matchedRule?.id,
            ),
        )
        val payload = MessagePayload(
            sender = sms.sender,
            content = sms.body,
            receivedAt = sms.timestamp,
            originalDestination = preferencesManager.destinationNumber.takeIf { it.isNotBlank() },
        )

        return try {
            withTimeout(SEND_TIMEOUT_MS) { dispatcher.dispatch(destinationType, destination, payload) }
            smsRepository.updateStatus(recordId, SmsStatus.SENT)
            preferencesManager.incrementSmsCount()
            matchedRule?.id?.let { ruleRepository.recordSuccess(it) }
            Log.i(TAG, "Forwarded via $destinationType (record $recordId)")
            ForwardOutcome.Sent(recordId)
        } catch (e: Exception) {
            // A real cancellation (not our own timeout) must propagate.
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val error = if (e is TimeoutCancellationException) "timeout" else e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Forward failed via $destinationType (record $recordId): $error", e)

            smsRepository.updateStatus(recordId, SmsStatus.FAILED, error)
            matchedRule?.id?.let { ruleRepository.recordFailure(it, error) }
            val retryScheduled = retryScheduler.scheduleRetry(recordId, attempt = 0)
            notifyFailure(matchedRule?.name, matchedRule?.id, destinationType, destination, error, recordId)
            ForwardOutcome.Failed(recordId, error, retryScheduled)
        }
    }

    private fun skip(reason: String): ForwardOutcome {
        Log.d(TAG, "Skipped: $reason")
        return ForwardOutcome.Skipped(reason)
    }

    private fun passesSimFilter(simSlot: Int): Boolean {
        val filter = preferencesManager.receivingSimSlot
        return filter < 0 || simSlot < 0 || simSlot == filter
    }

    private suspend fun notifyFailure(
        ruleName: String?,
        ruleId: Long?,
        type: DestinationType,
        destination: String,
        error: String,
        recordId: Long,
    ) {
        notificationRepository.notify(
            type = if (ruleName != null) NotificationType.RULE_ERROR else NotificationType.DESTINATION_UNREACHABLE,
            severity = NotificationSeverity.ERROR,
            title = if (ruleName != null) "Forwarding failed: '$ruleName'" else "Forwarding failed",
            message = "To ${type.name} $destination: $error",
            recordId = recordId,
            ruleId = ruleId,
        )
    }
}
