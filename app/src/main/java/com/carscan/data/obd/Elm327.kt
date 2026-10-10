package com.carscan.data.obd

import kotlinx.coroutines.delay
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

class ObdException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Канал до адаптера: Bluetooth SPP-сокет, а в тестах — эмулятор ELM327. */
interface ObdTransport : Closeable {
    val input: InputStream
    val output: OutputStream
}

/**
 * Ответ адаптера на одну команду.
 * [lines] — полезные строки без эха и служебных «SEARCHING...», [error] — код ошибки ELM327, если данных нет.
 */
data class ElmResponse(val command: String, val raw: String, val lines: List<String>, val error: String?) {
    val ok: Boolean get() = error == null
    val isNoData: Boolean get() = error == "NO DATA"
}

/** Сеанс с ELM327: команда + "\r", ответ читается до приглашения ">". */
class Elm327(private val input: InputStream, private val output: OutputStream) {

    suspend fun send(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ElmResponse {
        discardPending()
        output.write((command + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val raw = readUntilPrompt(timeoutMs)
            ?: throw ObdException("Адаптер не ответил на «$command» за ${timeoutMs / 1000.0} с")
        return parse(command, raw)
    }

    private suspend fun readUntilPrompt(timeoutMs: Long): String? {
        val text = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(256)
        while (System.currentTimeMillis() < deadline) {
            // available() вместо блокирующего read(): у Bluetooth-сокета нет таймаута чтения
            if (input.available() <= 0) {
                delay(POLL_INTERVAL_MS)
                continue
            }
            val count = input.read(buffer, 0, minOf(buffer.size, input.available()))
            for (i in 0 until count) {
                val ch = (buffer[i].toInt() and 0xFF).toChar()
                if (ch == '>') return text.toString()
                if (ch != '\u0000') text.append(ch)
            }
        }
        return null
    }

    /** Остатки предыдущего ответа (например, после таймаута) не должны попасть в следующий. */
    private fun discardPending() {
        while (input.available() > 0) input.read(ByteArray(input.available()))
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
        private const val POLL_INTERVAL_MS = 5L

        private val ERRORS = listOf(
            "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY", "BUS INIT",
            "FB ERROR", "DATA ERROR", "<DATA ERROR", "<RX ERROR", "BUFFER FULL", "STOPPED",
            "ACT ALERT", "LV RESET", "LP ALERT", "ERROR", "?",
        )

        fun parse(command: String, raw: String): ElmResponse {
            val lines = raw.split('\r', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.equals(command, ignoreCase = true) } // эхо, пока не выполнено ATE0
                .filterNot { it.startsWith("SEARCHING", ignoreCase = true) }
                .filterNot { it.startsWith("BUS INIT", ignoreCase = true) && !it.contains("ERROR", ignoreCase = true) }
            val hasData = lines.any { ObdParser.isDataLine(it) }
            val error = if (hasData) null else lines.firstNotNullOfOrNull { line ->
                ERRORS.firstOrNull { line.uppercase().startsWith(it) }
            }
            return ElmResponse(command, raw, lines, error)
        }
    }
}

/** Разбор строк ответа в байты сообщений (по одному на ответивший ЭБУ). Заголовки выключены (AT H0). */
object ObdParser {
    private val LENGTH_LINE = Regex("^[0-9A-F]{3}$")
    private val FRAME_LINE = Regex("^[0-9A-F]:[0-9A-F]*$")
    private val HEX_LINE = Regex("^[0-9A-F]+$")

    fun isDataLine(line: String): Boolean {
        val s = line.replace(" ", "").uppercase()
        return FRAME_LINE.matches(s) || (HEX_LINE.matches(s) && s.length % 2 == 0)
    }

    /**
     * Одиночные кадры — каждая строка отдельное сообщение («41 0C 1A F8» или «410C1AF8»).
     * Многокадровый ответ ISO-TP приходит как «013», «0: …», «1: …» — склеиваем по длине.
     */
    fun messages(lines: List<String>): List<IntArray> {
        val norm = lines.map { it.replace(" ", "").uppercase() }
        val result = mutableListOf<IntArray>()
        var i = 0
        while (i < norm.size) {
            val line = norm[i]
            if (LENGTH_LINE.matches(line) && i + 1 < norm.size && FRAME_LINE.matches(norm[i + 1])) {
                val length = line.toInt(16)
                val payload = StringBuilder()
                i++
                while (i < norm.size && FRAME_LINE.matches(norm[i])) {
                    payload.append(norm[i].substringAfter(':'))
                    i++
                }
                // Последний кадр дополняется до 8 байт — лишнее отрезаем по заявленной длине
                val bytes = hexBytes(payload.toString())
                result += if (bytes.size > length) bytes.copyOf(length) else bytes
                continue
            }
            if (HEX_LINE.matches(line) && line.length % 2 == 0) result += hexBytes(line)
            i++
        }
        return result
    }

    /** Данные PID режима 01 от первого ответившего ЭБУ (без «41 PID»). */
    fun mode01(lines: List<String>, pid: Int): IntArray? = mode01All(lines, pid).firstOrNull()

    /** Данные PID режима 01 от всех ЭБУ (нужно для масок поддерживаемых PID). */
    fun mode01All(lines: List<String>, pid: Int): List<IntArray> =
        messages(lines).filter { it.size >= 2 && it[0] == 0x41 && it[1] == pid }.map { it.copyOfRange(2, it.size) }

    /** Данные произвольного запроса: ответ начинается с (байт режима + 0x40) и остальных байт запроса. */
    fun responseData(lines: List<String>, request: IntArray): IntArray? {
        if (request.isEmpty()) return null
        val prefix = intArrayOf(request[0] + 0x40) + request.copyOfRange(1, request.size)
        return messages(lines)
            .firstOrNull { m -> m.size >= prefix.size && prefix.indices.all { m[it] == prefix[it] } }
            ?.let { it.copyOfRange(prefix.size, it.size) }
    }

    fun hexBytes(hex: String): IntArray {
        val clean = hex.replace(" ", "")
        return IntArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16) }
    }

    fun toHex(bytes: IntArray): String = bytes.joinToString(" ") { "%02X".format(it) }
}
