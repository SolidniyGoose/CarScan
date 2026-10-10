package com.carscan.data

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DriveSimulationTest {

    @Test
    fun `десять минут симуляции дают правдоподобные значения`() {
        val simulation = DriveSimulation(tankLiters = 90.0, random = Random(42))
        val samples = List(10 * 60 * 5) { simulation.step(0.2) }

        assertTrue(samples.all { it.rpm!! in 600..6500 })
        assertTrue(samples.all { it.speedKmh!! in 0..135 })
        assertTrue(samples.all { it.gear!!.matches(Regex("D[1-9]")) })
        assertTrue("машина должна ездить", samples.any { it.speedKmh!! > 40 })
        assertTrue("и останавливаться", samples.any { it.speedKmh == 0 })

        val first = samples.first()
        val last = samples.last()
        assertTrue("топливо расходуется", last.fuelLevelPercent!! < first.fuelLevelPercent!!)
        assertTrue("двигатель прогревается", first.coolantTemp!! < 70 && last.coolantTemp!! >= 85)
        assertTrue("АКПП прогревается", last.atfTemp!! > first.atfTemp!!)
        assertTrue(samples.all { it.voltage!! in 13.9..14.4 })
    }
}
