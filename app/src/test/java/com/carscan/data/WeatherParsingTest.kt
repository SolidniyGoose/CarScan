package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherParsingTest {

    @Test
    fun `разбирает текущую погоду Open-Meteo`() {
        val body = """
            {"latitude":55.7817,"longitude":37.570797,"current_units":{"temperature_2m":"°C"},
             "current":{"time":"2026-10-10T17:00","interval":900,"temperature_2m":9.0,"weather_code":2}}
        """.trimIndent()
        val conditions = WeatherRepository.parseOpenMeteo(body)
        assertEquals(9.0, conditions.temperatureC, 0.0)
        assertEquals(2, conditions.weatherCode)
    }

    @Test
    fun `geojs отдаёт координаты строками, города может не быть`() {
        val withCity = WeatherRepository.parseGeoJs("""{"latitude":"55.7386","longitude":"37.6068","city":"Moscow"}""")
        assertEquals(55.7386, withCity.latitude, 1e-9)
        assertEquals(37.6068, withCity.longitude, 1e-9)
        assertEquals("Moscow", withCity.place)
        assertEquals(LocationSource.NETWORK_IP, withCity.source)

        val noCity = WeatherRepository.parseGeoJs("""{"latitude":"55.7","longitude":"37.6"}""")
        assertNull(noCity.place)
    }

    @Test
    fun `координаты в URL всегда через точку`() {
        val url = WeatherRepository.openMeteoUrl(55.75, 37.6173)
        assertTrue(url, url.contains("latitude=55.7500&longitude=37.6173"))
    }

    @Test
    fun `коды погоды WMO`() {
        assertEquals("Ясно", WeatherRepository.describeWeatherCode(0))
        assertEquals("Снег", WeatherRepository.describeWeatherCode(73))
        assertEquals("—", WeatherRepository.describeWeatherCode(-1))
    }
}
