package com.carscan.ui

import com.carscan.data.AppSettings
import com.carscan.data.ConnectionInfo
import com.carscan.data.ConnectionState
import com.carscan.data.DataSourceType
import com.carscan.data.ProbeVerdict
import com.carscan.data.SensorProbe
import com.carscan.data.TripStats
import com.carscan.data.VehicleTelemetry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DiagnosticReportTest {

    /** Как на магнитоле: расход есть, уровня топлива нет — значит, нет остатка и запаса хода. */
    private val state = DashboardState(
        telemetry = VehicleTelemetry(rpm = 1500, speedKmh = 60, coolantTemp = 90, fuelRateLph = 6.0, fuelRateMethod = "MAF", voltage = 14.1),
        trip = TripStats(distanceKm = 20.0, fuelUsedL = 1.8, durationSec = 1440, avgConsumptionL100 = 9.0),
        settings = AppSettings(dataSource = DataSourceType.BLUETOOTH, obdDeviceName = "OBDII"),
        streaming = true,
        connection = ConnectionInfo(
            state = ConnectionState.CONNECTED,
            message = "Подключено",
            selfTest = listOf(
                SensorProbe("Обороты", "010C", true, "410C1770", "1500 об/мин", ProbeVerdict.OK),
                SensorProbe("Уровень топлива", "012F", false, "NO DATA", null, ProbeVerdict.NOT_SUPPORTED),
            ),
            selfTestAtMs = 1,
        ),
    )
    private val env = ReportEnvironment("1.0.2", "Test HU", "10 (API 29)", 0, TimeZone.getTimeZone("Europe/Moscow"))

    @Test
    fun `объясняет, почему нет запаса хода`() {
        val missing = DiagnosticReport.missingValues(state).toMap()
        assertTrue(missing.getValue("Уровень топлива").contains("PID 2F: не поддерживается"))
        assertTrue(missing.getValue("Запас хода").contains("нет остатка топлива"))
        val problems = DiagnosticReport.problems(state)
        assertTrue(problems.toString(), problems.any { it.startsWith("Уровень топлива: не поддерживается") })
        assertTrue(problems.toString(), problems.any { it.startsWith("Запас хода") })
    }

    @Test
    fun `отчёт содержит разделы и историю`() {
        val history = listOf(TelemetrySnapshot(0, state.telemetry, state.trip, null))
        val text = DiagnosticReport.build(state, env, listOf("Координаты для погоды: 55.750, 37.620"), history)
        listOf("== Проблемы ==", "== Проверка датчиков", "[не поддерживается] Уровень топлива", "== Почему пусто",
            "== Погода ==", "55.750", "== История поездки", "1.0.2").forEach {
            assertTrue("нет «$it»", text.contains(it))
        }
        println(text)
    }
}
