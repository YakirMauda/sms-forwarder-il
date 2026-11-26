package com.qrcommunication.smsforwarder.service

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guards against the same SMS broadcast being handled twice (e.g. redelivered by the OS).
 * Key = sender + body + timestamp, kept for [EXPIRY_MS]. The timestamp matters: two
 * identical texts received at different times (same bank alert twice) are both forwarded.
 */
@Singleton
class MessageDeduplicator @Inject constructor() {

    companion object {
        private const val TAG = "MessageDeduplicator"
        private const val MAX_CACHE_SIZE = 500
        private const val EXPIRY_MS = 60_000L
    }

    private val processedMessages = ConcurrentHashMap<String, Long>()

    /**
     * Retourne true si le message n'a PAS encore ete traite (donc doit etre forwarde).
     * Retourne false si c'est un doublon.
     */
    fun shouldProcess(sender: String, content: String, timestampMs: Long): Boolean {
        val now = System.currentTimeMillis()
        cleanup(now)
        val hash = generateHash(sender, content, timestampMs)

        val existing = processedMessages.putIfAbsent(hash, now)
        if (existing != null) {
            Log.d(TAG, "Duplicate message detected from $sender, skipping")
            return false
        }

        Log.d(TAG, "New message from $sender, will process")
        return true
    }

    private fun generateHash(sender: String, content: String, timestampMs: Long): String {
        return "$sender|$timestampMs|${content.hashCode()}"
    }

    private fun cleanup(now: Long) {
        processedMessages.entries.removeIf { now - it.value > EXPIRY_MS }
        if (processedMessages.size > MAX_CACHE_SIZE) processedMessages.clear()
    }
}
