package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripComputerTest {

    private val cruise = VehicleTelemetry(speedKmh = 60, fuelRateLph = 6.0)

    private fun TripComputer.drive(sample: VehicleTelemetry, seconds: Int, startMs: Long = 0L, stepMs: Long = 200L): TripStats {
        var stats = TripStats()
        var t = startMs
        while (t <= startMs + seconds * 1000L) {
            stats = onSample(sample, t)
            t += stepMs
        }
        return stats
    }

    @Test
    fun `средний расход считается по пройденному пути`() {
        // 60 км/ч и 6 л/ч в течение минуты: 1 км, 0.1 л, 10 л/100 км
        val stats = TripComputer().drive(cruise, seconds = 60)
        assertEquals(1.0, stats.distanceKm, 0.01)
        assertEquals(0.1, stats.fuelUsedL, 0.001)
        assertEquals(10.0, stats.avgConsumptionL100!!, 0.1)
        assertEquals(60, stats.durationSec)
        assertEquals(60.0, stats.avgSpeedKmh!!, 0.5)
    }

    @Test
    fun `в движении мгновенный расход в литрах на 100 км, на месте в литрах в час`() {
        val computer = TripComputer()
        val moving = computer.onSample(cruise, 0)
        assertEquals(10.0, moving.instantL100!!, 0.001)
        assertNull(moving.instantLph)

        val idle = computer.onSample(VehicleTelemetry(speedKmh = 0, fuelRateLph = 1.1), 200)
        assertNull(idle.instantL100)
        assertEquals(1.1, idle.instantLph!!, 0.001)
    }

    @Test
    fun `средний расход не показывается до первых 100 метров`() {
        val stats = TripComputer().drive(cruise, seconds = 3)
        assertNull(stats.avgConsumptionL100)
    }

    @Test
    fun `большой разрыв между снимками не считается ездой`() {
        val computer = TripComputer()
        computer.onSample(cruise, 0)
        val stats = computer.onSample(cruise, 60 * 60 * 1000L) // час без данных
        assertEquals(60 * 5_000.0 / 3_600_000.0, stats.distanceKm, 1e-9)
    }

    @Test
    fun `после паузы поездка продолжается без скачка`() {
        val computer = TripComputer()
        computer.drive(cruise, seconds = 60)
        computer.pause()
        val stats = computer.drive(cruise, seconds = 60, startMs = 10 * 60 * 1000L)
        assertEquals(2.0, stats.distanceKm, 0.02)
        assertNotNull(stats.avgConsumptionL100)
    }

    @Test
    fun `сброс обнуляет поездку`() {
        val computer = TripComputer()
        computer.drive(cruise, seconds = 60)
        computer.reset()
        val stats = computer.onSample(cruise, 0)
        assertEquals(0.0, stats.distanceKm, 0.0)
        assertEquals(0L, stats.durationSec)
    }
}
