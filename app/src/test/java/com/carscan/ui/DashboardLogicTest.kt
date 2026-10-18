package com.carscan.ui

import com.carscan.data.AppSettings
import com.carscan.data.LocationSource
import com.carscan.data.OutsideWeather
import com.carscan.data.ReadinessTest
import com.carscan.data.TripStats
import com.carscan.data.VehicleTelemetry
import com.carscan.ui.theme.Palettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardLogicTest {

    private val telemetry = VehicleTelemetry(
        rpm = 2100,
        speedKmh = 90,
        gear = "D6",
        coolantTemp = 92,
        atfTemp = 74,
        cabinTemp = 21.6,
        ambientAirTemp = 7,
        fuelLevelPercent = 50.0,
        fuelRateLph = 9.0,
        voltage = 14.12,
        readiness = listOf(ReadinessTest("Катализатор", true)),
    )
    private val weather = OutsideWeather(9.3, 2, "Москва", LocationSource.DEVICE, 0L)
    private val state = DashboardState(
        telemetry = telemetry,
        trip = TripStats(distanceKm = 12.0, fuelUsedL = 1.2, avgConsumptionL100 = 10.0),
        weather = WeatherState(current = weather),
    )

    private fun status(s: DashboardState, node: CarNode) = CarNodes.evaluate(s, node)

    @Test
    fun `температура ОЖ определяет состояние двигателя`() {
        fun level(c: Int) = status(state.copy(telemetry = telemetry.copy(coolantTemp = c)), CarNode.ENGINE).level
        assertEquals(StatusLevel.INFO, level(45))
        assertEquals(StatusLevel.OK, level(92))
        assertEquals(StatusLevel.WARN, level(108))
        assertEquals(StatusLevel.CRIT, level(115))
        assertEquals(StatusLevel.OFF, status(state.copy(telemetry = VehicleTelemetry()), CarNode.ENGINE).level)
    }

    @Test
    fun `напряжение оценивается с учётом работы двигателя`() {
        assertEquals("Зарядка" to StatusLevel.OK, CarNodes.batteryStatus(14.1, 800))
        assertEquals(StatusLevel.WARN, CarNodes.batteryStatus(12.6, 800).second)
        assertEquals("Норма" to StatusLevel.OK, CarNodes.batteryStatus(12.6, 0))
        assertEquals(StatusLevel.CRIT, CarNodes.batteryStatus(11.8, null).second)
    }

    @Test
    fun `топливо пересчитывается в литры и запас хода`() {
        assertEquals(45.0, state.fuelLiters!!, 1e-9)       // 50 % от 90 л
        assertEquals(450.0, state.rangeKm!!, 1e-9)         // 45 л при 10 л/100 км
        val low = status(state.copy(telemetry = telemetry.copy(fuelLevelPercent = 8.0)), CarNode.FUEL)
        assertEquals(StatusLevel.CRIT, low.level)
    }

    @Test
    fun `улица берётся из интернета, а без него с датчика ТС`() {
        assertEquals(9.3, state.outsideTempC!!, 0.0)
        // Без интернета — датчик ТС, но только принятый фильтром (на стоянке его греет двигатель)
        assertEquals(7.0, state.copy(weather = WeatherState(), ambientTempC = 7.0).outsideTempC!!, 0.0)
        assertEquals(null, state.copy(weather = WeatherState(), ambientTempC = null).outsideTempC)
    }

    @Test
    fun `главная панель содержит все обязательные показатели`() {
        val spec = PanelContent.build(state)
        val rows = spec.sections.first().rows.associate { it.param to it.value }
        assertEquals("92 °C", rows["Темп. ОЖ"])
        assertEquals("74 °C", rows["Темп. масла АКПП"])
        assertEquals("45.0 л · 50 %", rows["Топливо в баке"])
        assertEquals("10.0 л/100 км", rows["Средний расход"])
        assertEquals("14.12 V", rows["Напряжение сети"])
        assertEquals("22 °C", rows["Темп. в салоне"])
        assertEquals("+9 °C (интернет)", rows["Темп. на улице"])
    }

    @Test
    fun `тап по узлу открывает панель детали`() {
        val spec = PanelContent.build(state.copy(selectedPanel = ActivePanel.NODE, selectedNode = CarNode.TRANSMISSION))
        assertEquals("ИНФОРМАЦИЯ О ДЕТАЛИ", spec.caption)
        assertEquals("УЗЕЛ: transmission", spec.chip)
        assertTrue(spec.closable)
        assertTrue(spec.actions.contains(PanelAction.FOCUS))
    }

    @Test
    fun `в информации о детали нет разделов, а нигде нет слова Раздел`() {
        for (node in CarNode.entries) {
            val spec = PanelContent.build(state.copy(selectedPanel = ActivePanel.NODE, selectedNode = node))
            assertTrue(node.name, spec.sections.all { it.title == null })
        }
        for (panel in ActivePanel.entries) {
            val spec = PanelContent.build(state.copy(selectedPanel = panel, selectedNode = CarNode.ENGINE))
            assertTrue(panel.name, spec.sections.none { it.title?.contains("Раздел") == true })
        }
    }

    @Test
    fun `в настройках можно выбрать цветовую гамму`() {
        val spec = PanelContent.build(state.copy(selectedPanel = ActivePanel.SETTINGS, settings = AppSettings(themeId = "red")))
        val themes = spec.sections.first { it.choiceGroup == ChoiceGroup.THEME }.choices
        assertEquals(Palettes.all.map { it.id }, themes.map { it.id })
        assertEquals("red", themes.single { it.selected }.id)
    }

    @Test
    fun `в настройках первым идёт автомобиль с правкой параметров`() {
        val spec = PanelContent.build(state.copy(selectedPanel = ActivePanel.SETTINGS, settings = AppSettings(tankOverride = 85.0)))
        val vehicle = spec.sections.first()
        assertEquals("Автомобиль", vehicle.title)
        assertEquals(listOf(StepperId.DISPLACEMENT, StepperId.TANK, StepperId.VE), vehicle.steppers.map { it.id })
        assertEquals(ChoiceGroup.FUEL_TYPE, vehicle.choiceGroup)
        assertTrue(PanelAction.PICK_VEHICLE in vehicle.actions)
        assertTrue("есть правка — можно вернуть справочник", PanelAction.RESET_VEHICLE in vehicle.actions)
    }

    @Test
    fun `выбор автомобиля по шагам`() {
        val picker = VehiclePickerState(make = "Lada", models = listOf(com.carscan.data.CatalogModel("Lada", "Vesta", "I (2015–2022)", 2)))
        val step2 = PanelContent.build(state.copy(selectedPanel = ActivePanel.VEHICLE_PICKER, vehiclePicker = picker))
        assertEquals("Lada", step2.title)
        assertEquals(ChoiceGroup.VEHICLE_MODEL, step2.sections.single().choiceGroup)
        assertEquals("I (2015–2022) · 2 двигателя", step2.sections.single().choices.single().hint)

        val step1 = PanelContent.build(state.copy(selectedPanel = ActivePanel.VEHICLE_PICKER, vehiclePicker = VehiclePickerState(makes = listOf("Audi", "Lada"))))
        assertEquals(ChoiceGroup.VEHICLE_MAKE, step1.sections.single().choiceGroup)
        assertTrue(PanelAction.PICKER_BACK in step1.actions)
    }

    @Test
    fun `без данных показываются прочерки, а не нули`() {
        val empty = DashboardState()
        val rows = PanelContent.build(empty).sections.first().rows
        assertTrue(rows.all { it.value.startsWith(NO_VALUE) })
    }

    @Test
    fun `форматирование`() {
        assertEquals("+9 °C", signedTemp(9.4))
        assertEquals("-3 °C", signedTemp(-2.6))
        assertEquals("0 °C", signedTemp(0.2))
        assertEquals("07:32", formatDuration(452))
        assertEquals("1:07:32", formatDuration(4052))
    }
}
