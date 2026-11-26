package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.service.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes a message to the sender for its channel.
 * SRP: no filtering / matching here, only dispatch.
 */
@Singleton
class DestinationDispatcher @Inject constructor(
    private val smsSender: SmsSender,
    private val webhookSender: WebhookSender,
) {
    suspend fun dispatch(channel: Channel, destination: String, payload: MessagePayload) {
        when (channel) {
            // Owner's choice: forward the text exactly as received, no sender/date header.
            Channel.SMS -> smsSender.sendSms(destination, payload.content)
            Channel.WEBHOOK -> webhookSender.send(destination, payload)
            Channel.TELEGRAM -> throw UnsupportedOperationException("Telegram channel is not set up yet")
        }
    }
}
