package com.qrcommunication.smsforwarder.service.sender

import android.util.Log
import com.qrcommunication.smsforwarder.data.preferences.PreferencesManager
import com.qrcommunication.smsforwarder.domain.rules.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional Telegram channel (off by default): sends the SMS text, verbatim, through the
 * user's own bot (token in Settings) to a chat id (the target address).
 */
@Singleton
class TelegramSender @Inject constructor(
    private val preferencesManager: PreferencesManager,
) : MessageSender {

    override val channel = Channel.TELEGRAM

    override suspend fun send(address: String, payload: MessagePayload): Unit = withContext(Dispatchers.IO) {
        val token = preferencesManager.telegramBotToken
        if (token.isBlank()) throw TelegramException("Telegram bot token is not set")
        val request = buildRequest(token, address, payload.content)

        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            Log.d(TAG, "sendMessage -> $code")
            if (code >= 400) {
                val error = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                val description = runCatching { JSONObject(error.orEmpty()).optString("description") }.getOrNull()
                throw TelegramException("Telegram HTTP $code: ${description ?: error.orEmpty().take(200)}")
            }
        } catch (e: TelegramException) {
            throw e
        } catch (e: Exception) {
            throw TelegramException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            connection.disconnect()
        }
    }

    data class Request(val url: String, val body: String)

    companion object {
        private const val TAG = "TelegramSender"

        fun buildRequest(token: String, chatId: String, text: String) = Request(
            url = "https://api.telegram.org/bot${token.trim()}/sendMessage",
            body = JSONObject().put("chat_id", chatId.trim()).put("text", text).toString(),
        )
    }
}

class TelegramException(message: String, cause: Throwable? = null) : Exception(message, cause)
