package com.carscan.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Эталонные значения: формулы SAE J1979 и примеры из таблицы OBD-II PIDs
 * (en.wikipedia.org/wiki/OBD-II_PIDs), те же расчёты делают Car Scanner и python-OBD.
 */
class ObdDecodeTest {

    private fun bytes(hex: String) = ObdParser.hexBytes(hex)

    @Test
    fun `обороты 41 0C 1A F8 = 1726`() {
        assertEquals(1726.0, ObdDecode.rpm(bytes("1AF8"))!!, 0.0)
        assertEquals(0.0, ObdDecode.rpm(bytes("0000"))!!, 0.0)
        assertEquals(16383.75, ObdDecode.rpm(bytes("FFFF"))!!, 0.0) // максимум по стандарту
    }

    @Test
    fun `температуры со смещением минус 40`() {
        assertEquals(83, ObdDecode.temperature(bytes("7B")))   // 123 - 40
        assertEquals(-40, ObdDecode.temperature(bytes("00")))
        assertEquals(215, ObdDecode.temperature(bytes("FF")))
    }

    @Test
    fun `скорость, давление, нагрузка, уровень топлива`() {
        assertEquals(60, ObdDecode.speed(bytes("3C")))
        assertEquals(101, ObdDecode.intakePressure(bytes("65")))
        assertEquals(100.0, ObdDecode.engineLoad(bytes("FF"))!!, 1e-9)
        assertEquals(50.2, ObdDecode.engineLoad(bytes("80"))!!, 0.01)
        assertEquals(50.2, ObdDecode.fuelLevel(bytes("80"))!!, 0.01)
    }

    @Test
    fun `топливные коррекции от -100 до +99_2 процента`() {
        assertEquals(0.0, ObdDecode.fuelTrim(bytes("80"))!!, 1e-9)
        assertEquals(-100.0, ObdDecode.fuelTrim(bytes("00"))!!, 1e-9)
        assertEquals(99.22, ObdDecode.fuelTrim(bytes("FF"))!!, 0.01)
    }

    @Test
    fun `MAF, напряжение блока, расход 5E`() {
        assertEquals(5.0, ObdDecode.maf(bytes("01F4"))!!, 1e-9)           // 500 / 100
        assertEquals(14.0, ObdDecode.moduleVoltage(bytes("36B0"))!!, 1e-9) // 14000 / 1000
        assertEquals(10.0, ObdDecode.fuelRate(bytes("00C8"))!!, 1e-9)      // 200 / 20
    }

    @Test
    fun `напряжение AT RV`() {
        assertEquals(14.2, ObdDecode.adapterVoltage("14.2V")!!, 1e-9)
        assertEquals(12.6, ObdDecode.adapterVoltage(" 12.6 V ")!!, 1e-9)
        assertNull(ObdDecode.adapterVoltage("?"))
    }

    @Test
    fun `не хватает байтов — null, а не мусор`() {
        assertNull(ObdDecode.rpm(bytes("1A")))
        assertNull(ObdDecode.maf(intArrayOf()))
    }

    @Test
    fun `маска поддерживаемых PID BE 1F A8 13 из примера Википедии`() {
        val pids = ObdDecode.supportedNumbers(0x00, bytes("BE1FA813"))
        val expected = setOf(0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x13, 0x15, 0x1C, 0x1F, 0x20)
        assertEquals(expected, pids)
    }

    @Test
    fun `PID 01 - Check Engine, ошибки и готовность мониторов`() {
        // A=83: MIL горит, 3 ошибки. B=07: бензин, 3 непрерывных монитора завершены.
        // C=65: катализатор, EVAP, датчик O2, подогрев O2. D=04: EVAP не завершён.
        val status = ObdDecode.monitorStatus(bytes("83076504"))!!
        assertTrue(status.milOn)
        assertEquals(3, status.dtcCount)
        assertFalse(status.compressionIgnition)
        val readiness = status.readiness.associate { it.name to it.passed }
        assertEquals(
            mapOf(
                "Пропуски зажигания" to true, "Топливная система" to true, "Компоненты" to true,
                "Катализатор" to true, "Система EVAP" to false, "Датчик кислорода" to true, "Подогрев датчика O₂" to true,
            ),
            readiness,
        )
    }

    @Test
    fun `PID 01 - дизель распознаётся по биту 3 байта B`() {
        val status = ObdDecode.monitorStatus(bytes("000F4000"))!!
        assertTrue(status.compressionIgnition)
        assertFalse(status.milOn)
        assertTrue(status.readiness.any { it.name == "Сажевый фильтр" && it.passed })
    }

    @Test
    fun `расход бензина по MAF`() {
        // 10 г/с воздуха / 14.7 / 745 г/л * 3600 = 3.287 л/ч
        assertEquals(3.287, ObdDecode.fuelRateFromMaf(10.0, null, null), 0.001)
        // +5 % коррекции — ЭБУ льёт на 5 % больше
        assertEquals(3.287 * 1.05, ObdDecode.fuelRateFromMaf(10.0, 3.0, 2.0), 0.001)
    }

    @Test
    fun `расход воздуха по MAP для машин без датчика MAF`() {
        // 2.0 л, 3000 об/мин, полный газ (100 кПа), 25 °C, КН 85 %: ≈ 49.7 г/с — типично для 2-литрового мотора
        assertEquals(49.66, ObdDecode.mafFromMap(100.0, 25.0, 3000.0, 2.0, 0.85), 0.05)
        // Холостой ход 1.6 л: 30 кПа, 800 об/мин → ≈ 3.2 г/с
        assertEquals(3.18, ObdDecode.mafFromMap(30.0, 25.0, 800.0, 1.6, 0.85), 0.02)
    }

    @Test
    fun `режим 06 - записи по 9 байт`() {
        // MID A2 (цилиндр 1): TID 0B = 0 (среднее за 10 циклов), TID 0C = 5 (текущий цикл), пределы 0…65535
        val record1 = "A2" + "0B" + "24" + "0000" + "0000" + "FFFF"
        val record2 = "A2" + "0C" + "24" + "0005" + "0000" + "FFFF"
        val records = ObdDecode.monitorRecords(bytes("46$record1$record2"))
        assertEquals(2, records.size)
        assertEquals(MonitorRecord(0xA2, 0x0B, 0x24, value = 0, min = 0, max = 65535), records[0])
        assertEquals(MonitorRecord(0xA2, 0x0C, 0x24, value = 5, min = 0, max = 65535), records[1])
    }
}
