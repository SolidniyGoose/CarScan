package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientTempFilterTest {

    @Test
    fun `на стоянке с прогретым двигателем завышенное значение не показывается`() {
        val f = AmbientTempFilter()
        // Как в отчёте с Outlander XL: холостой ход, ОЖ 87 °C, датчик +32 °C при +11 °C на улице
        assertNull(f.onSample(32, 0, 87, 0))
        assertNull(f.onSample(29, 0, 87, 60_000))
    }

    @Test
    fun `на холодном двигателе значение верное`() {
        val f = AmbientTempFilter()
        assertEquals(11.0, f.onSample(11, 0, 20, 0)!!, 0.0)
        // Двигатель прогрелся, подкапотник греет датчик — значение не растёт
        assertEquals(11.0, f.onSample(25, 0, 85, 600_000)!!, 0.0)
        // А понижение принимается сразу
        assertEquals(9.0, f.onSample(9, 0, 85, 660_000)!!, 0.0)
    }

    @Test
    fun `после минуты езды от 30 км в ч датчик принимается`() {
        val f = AmbientTempFilter()
        assertNull(f.onSample(30, 60, 90, 0))          // только разогнались — ещё греет
        assertNull(f.onSample(20, 60, 90, 30_000))
        assertEquals(12.0, f.onSample(12, 60, 90, 61_000)!!, 0.0)
        assertEquals(13.0, f.onSample(13, 70, 90, 120_000)!!, 0.0) // в движении можно и вверх
        // Встали в пробке — снова только вниз
        assertEquals(13.0, f.onSample(18, 0, 90, 130_000)!!, 0.0)
    }
}
