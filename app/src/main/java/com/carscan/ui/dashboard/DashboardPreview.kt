package com.carscan.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.carscan.data.ConnectionInfo
import com.carscan.data.ConnectionState
import com.carscan.data.LocationSource
import com.carscan.data.OutsideWeather
import com.carscan.data.ReadinessTest
import com.carscan.data.TripStats
import com.carscan.data.VehicleTelemetry
import com.carscan.ui.ActivePanel
import com.carscan.ui.ChoiceGroup
import com.carscan.ui.DashboardActions
import com.carscan.ui.DashboardState
import com.carscan.ui.PanelAction
import com.carscan.ui.StepperId
import com.carscan.ui.WeatherState
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Palettes

/** Демо-состояние для превью в Android Studio. */
internal fun sampleDashboardState(): DashboardState = DashboardState(
    telemetry = VehicleTelemetry(
        rpm = 1470,
        speedKmh = 64,
        gear = "D5",
        engineLoad = 31.5,
        coolantTemp = 92,
        atfTemp = 74,
        intakeAirTemp = 24,
        ambientAirTemp = 8,
        cabinTemp = 21.8,
        fuelLevelPercent = 62.0,
        fuelRateLph = 6.4,
        voltage = 14.12,
        intakePressureKpa = 51,
        shortTermFuelTrim = 1.2,
        longTermFuelTrim = -1.56,
        fuelSystemStatus = "Замкнутый контур (датчик O₂)",
        misfires = List(4) { 0 },
        readiness = listOf("Пропуски зажигания", "Топливная система", "Катализатор").map { ReadinessTest(it, true) },
    ),
    trip = TripStats(distanceKm = 12.4, fuelUsedL = 1.29, durationSec = 44 * 60 + 12, avgConsumptionL100 = 10.4, instantL100 = 10.0, avgSpeedKmh = 16.8),
    weather = WeatherState(OutsideWeather(9.0, 2, "Москва", LocationSource.DEVICE, 0L)),
    streaming = true,
    connection = ConnectionInfo(ConnectionState.CONNECTED, "Симулятор"),
    selectedPanel = ActivePanel.MAIN,
)

internal object NoopDashboardActions : DashboardActions {
    override fun selectPanel(panel: ActivePanel) = Unit
    override fun closeNodePanel() = Unit
    override fun onPanelAction(action: PanelAction) = Unit
    override fun onChoice(group: ChoiceGroup, id: String) = Unit
    override fun onStep(id: StepperId, direction: Int) = Unit
    override fun togglePower() = Unit
}

@Preview(name = "Магнитола 1024×600", widthDp = 1024, heightDp = 600)
@Preview(name = "Телефон 800×360", widthDp = 800, heightDp = 360)
@Composable
private fun DashboardOverlayPreview() {
    CarScanTheme {
        Box(Modifier.fillMaxSize().background(Palettes.Cyan.background)) {
            DashboardOverlay(sampleDashboardState(), NoopDashboardActions)
        }
    }
}
