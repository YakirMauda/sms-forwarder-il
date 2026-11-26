package com.qrcommunication.smsforwarder.service

import com.qrcommunication.smsforwarder.service.sender.TelegramSender
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SendingHelpersTest {

    @Test
    fun simChoice() {
        assertNull(SimChoice.slotFor(SimChoice.DEFAULT, incomingSimSlot = 1))
        assertEquals(0, SimChoice.slotFor(0, incomingSimSlot = 1))
        assertEquals(1, SimChoice.slotFor(SimChoice.SAME_AS_INCOMING, incomingSimSlot = 1))
        assertNull(SimChoice.slotFor(SimChoice.SAME_AS_INCOMING, incomingSimSlot = -1))
    }

    @Test
    fun smsErrors_describe() {
        assertEquals("SMS error 4 (no service)", SmsErrors.describe(4))
        assertEquals("SMS error 2 (radio off), radio code 38", SmsErrors.describe(2, 38))
        assertEquals("SMS error 99 (unknown)", SmsErrors.describe(99))
    }

    @Test
    fun telegramRequest_sendsTextVerbatim() {
        val request = TelegramSender.buildRequest(" 123:ABC ", " 4567 ", "קוד: 123456\nשורה 2")

        assertEquals("https://api.telegram.org/bot123:ABC/sendMessage", request.url)
        val body = JSONObject(request.body)
        assertEquals("4567", body.getString("chat_id"))
        assertEquals("קוד: 123456\nשורה 2", body.getString("text"))
    }
}
