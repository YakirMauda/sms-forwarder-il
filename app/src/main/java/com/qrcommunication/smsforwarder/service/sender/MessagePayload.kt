package com.qrcommunication.smsforwarder.service.sender

/**
 * Payload neutre passe au dispatcher : independant du canal de destination.
 */
data class MessagePayload(
    val sender: String,
    val content: String,
    val receivedAt: Long,
    val sourceLabel: String? = null,
    val originalDestination: String? = null,
    /** History record this send belongs to (0 = none, e.g. a test SMS); used for SMS status callbacks. */
    val recordId: Long = 0L,
    /** SIM slot the original SMS arrived on (-1 = unknown); used by "reply from the same SIM". */
    val incomingSimSlot: Int = -1,
)
