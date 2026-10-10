package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleCatalogTest {

    // Тесты запускаются из каталога модуля app/
    private val catalog = VehicleCatalog.parse(File("src/main/assets/vehicles.csv").readText(Charsets.UTF_8))

    @Test
    fun `справочник разбирается целиком и ключи уникальны`() {
        assertTrue("вариантов: ${catalog.vehicles.size}", catalog.vehicles.size >= 500)
        assertTrue("марок: ${catalog.makes().size}", catalog.makes().size >= 40)
        val duplicates = catalog.vehicles.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("повторы: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `значения в правдоподобных пределах`() {
        for (v in catalog.vehicles) {
            assertTrue(v.id, v.displacementL in 0.6..8.5)
            assertTrue(v.id, v.tankLiters in 30.0..150.0)
            assertTrue(v.id, v.powerHp in 40..800)
            assertTrue(v.id, v.cylinders in 3..12)
            // Объём в подписи двигателя совпадает с полем объёма (если подпись начинается с цифр)
            Regex("""^(\d\.\d)\b""").find(v.engine)?.let { m ->
                assertEquals(v.id, m.groupValues[1].toDouble(), v.displacementL, 0.11)
            }
        }
    }

    @Test
    fun `автомобиль по умолчанию есть в справочнике`() {
        val gls = catalog.find(VehicleCatalog.DEFAULT_ID)
        assertNotNull(gls)
        assertEquals(4.0, gls!!.displacementL, 0.0)
        assertEquals(90.0, gls.tankLiters, 0.0)
    }

    @Test
    fun `навигация марка - модель - двигатель`() {
        val models = catalog.models("Lada")
        val vesta = models.first { it.model == "Vesta" && it.generation.startsWith("I ") }
        val variants = catalog.variants("Lada", vesta.key)
        assertEquals(listOf(106, 122), variants.map { it.powerHp })
        assertEquals(55.0, variants[0].tankLiters, 0.0)
        assertEquals("1.6 л · 106 л.с. · бензин · бак 55 л", variants[0].summary)
    }

    @Test
    fun `ошибка в строке указывает её номер`() {
        val error = runCatching { VehicleCatalog.parse("# заголовок\nLada;Vesta;I;1.6;1,6;P;4;106;55") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("vehicles.csv:2"))
    }

    @Test
    fun `ручные правки поверх справочника`() {
        val spec = catalog.find(VehicleCatalog.DEFAULT_ID)!!
        val profile = VehicleRepository.profileOf(spec, AppSettings(tankOverride = 100.0, fuelOverride = FuelType.DIESEL, veOverride = 0.9))
        assertEquals(100.0, profile.tankLiters, 0.0)
        assertEquals(FuelType.DIESEL, profile.fuelType)
        assertEquals(0.9, profile.volumetricEfficiency, 0.0)
        assertEquals(4.0, profile.displacementL, 0.0) // не правили — из справочника
        assertEquals("Mercedes-Benz GLS", profile.name)
    }
}
