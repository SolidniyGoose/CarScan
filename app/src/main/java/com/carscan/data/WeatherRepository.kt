package com.carscan.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class OutsideWeather(
    val temperatureC: Double,
    val weatherCode: Int,
    val place: String?,
    val locationSource: LocationSource,
    val fetchedAtMs: Long,
)

enum class LocationSource(val label: String) {
    DEVICE("геолокация"),
    NETWORK_IP("по IP-адресу"),
}

internal data class Coordinates(
    val latitude: Double,
    val longitude: Double,
    val place: String?,
    val source: LocationSource,
)

internal data class CurrentConditions(val temperatureC: Double, val weatherCode: Int)

/**
 * Уличная температура из интернета: Open-Meteo (бесплатно, без API-ключа).
 * Координаты берутся из последней известной геопозиции устройства,
 * а если её нет (нет разрешения или GPS) — приблизительно по IP-адресу.
 */
class WeatherRepository(private val context: Context) {

    suspend fun fetchCurrent(): OutsideWeather = withContext(Dispatchers.IO) {
        val coordinates = deviceCoordinates() ?: parseGeoJs(httpGet(GEOJS_URL))
        val conditions = parseOpenMeteo(httpGet(openMeteoUrl(coordinates.latitude, coordinates.longitude)))
        OutsideWeather(
            temperatureC = conditions.temperatureC,
            weatherCode = conditions.weatherCode,
            place = coordinates.place,
            locationSource = coordinates.source,
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    private fun deviceCoordinates(): Coordinates? {
        val location = lastKnownLocation() ?: return null
        return Coordinates(
            latitude = location.latitude,
            longitude = location.longitude,
            place = placeName(location.latitude, location.longitude),
            source = LocationSource.DEVICE,
        )
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // проверено в hasLocationPermission()
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION") // синхронный getFromLocation достаточен: вызываем на IO-потоке
    private fun placeName(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(context, Locale.forLanguageTag("ru"))
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "CarScan/1.0 (Android)")
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "HTTP $code для $url" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 8_000
        private const val GEOJS_URL = "https://get.geojs.io/v1/ip/geo.json"

        internal fun openMeteoUrl(latitude: Double, longitude: Double): String =
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code",
                latitude,
                longitude,
            )

        internal fun parseOpenMeteo(body: String): CurrentConditions {
            val current = JSONObject(body).getJSONObject("current")
            return CurrentConditions(
                temperatureC = current.getDouble("temperature_2m"),
                weatherCode = current.optInt("weather_code", -1),
            )
        }

        /** geojs отдаёт координаты строками; города может не быть. */
        internal fun parseGeoJs(body: String): Coordinates {
            val json = JSONObject(body)
            return Coordinates(
                latitude = json.getString("latitude").toDouble(),
                longitude = json.getString("longitude").toDouble(),
                place = json.optString("city").takeIf { it.isNotBlank() },
                source = LocationSource.NETWORK_IP,
            )
        }

        /** Расшифровка кода погоды WMO, который возвращает Open-Meteo. */
        fun describeWeatherCode(code: Int): String = when (code) {
            0 -> "Ясно"
            1 -> "Преимущественно ясно"
            2 -> "Переменная облачность"
            3 -> "Пасмурно"
            45, 48 -> "Туман"
            51, 53, 55, 56, 57 -> "Морось"
            61, 63, 65, 66, 67 -> "Дождь"
            71, 73, 75, 77 -> "Снег"
            80, 81, 82 -> "Ливень"
            85, 86 -> "Снегопад"
            95, 96, 99 -> "Гроза"
            else -> "—"
        }
    }
}
