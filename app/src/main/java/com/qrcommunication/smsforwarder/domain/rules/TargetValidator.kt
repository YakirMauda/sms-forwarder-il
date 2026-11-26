package com.qrcommunication.smsforwarder.domain.rules

/**
 * Catches targets that can never work before they are saved (a typo would otherwise fail
 * and be retried several times for nothing).
 */
object TargetValidator {

    private val TELEGRAM_CHAT = Regex("""^-?\d{3,}$|^@[A-Za-z][A-Za-z0-9_]{4,}$""")
    private val WEB_URL = Regex("""^https?://[^\s/]+\.[^\s]+$""", RegexOption.IGNORE_CASE)

    fun isValid(target: Target): Boolean {
        val address = target.address.trim()
        return when (target.channel) {
            Channel.SMS -> PhoneNumbers.isPhoneLike(address) && address.count { it.isDigit() } >= 3
            Channel.TELEGRAM -> TELEGRAM_CHAT.matches(address)
            Channel.WEBHOOK -> WEB_URL.matches(address)
        }
    }

    /** "052-123 45 67" / "(052)1234567" -> "0521234567"; keeps a leading "+". Sent to SmsManager. */
    fun cleanSmsNumber(address: String): String {
        val trimmed = address.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }
}
