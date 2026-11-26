package com.qrcommunication.smsforwarder.service.sender

import com.qrcommunication.smsforwarder.domain.rules.Channel
import com.qrcommunication.smsforwarder.service.SmsSender
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class DestinationDispatcherTest {

    private val smsSender: SmsSender = mock()
    private val webhookSender: WebhookSender = mock()
    private lateinit var dispatcher: DestinationDispatcher

    @Before
    fun setUp() {
        dispatcher = DestinationDispatcher(smsSender, webhookSender)
    }

    private val payload = MessagePayload(
        sender = "+33612345678",
        content = "Hello",
        receivedAt = 1709564400000L,
    )

    @Test
    fun dispatch_sms_sendsContentVerbatim() = runTest {
        dispatcher.dispatch(Channel.SMS, "+33699999999", payload)

        verify(smsSender).sendSms("+33699999999", "Hello")
        verifyNoInteractions(webhookSender)
    }

    @Test
    fun dispatch_sms_hebrewMultilineContent_isUnchanged() = runTest {
        val text = "קוד האימות שלך הוא: 123456\nאין למסור את הקוד לאיש"
        dispatcher.dispatch(Channel.SMS, "0501234567", payload.copy(sender = "Leumi", content = text))

        verify(smsSender).sendSms("0501234567", text)
    }

    @Test
    fun dispatch_webhook_callsWebhookSender() = runTest {
        val url = "https://example.com/webhook"
        dispatcher.dispatch(Channel.WEBHOOK, url, payload)

        verify(webhookSender).send(eq(url), eq(payload))
        verifyNoInteractions(smsSender)
    }
}
