package com.qrcommunication.smsforwarder.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun normalize_israeliForms() {
        listOf("+972501234567", "+972 50-123-4567", "972501234567", "00972501234567", "050 123 4567", "(050)1234567")
            .forEach { assertEquals(it, "0501234567", PhoneNumbers.normalize(it)) }
    }

    @Test
    fun normalize_foreignKeepsPlus() {
        assertEquals("+12025550123", PhoneNumbers.normalize("+1 202-555-0123"))
    }

    @Test
    fun normalize_shortCode() {
        assertEquals("8888", PhoneNumbers.normalize("8888"))
    }

    @Test
    fun isPhoneLike() {
        assertTrue(PhoneNumbers.isPhoneLike("050-1234567"))
        assertTrue(PhoneNumbers.isPhoneLike("+972 50 123 4567"))
        assertFalse(PhoneNumbers.isPhoneLike("Leumi"))
        assertFalse(PhoneNumbers.isPhoneLike("כללית"))
        assertFalse(PhoneNumbers.isPhoneLike("Bank123"))
        assertFalse(PhoneNumbers.isPhoneLike("12"))
    }

    @Test
    fun same() {
        assertTrue(PhoneNumbers.same("+972-52-1112233", "0521112233"))
        assertTrue(PhoneNumbers.same("Leumi", "LEUMI"))
        assertFalse(PhoneNumbers.same("0521112233", "0521112234"))
    }
}
