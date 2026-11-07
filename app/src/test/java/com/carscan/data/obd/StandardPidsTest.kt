package com.carscan.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Формулы SAE J1979 для списка «Все датчики». Байты — из отчёта с Outlander XL и примеров стандарта. */
class StandardPidsTest {

    private fun decode(pid: Int, hex: String): Double? = StandardPids.byPid.getValue(pid).decode(ObdParser.hexBytes(hex))

    @Test
    fun `значения из отчёта с машины`() {
        assertEquals(14.173, decode(0x42, "375D")!!, 1e-9)  // 4142375D → 14.17 V
        assertEquals(98.0, decode(0x33, "62")!!, 1e-9)       // 413362 → 98 кПа
        assertEquals(699.0, decode(0x0C, "0AEC")!!, 1e-9)    // 410C0AEC → 699 об/мин
        assertEquals(87.0, decode(0x05, "7F")!!, 1e-9)       // 41057F → 87 °C
        assertEquals(2.40, decode(0x10, "00F0")!!, 1e-9)     // 411000F0 → 2.40 г/с
    }

    @Test
    fun `формулы стандарта`() {
        assertEquals(6.0, decode(0x0E, "8C")!!, 1e-9)        // 140 / 2 − 64
        assertEquals(14.9, decode(0x11, "26")!!, 0.01)       // 38 · 100 / 255
        assertEquals(0.45, decode(0x14, "5A80")!!, 1e-9)     // 90 / 200 В
        assertEquals(-25.0, decode(0x32, "FF9C")!!, 1e-9)    // −100 / 4 Па, число со знаком
        assertEquals(1.0, decode(0x44, "8000")!!, 1e-9)      // λ = 1
        assertEquals(450.0, decode(0x3C, "1324")!!, 1e-9)    // 4900 / 10 − 40 °C
        assertEquals(200000.5, decode(0xA6, "001E8485")!!, 1e-9)
    }

    @Test
    fun `лямбда-зонды названы по банку и номеру`() {
        assertEquals("Лямбда-зонд Б1 Д1, напряжение", StandardPids.byPid.getValue(0x14).name)
        assertEquals("Лямбда-зонд Б1 Д2, напряжение", StandardPids.byPid.getValue(0x15).name)
        assertEquals("Лямбда-зонд Б2 Д1, напряжение", StandardPids.byPid.getValue(0x18).name)
    }

    @Test
    fun `коротких данных не хватает - значения нет`() {
        assertNull(decode(0x0C, "0A"))
        assertNull(decode(0xA6, "001E"))
    }

    @Test
    fun `номера PID не повторяются и форматирование с единицами`() {
        assertEquals(StandardPids.all.size, StandardPids.byPid.size)
        assertEquals("14.17 V", StandardPids.byPid.getValue(0x42).format(14.173))
        assertEquals("6.0°", StandardPids.byPid.getValue(0x0E).format(6.0))
    }
}
