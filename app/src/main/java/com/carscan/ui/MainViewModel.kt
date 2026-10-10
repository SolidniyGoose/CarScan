package com.carscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class DashboardState(
    // Основные параметры (всегда на экране)
    val voltage: Double = 12.0,
    val coolantTemp: Int = 90,
    val engineLoad: Double = 0.0,
    val currentGear: String = "P",
    val rpm: Int = 0,
    val speed: Int = 0,
    
    // Дополнительные параметры двигателя
    val atfTemp: Int = 20,
    val fuelLevel: Double = 45.0,
    val avgFuelConsumption: Double = 8.5,
    val shortTermFuelTrim: Double = 0.0,
    val longTermFuelTrim: Double = 0.0,
    val intakePressure: Int = 30,
    val fuelSystemStatus: String = "Open loop",
    
    // Диагностика
    val checkEngineOn: Boolean = false,
    val dtcCount: Int = 0,
    
    // Тесты (True = пройден)
    val testMisfire: Boolean = true,
    val testFuelSystem: Boolean = true,
    val testComponents: Boolean = true,
    val testCatalyst: Boolean = true,
    val testOxygenSensor: Boolean = true,
    val testEgr: Boolean = true,
    
    // Misfire Data
    val misfireCylinder1: Int = 0,
    val misfireCylinder2: Int = 0,
    val misfireCylinder3: Int = 0,
    val misfireCylinder4: Int = 0,
    
    // UI Navigation State
    val selectedPanel: ActivePanel = ActivePanel.MAIN,
    
    val isConnected: Boolean = false
)

enum class ActivePanel {
    MAIN, SENSORS_ALL, DIAGNOSTICS_ECU, EMISSIONS
}

class MainViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init {
        startSimulator()
    }

    fun selectPanel(panel: ActivePanel) {
        _uiState.update { it.copy(selectedPanel = panel) }
    }

    private fun startSimulator() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnected = true, checkEngineOn = false, dtcCount = 0) }
            
            var currentRpm = 660.0
            var currentLoad = 28.6
            var currentAtf = 22
            var currentCoolant = 91
            var currentSpeed = 0.0
            var accelerating = true

            while (isActive) {
                // Симуляция езды
                if (accelerating) {
                    currentSpeed += Random.nextDouble(0.5, 2.5)
                    if (currentSpeed > 110) accelerating = false
                } else {
                    currentSpeed -= Random.nextDouble(0.5, 3.0)
                    if (currentSpeed < 5) accelerating = true
                }
                
                // Обороты зависят от скорости (имитация переключения передач)
                val gearFactor = (currentSpeed / 30).toInt() + 1
                val simulatedGear = if (gearFactor > 6) "6" else gearFactor.toString()
                
                currentRpm = 1000 + (currentSpeed % 30) * 80 + Random.nextDouble(-30.0, 30.0)
                if (currentSpeed < 2) {
                    currentRpm = 750 + Random.nextDouble(-15.0, 15.0)
                    currentLoad = 20.0 + Random.nextDouble(-1.0, 1.0)
                } else {
                    currentLoad = 40.0 + (currentSpeed % 30) * 1.5 + Random.nextDouble(-2.0, 2.0)
                }
                
                if (Random.nextInt(100) > 95 && currentAtf < 90) currentAtf += 1
                if (Random.nextInt(100) > 95 && currentCoolant < 105) currentCoolant += 1

                _uiState.update { state ->
                    state.copy(
                        rpm = currentRpm.toInt(),
                        speed = currentSpeed.toInt(),
                        engineLoad = String.format("%.2f", currentLoad).replace(",", ".").toDouble(),
                        voltage = 13.4 + Random.nextDouble(-0.1, 0.1),
                        atfTemp = currentAtf,
                        fuelLevel = 45.0 - (currentLoad / 1000.0), // легкая симуляция расхода
                        avgFuelConsumption = 8.5 + (currentRpm - 660) / 1000.0,
                        coolantTemp = currentCoolant,
                        shortTermFuelTrim = String.format("%.2f", Random.nextDouble(-3.0, 3.0)).replace(",", ".").toDouble(),
                        longTermFuelTrim = -1.56,
                        intakePressure = 28 + Random.nextInt(-2, 2),
                        fuelSystemStatus = "Closed loop, using oxygen sensor feedback",
                        currentGear = if (currentSpeed < 2) "D1" else "D$simulatedGear"
                    )
                }
                
                delay(200) // 5 раз в секунду
            }
        }
    }
}
