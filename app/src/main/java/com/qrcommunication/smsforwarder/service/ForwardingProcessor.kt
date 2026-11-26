package com.qrcommunication.smsforwarder.service

import android.util.Log
import com.qrcommunication.smsforwarder.data.local.entity.NotificationSeverity
import com.qrcommunication.smsforwarder.data.local.entity.NotificationType
import com.qrcommunication.smsforwarder.data.local.entity.SmsRecord
import com.qrcommunication.smsforwarder.data.local.entity.SmsStatus
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.data.repository.AppNotificationRepository
import com.qrcommunication.smsforwarder.data.repository.ForwardingRuleRepository
import com.qrcommunication.smsforwarder.data.repository.SmsRepository
import com.qrcommunication.smsforwarder.domain.model.IncomingSms
import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.domain.rules.ResolvedTarget
import com.qrcommunication.smsforwarder.domain.usecase.ResolveTargetsUseCase
import com.qrcommunication.smsforwarder.service.sender.DestinationDispatcher
import com.qrcommunication.smsforwarder.service.sender.MessagePayload
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

sealed class TargetResult {
    abstract val recordId: Long

    data class Sent(override val recordId: Long) : TargetResult()
    data class Failed(override val recordId: Long, val error: String, val retryScheduled: Boolean) : TargetResult()
}

sealed class ForwardOutcome {
    data class Skipped(val reason: String) : ForwardOutcome()
    data class Forwarded(val results: List<TargetResult>) : ForwardOutcome()
}

/**
 * Handles one incoming SMS end-to-end: filter -> rule engine -> per target: log + send.
 *
 * Called from [com.qrcommunication.smsforwarder.receiver.SmsReceiver] inside `goAsync()`,
 * so it must finish quickly. Each send is capped at [SEND_TIMEOUT_MS]; a failed or slow
 * send is marked FAILED and handed to [RetryScheduler] (WorkManager).
 */
@Singleton
class ForwardingProcessor @Inject constructor(
    private val smsRepository: SmsRepository,
    private val preferencesManager: PreferencesManager,
    private val loopProtection: LoopProtection,
    private val deduplicator: MessageDeduplicator,
    private val dispatcher: DestinationDispatcher,
    private val resolveTargets: ResolveTargetsUseCase,
    private val ruleRepository: ForwardingRuleRepository,
    private val notificationRepository: AppNotificationRepository,
    private val retryScheduler: RetryScheduler,
) {
    companion object {
        private const val TAG = "ForwardingProcessor"

        /** Total send budget; must stay well below the ~10s Android gives a `goAsync()` receiver. */
        const val SEND_TIMEOUT_MS = 7_000L
    }

    suspend fun process(sms: IncomingSms): ForwardOutcome {
        if (!preferencesManager.isForwardingEnabled) return skip("forwarding disabled")
        if (!passesSimFilter(sms.simSlot)) return skip("SIM ${sms.simSlot} filtered out")
        if (!deduplicator.shouldProcess(sms.sender, sms.body, sms.timestamp)) return skip("duplicate")

        // The rule engine already drops SMS targets equal to the sender; LoopProtection also
        // catches a target that is this phone's own SIM number.
        val targets = resolveTargets(sms.sender, sms.body).filterNot {
            it.target.channel == Channel.SMS && loopProtection.isLoopDetected(sms.sender, it.target.address)
        }
        if (targets.isEmpty()) return skip("no matching rule / target")

        val payload = MessagePayload(
            sender = sms.sender,
            content = sms.body,
            receivedAt = sms.timestamp,
            originalDestination = preferencesManager.destinationNumber.takeIf { it.isNotBlank() },
        )
        val perTargetTimeout = SEND_TIMEOUT_MS / targets.size
        return ForwardOutcome.Forwarded(targets.map { send(sms, it, payload, perTargetTimeout) })
    }

    private suspend fun send(sms: IncomingSms, resolved: ResolvedTarget, payload: MessagePayload, timeoutMs: Long): TargetResult {
        val (target, ruleId) = resolved
        val recordId = smsRepository.insertRecord(
            SmsRecord(
                sender = sms.sender,
                content = sms.body,
                receivedAt = sms.timestamp,
                status = SmsStatus.PENDING.value,
                destination = target.address,
                ruleId = ruleId,
                channel = target.channel.name,
            ),
        )
        return try {
            withTimeout(timeoutMs) { dispatcher.dispatch(target.channel, target.address, payload) }
            smsRepository.updateStatus(recordId, SmsStatus.SENT)
            preferencesManager.incrementSmsCount()
            ruleId?.let { ruleRepository.recordSuccess(it) }
            Log.i(TAG, "Forwarded via ${target.channel} (record $recordId)")
            TargetResult.Sent(recordId)
        } catch (e: Exception) {
            // A real cancellation (not our own timeout) must propagate.
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val error = if (e is TimeoutCancellationException) "timeout" else e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Forward failed via ${target.channel} (record $recordId): $error", e)

            smsRepository.updateStatus(recordId, SmsStatus.FAILED, error)
            ruleId?.let { ruleRepository.recordFailure(it, error) }
            val retryScheduled = retryScheduler.scheduleRetry(recordId, attempt = 0)
            notifyFailure(ruleId, target.channel, target.address, error, recordId)
            TargetResult.Failed(recordId, error, retryScheduled)
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

    private suspend fun notifyFailure(ruleId: Long?, channel: Channel, destination: String, error: String, recordId: Long) {
        notificationRepository.notify(
            type = if (ruleId != null) NotificationType.RULE_ERROR else NotificationType.DESTINATION_UNREACHABLE,
            severity = NotificationSeverity.ERROR,
            title = "Forwarding failed",
            message = "To ${channel.name} $destination: $error",
            recordId = recordId,
            ruleId = ruleId,
        )
    }
}
