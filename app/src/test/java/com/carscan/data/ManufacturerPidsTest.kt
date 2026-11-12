package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Команды Mitsubishi из профилей Torque владельцев Outlander: формулы и выбор по автомобилю. */
class ManufacturerPidsTest {

    private fun bytes(hex: String) = com.carscan.data.obd.ObdParser.hexBytes(hex)

    @Test
    fun `уровень топлива с приборов - C минус 16 процентов`() {
        val fuel = ManufacturerPids.MMC_FUEL_LEVEL
        assertEquals(60.0, fuel.read(bytes("01004C0000"))!!, 1e-9)  // C = 76
        assertEquals(0.0, fuel.read(bytes("000010"))!!, 1e-9)       // C = 16 — пустой бак
        assertEquals(100.0, fuel.read(bytes("000074"))!!, 1e-9)     // C = 116 — полный
        assertEquals(100.0, fuel.read(bytes("000078"))!!, 1e-9)     // чуть выше полного — 100 %
        assertNull("FF — не уровень топлива", fuel.read(bytes("0000FF")))
        assertNull("байтов не хватает", fuel.read(bytes("0000")))
    }

    @Test
    fun `температура в салоне с климата - A делить на 4 минус 16`() {
        val cabin = ManufacturerPids.MMC_CABIN_TEMP
        assertEquals(22.5, cabin.read(bytes("9A8000"))!!, 1e-9)
        assertEquals(-16.0, cabin.read(bytes("00"))!!, 1e-9)
        assertNull(cabin.read(bytes("FF")))
        assertEquals("22.5 °C", cabin.format(22.5))
    }

    @Test
    fun `температура вариатора по полиному датчика JF011E`() {
        assertEquals(65.0, ManufacturerPids.jf011eTemperature(133), 0.1)
        assertEquals(47.5, ManufacturerPids.jf011eTemperature(100), 0.1)
        // Второй вариант формулы с форумов расходится не больше чем на 3 °C
        for (n in 20..200 step 10) {
            val x = n.toDouble()
            val cubic = 0.0000286 * x * x * x - 0.00951 * x * x + 1.46 * x - 30.1
            assertEquals("N = $n", cubic, ManufacturerPids.jf011eTemperature(n), 3.0)
        }
        val cvt = ManufacturerPids.MMC_CVT_TEMP
        val data = IntArray(18).also { it[13] = 0x85 }
        assertEquals(65.0, cvt.read(data)!!, 0.1)
        assertNull("0 — обрыв датчика", cvt.read(IntArray(18)))
        assertNull("короткий ответ", cvt.read(IntArray(10)))
    }

    @Test
    fun `команды выбираются по автомобилю`() {
        val xl = ManufacturerPids.forVehicle("Mitsubishi", "Outlander", "XL (2006–2012)", "2.0")
        assertNotNull(xl.fuelLevel)
        assertNotNull(xl.cabinTemp)
        assertEquals("7E1 2103", xl.atfTemp!!.label)

        assertNull("у V6 не вариатор", ManufacturerPids.forVehicle("Mitsubishi", "Outlander", "XL (2006–2012)", "3.0 V6").atfTemp)
        assertNull("у Outlander III после 2015 другой вариатор", ManufacturerPids.forVehicle("Mitsubishi", "Outlander", "III (2012–2022)", "2.0").atfTemp)
        assertEquals(ManufacturerPids.Commands(), ManufacturerPids.forVehicle("Mercedes-Benz", "GLS", "X167 (2019–)", "580 4MATIC"))
    }

    @Test
    fun `профиль Outlander XL из справочника получает команды`() {
        val spec = VehicleSpec("Mitsubishi", "Outlander", "XL (2006–2012)", "2.0", 2.0, FuelType.PETROL, 4, 147, 60.0)
        val profile = VehicleRepository.profileOf(spec, AppSettings())
        assertEquals(listOf("6A0 21A3", "688 2110", "7E1 2103"), profile.manufacturerCommands.map { it.label })
        assertTrue(profile.fuelLevelCommand!!.source.isNotEmpty())
    }
}
