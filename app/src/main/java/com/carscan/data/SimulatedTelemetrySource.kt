package com.carscan.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Симулятор езды: разгон, крейсерский режим, торможение, остановки.
 * Обороты и передача связаны со скоростью (9-ступенчатая АКПП), двигатель и АКПП прогреваются,
 * топливо расходуется пропорционально нагрузке.
 */
class SimulatedTelemetrySource(
    private val profile: () -> VehicleProfile = { VehicleProfile() },
    private val random: Random = Random.Default,
) : TelemetrySource {

    override val name: String = "Симулятор"

    override val connection: StateFlow<ConnectionInfo> =
        MutableStateFlow(ConnectionInfo(ConnectionState.CONNECTED, "Симулятор: данные генерируются в приложении"))

    override fun samples(): Flow<VehicleTelemetry> = flow {
        val simulation = DriveSimulation(profile().tankLiters, random)
        while (true) {
            emit(simulation.step(TICK_MS / 1000.0))
            delay(TICK_MS)
        }
    }

    companion object {
        const val TICK_MS = 200L // 5 раз в секунду
    }
}

internal class DriveSimulation(
    private val tankLiters: Double,
    private val random: Random,
) {
    private enum class Phase { IDLE, ACCELERATE, CRUISE, BRAKE }

    private var phase = Phase.IDLE
    private var phaseLeftSec = 3.0
    private var speed = 0.0
    private var targetSpeed = 0.0
    private var coolant = 62.0
    private var atf = 24.0
    private var cabin = 14.0
    private var fuelPercent = 64.0

    fun step(dtSec: Double): VehicleTelemetry {
        advanceSpeed(dtSec)

        val gear = gearFor(speed)
        val stopped = speed < 1.0
        val rpm = if (stopped) {
            680.0 + random.nextDouble(-15.0, 15.0)
        } else {
            val boost = if (phase == Phase.ACCELERATE) 300.0 else 0.0
            max(900.0, speed * RPM_PER_KMH[gear - 1]) + boost + random.nextDouble(-25.0, 25.0)
        }
        val load = when {
            stopped -> 19.0 + random.nextDouble(-1.0, 1.0)
            phase == Phase.ACCELERATE -> random.nextDouble(58.0, 78.0)
            phase == Phase.BRAKE -> random.nextDouble(5.0, 10.0)
            else -> 22.0 + speed * 0.15 + random.nextDouble(-2.0, 2.0)
        }
        val fuelRate = if (stopped) {
            1.1 + random.nextDouble(-0.05, 0.05)
        } else {
            0.6 + rpm / 1000.0 * load / 100.0 * 11.0
        }

        fuelPercent = max(0.0, fuelPercent - fuelRate * dtSec / 3600.0 / tankLiters * 100.0)
        coolant = approach(coolant, if (coolant < 88) 95.0 else 89.0 + load * 0.05, if (coolant < 88) 0.4 else 0.2, dtSec)
        atf = approach(atf, 70.0 + load * 0.15, 0.08, dtSec)
        cabin = approach(cabin, 22.0, 0.03, dtSec)
        val ambient = 9.0 + random.nextDouble(-0.2, 0.2)

        return VehicleTelemetry(
            rpm = rpm.toInt(),
            speedKmh = speed.toInt(),
            gear = "D$gear",
            engineLoad = load,
            coolantTemp = coolant.toInt(),
            atfTemp = atf.toInt(),
            intakeAirTemp = (ambient + 12 + load * 0.1).toInt(),
            ambientAirTemp = ambient.toInt(),
            cabinTemp = cabin,
            fuelLevelPercent = fuelPercent,
            fuelRateLph = fuelRate,
            fuelRateMethod = "симулятор",
            voltage = 14.15 + random.nextDouble(-0.08, 0.08),
            intakePressureKpa = (28 + load * 0.72).toInt(),
            shortTermFuelTrim = random.nextDouble(-3.0, 3.0),
            longTermFuelTrim = -1.56,
            fuelSystemStatus = if (coolant > 70) "Замкнутый контур (датчик O₂)" else "Разомкнутый контур (прогрев)",
            checkEngineOn = false,
            dtcCount = 0,
            misfires = List(4) { 0 },
            readiness = READINESS.map { ReadinessTest(it, passed = true) },
        )
    }

    private fun advanceSpeed(dt: Double) {
        phaseLeftSec -= dt
        when (phase) {
            Phase.IDLE -> if (phaseLeftSec <= 0) {
                phase = Phase.ACCELERATE
                targetSpeed = random.nextDouble(40.0, 120.0)
            }
            Phase.ACCELERATE -> {
                speed += random.nextDouble(5.0, 9.0) * dt
                if (speed >= targetSpeed) {
                    speed = targetSpeed
                    phase = Phase.CRUISE
                    phaseLeftSec = random.nextDouble(6.0, 15.0)
                }
            }
            Phase.CRUISE -> {
                speed = (speed + random.nextDouble(-3.0, 3.0) * dt).coerceIn(targetSpeed - 4, targetSpeed + 4)
                if (phaseLeftSec <= 0) {
                    phase = Phase.BRAKE
                    targetSpeed = if (random.nextInt(3) == 0) 0.0 else speed * random.nextDouble(0.3, 0.7)
                }
            }
            Phase.BRAKE -> {
                speed -= random.nextDouble(6.0, 11.0) * dt
                if (speed <= targetSpeed) {
                    speed = max(targetSpeed, 0.0)
                    if (speed < 1.0) {
                        speed = 0.0
                        phase = Phase.IDLE
                        phaseLeftSec = random.nextDouble(3.0, 8.0)
                    } else {
                        phase = Phase.ACCELERATE
                        targetSpeed = min(130.0, speed + random.nextDouble(15.0, 60.0))
                    }
                }
            }
        }
    }

    private fun gearFor(speedKmh: Double): Int = UPSHIFT_KMH.count { speedKmh >= it }.coerceIn(1, 9)

    private fun approach(value: Double, target: Double, ratePerSec: Double, dt: Double): Double {
        val step = ratePerSec * dt
        return if (value < target) min(target, value + step) else max(target, value - step)
    }

    private companion object {
        // Скорости включения передач 1..9 и обороты на 1 км/ч для каждой передачи
        val UPSHIFT_KMH = doubleArrayOf(0.0, 18.0, 32.0, 48.0, 64.0, 80.0, 95.0, 110.0, 125.0)
        val RPM_PER_KMH = doubleArrayOf(95.0, 62.0, 45.0, 35.0, 29.0, 24.5, 21.0, 18.5, 16.5)
        val READINESS = listOf(
            "Пропуски зажигания", "Топливная система", "Компоненты",
            "Катализатор", "Датчик кислорода", "Система EGR",
        )
    }
}
