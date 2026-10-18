package com.carscan.data

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import kotlin.random.Random

/** Загрузка обновления по нестабильной связи: локальный сервер рвёт соединение посреди файла. */
class UpdateDownloadTest {
    private val payload = Random(42).nextBytes(300_000)
    private val ranges = mutableListOf<String?>()
    private lateinit var server: HttpServer
    private lateinit var target: File

    /** [dropFirst] — первый ответ обрывается на трети; [supportRange] — сервер умеет докачку (206). */
    private fun start(dropFirst: Boolean, supportRange: Boolean) {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/app.apk") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            ranges += range
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()?.takeIf { supportRange } ?: 0
            if (from >= payload.size) {
                ex.sendResponseHeaders(416, -1) // как GitHub на запрос с конца файла
                ex.close()
                return@createContext
            }
            val body = payload.copyOfRange(from, payload.size)
            ex.sendResponseHeaders(if (from > 0) 206 else 200, body.size.toLong())
            if (dropFirst && ranges.size == 1) {
                ex.responseBody.write(body, 0, body.size / 3)
                ex.responseBody.flush()
                ex.close() // обрыв: заявлено больше, чем отправлено
            } else {
                ex.responseBody.use { it.write(body) }
            }
        }
        server.start()
    }

    private val url get() = "http://127.0.0.1:${server.address.port}/app.apk"

    @Before
    fun setUp() {
        target = File.createTempFile("carscan-update", ".apk").apply { delete() }
    }

    @After
    fun tearDown() {
        server.stop(0)
        target.delete()
    }

    @Test
    fun `обрыв посреди файла - докачка с того же места`() = runBlocking {
        start(dropFirst = true, supportRange = true)
        UpdateRepository.downloadWithResume(url, payload.size.toLong(), target, "test", {}, retryDelayMs = 10)
        assertArrayEquals(payload, target.readBytes())
        assertEquals(2, ranges.size)
        assertEquals("bytes=100000-", ranges[1])
    }

    @Test
    fun `сервер без докачки - файл скачивается заново целиком`() = runBlocking {
        start(dropFirst = true, supportRange = false)
        UpdateRepository.downloadWithResume(url, payload.size.toLong(), target, "test", {}, retryDelayMs = 10)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun `уже скачанный файл не качается повторно`() = runBlocking {
        start(dropFirst = false, supportRange = true)
        UpdateRepository.downloadWithResume(url, payload.size.toLong(), target, "test", {}, retryDelayMs = 10)
        // Повторное «Обновить» (например, после выдачи разрешения на установку): файл уже целый
        UpdateRepository.downloadWithResume(url, payload.size.toLong(), target, "test", {}, retryDelayMs = 10)
        assertEquals(listOf(null, "bytes=300000-"), ranges)
        assertArrayEquals(payload, target.readBytes())
    }
}
