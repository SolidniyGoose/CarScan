package com.carscan.data.obd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор «сырого» текста ELM327 в том виде, в каком он приходит из Bluetooth-сокета. */
class ElmParsingTest {

    private fun data(command: String, raw: String, pid: Int) =
        ObdParser.mode01(Elm327.parse(command, raw).lines, pid)

    @Test
    fun `ответ с пробелами и без`() {
        assertArrayEquals(intArrayOf(0x1A, 0xF8), data("010C", "41 0C 1A F8\r\r", 0x0C))
        assertArrayEquals(intArrayOf(0x1A, 0xF8), data("010C", "410C1AF8\r\r", 0x0C))
    }

    @Test
    fun `эхо команды и SEARCHING отбрасываются`() {
        val r = Elm327.parse("010D", "010D\rSEARCHING...\r41 0D 3C\r\r")
        assertTrue(r.ok)
        assertArrayEquals(intArrayOf(0x3C), ObdParser.mode01(r.lines, 0x0D))
    }

    @Test
    fun `несколько ЭБУ отвечают на 0100 - маски объединяются`() {
        val r = Elm327.parse("0100", "41 00 BE 3F A8 13\r41 00 98 18 80 11\r\r")
        val masks = ObdParser.mode01All(r.lines, 0x00)
        assertEquals(2, masks.size)
        val all = masks.flatMap { ObdDecode.supportedNumbers(0, it) }.toSet()
        assertTrue(0x0C in all && 0x1C in all)
    }

    @Test
    fun `ошибки адаптера распознаются`() {
        assertEquals("NO DATA", Elm327.parse("0146", "NO DATA\r\r").error)
        assertTrue(Elm327.parse("0146", "NO DATA\r\r").isNoData)
        assertEquals("UNABLE TO CONNECT", Elm327.parse("0100", "SEARCHING...\rUNABLE TO CONNECT\r\r").error)
        assertEquals("?", Elm327.parse("ATAT1", "?\r\r").error)
        assertEquals("CAN ERROR", Elm327.parse("0100", "CAN ERROR\r\r").error)
        assertEquals("BUS INIT", Elm327.parse("0100", "BUS INIT: ...ERROR\r\r").error)
    }

    @Test
    fun `AT-команды без данных не считаются ошибкой`() {
        assertTrue(Elm327.parse("ATE0", "ATE0\rOK\r\r").ok)
        assertTrue(Elm327.parse("ATZ", "\r\rELM327 v1.5\r\r").ok)
        assertEquals(listOf("14.2V"), Elm327.parse("ATRV", "14.2V\r\r").lines)
        assertTrue(Elm327.parse("0100", "BUS INIT: ...OK\r41 00 BE 1F A8 13\r\r").ok)
    }

    @Test
    fun `ответ другого PID не подставляется`() {
        assertNull(data("010C", "41 0D 3C\r\r", 0x0C))
    }

    @Test
    fun `многокадровый ответ CAN склеивается по длине`() {
        // 19 байт: 46 + две записи режима 06 по 9 байт; последний кадр дополнен нулями
        val raw = "013\r0: 46 A2 0B 24 00 00\r1: 00 00 FF FF A2 0C 24\r2: 00 05 00 00 FF FF 00\r\r"
        val messages = ObdParser.messages(Elm327.parse("06A2", raw).lines)
        assertEquals(1, messages.size)
        assertEquals(19, messages[0].size)
        val records = ObdDecode.monitorRecords(messages[0])
        assertEquals(listOf(0x0B, 0x0C), records.map { it.tid })
        assertEquals(5, records[1].value)
    }

    @Test
    fun `ответ команды производителя (UDS 22)`() {
        val r = Elm327.parse("221940", "62 19 40 7D\r\r")
        assertArrayEquals(intArrayOf(0x7D), ObdParser.responseData(r.lines, ObdParser.hexBytes("221940")))
        assertFalse(Elm327.parse("221940", "7F 22 31\r\r").lines.isEmpty())
        assertNull(ObdParser.responseData(Elm327.parse("221940", "7F 22 31\r\r").lines, ObdParser.hexBytes("221940")))
    }
}
