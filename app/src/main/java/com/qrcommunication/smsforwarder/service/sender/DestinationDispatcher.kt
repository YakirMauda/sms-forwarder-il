package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a message to the [MessageSender] registered for its channel (Hilt multibinding).
 * SRP: no filtering / matching here, only dispatch.
 */
@Singleton
class DestinationDispatcher @Inject constructor(
    senders: Set<@JvmSuppressWildcards MessageSender>,
) {
    private val byChannel: Map<Channel, MessageSender> = senders.associateBy { it.channel }

    suspend fun dispatch(channel: Channel, destination: String, payload: MessagePayload) {
        val sender = byChannel[channel] ?: throw UnsupportedOperationException("No sender for channel $channel")
        sender.send(destination, payload)
    }
}
