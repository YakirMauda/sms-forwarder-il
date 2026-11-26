package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationDispatcherTest {

    private class FakeSender(override val channel: Channel) : MessageSender {
        val sent = mutableListOf<Pair<String, MessagePayload>>()
        override suspend fun send(address: String, payload: MessagePayload) {
            sent += address to payload
        }
    }

    private val sms = FakeSender(Channel.SMS)
    private val webhook = FakeSender(Channel.WEBHOOK)
    private val dispatcher = DestinationDispatcher(setOf(sms, webhook))
    private val payload = MessagePayload(sender = "Leumi", content = "קוד: 123456", receivedAt = 1L, recordId = 9)

    @Test
    fun routesToSenderOfTheChannel() = runTest {
        dispatcher.dispatch(Channel.SMS, "0501234567", payload)
        dispatcher.dispatch(Channel.WEBHOOK, "https://example.com/h", payload)

        assertEquals(listOf("0501234567" to payload), sms.sent)
        assertEquals(listOf("https://example.com/h" to payload), webhook.sent)
    }

    @Test
    fun missingChannel_throws() = runTest {
        val error = runCatching { dispatcher.dispatch(Channel.TELEGRAM, "123", payload) }.exceptionOrNull()
        assertTrue(error is UnsupportedOperationException)
    }
}
