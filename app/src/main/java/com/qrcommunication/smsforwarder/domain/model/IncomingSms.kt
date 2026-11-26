package com.qrcommunication.smsforwarder.domain.model

/**
 * An SMS as it arrived on the device (all multipart segments already joined).
 *
 * @param simSlot slot index the message arrived on, or -1 when unknown.
 */
data class IncomingSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val simSlot: Int = -1,
)
