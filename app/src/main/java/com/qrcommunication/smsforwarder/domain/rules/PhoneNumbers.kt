package com.qrcommunication.smsforwarder.domain.rules

/**
 * Israeli-aware phone number helpers (pure Kotlin).
 *
 * "+972 50-123-4567", "972501234567", "00972501234567" and "050 1234567" all
 * normalize to "0501234567". Non-Israeli numbers keep their digits (and leading +).
 */
object PhoneNumbers {

    private val PHONE_LIKE = Regex("""^\+?[0-9\s\-().]+$""")

    /** True for things like "050-1234567", "+972501234567" or short codes "8888"; false for "Leumi". */
    fun isPhoneLike(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.isNotEmpty() && PHONE_LIKE.matches(trimmed) && trimmed.count { it.isDigit() } >= 3
    }

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        val hasPlus = trimmed.startsWith("+")
        return when {
            hasPlus && digits.startsWith("972") -> "0" + digits.removePrefix("972")
            digits.startsWith("00972") -> "0" + digits.removePrefix("00972")
            digits.startsWith("972") && digits.length >= 11 -> "0" + digits.removePrefix("972")
            hasPlus -> "+$digits"
            else -> digits
        }
    }

    /** Same number, ignoring formatting and +972/0 prefixes. Non-numeric values compare case-insensitively. */
    fun same(a: String, b: String): Boolean =
        if (isPhoneLike(a) && isPhoneLike(b)) normalize(a) == normalize(b) else a.trim().equals(b.trim(), ignoreCase = true)
}
