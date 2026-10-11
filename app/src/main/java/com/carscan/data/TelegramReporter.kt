package com.carscan.data

import com.carscan.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Отправка отчёта диагностики файлом в личный Telegram-бот.
 * Токен бота и chat id попадают в сборку из telegram.properties (локально) или секретов GitHub — см. docs/DEBUG_REPORT.md.
 */
class TelegramReporter(
    private val token: String = BuildConfig.TG_BOT_TOKEN,
    private val chatId: String = BuildConfig.TG_CHAT_ID,
) {
    val isConfigured: Boolean get() = token.isNotBlank() && chatId.isNotBlank()

    suspend fun sendDocument(fileName: String, content: String, caption: String) = withContext(Dispatchers.IO) {
        check(isConfigured) { "Бот не настроен в этой сборке" }
        val boundary = "----CarScan" + UUID.randomUUID().toString().replace("-", "")
        val connection = URL("https://api.telegram.org/bot$token/sendDocument").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.buffered().use { out ->
                fun part(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                part("chat_id", chatId)
                part("caption", caption.take(CAPTION_LIMIT))
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"$fileName\"\r\n" +
                        "Content-Type: text/plain; charset=utf-8\r\n\r\n").toByteArray(),
                )
                out.write(content.toByteArray(Charsets.UTF_8))
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (json?.optBoolean("ok") != true) {
                error("Telegram: ${json?.optString("description")?.takeIf { it.isNotBlank() } ?: "HTTP $code"}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val CAPTION_LIMIT = 1000
    }
}
