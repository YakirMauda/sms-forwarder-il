package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel

/**
 * One implementation per channel (SMS, Telegram, webhook). The dispatcher picks the one
 * for the target's channel, so adding a channel never touches existing senders.
 */
interface MessageSender {
    val channel: Channel

    /** @throws Exception when the message could not be handed off; the caller logs it and retries. */
    suspend fun send(address: String, payload: MessagePayload)
}
