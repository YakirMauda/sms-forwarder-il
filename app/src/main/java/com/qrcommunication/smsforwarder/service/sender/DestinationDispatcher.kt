package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.data.local.entity.DestinationType
import com.qrcommunication.smsforwarder.service.SmsSender
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Route le message vers le sender adapte au type de destination.
 * SRP : aucune logique de filtrage / matching, uniquement la dispatch.
 */
@Singleton
class DestinationDispatcher @Inject constructor(
    private val smsSender: SmsSender,
    private val webhookSender: WebhookSender,
) {
    suspend fun dispatch(
        type: DestinationType,
        destination: String,
        payload: MessagePayload,
    ) {
        when (type) {
            // Owner's choice: forward the text exactly as received, no sender/date header.
            DestinationType.SMS -> smsSender.sendSms(destination, payload.content)
            DestinationType.WEBHOOK -> webhookSender.send(destination, payload)
        }
    }
}
