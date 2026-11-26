package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.service.SimSelector
import com.qrcommunication.smsforwarder.service.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS channel. Owner's choice: the text is forwarded exactly as received (no header).
 * Which SIM sends it is decided by [SimSelector], kept separate from sending.
 */
@Singleton
class SmsMessageSender @Inject constructor(
    private val smsSender: SmsSender,
    private val simSelector: SimSelector,
) : MessageSender {

    override val channel = Channel.SMS

    override suspend fun send(address: String, payload: MessagePayload) {
        smsSender.sendSms(
            destination = address,
            message = payload.content,
            recordId = payload.recordId,
            subscriptionId = simSelector.subscriptionIdFor(payload.incomingSimSlot),
        )
    }
}
