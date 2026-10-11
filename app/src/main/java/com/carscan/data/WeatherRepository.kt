package com.carscan.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

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
 * Координаты — свежая геопозиция устройства (последняя известная, если ей меньше часа, иначе новый запрос),
 * а если её нет (нет разрешения или GPS) — приблизительно по IP-адресу.
 */
class WeatherRepository(private val context: Context) {

    /** Как было получено последнее значение: для отчёта диагностики. */
    @Volatile
    var diagnostics: List<String> = listOf("Погода ещё не запрашивалась")
        private set

    suspend fun fetchCurrent(): OutsideWeather = withContext(Dispatchers.IO) {
        val diag = mutableListOf<String>()
        try {
            val coordinates = deviceCoordinates(diag) ?: parseGeoJs(httpGet(GEOJS_URL).also { diag += "geojs: ${it.take(400)}" })
            diag += String.format(
                Locale.US, "Координаты для погоды: %.3f, %.3f (%s, %s)",
                coordinates.latitude, coordinates.longitude, coordinates.source.label, coordinates.place ?: "город не определён",
            )
            val url = openMeteoUrl(coordinates.latitude, coordinates.longitude)
            val body = httpGet(url)
            diag += "Open-Meteo: ${body.take(600)}"
            val conditions = parseOpenMeteo(body)
            OutsideWeather(
                temperatureC = conditions.temperatureC,
                weatherCode = conditions.weatherCode,
                place = coordinates.place,
                locationSource = coordinates.source,
                fetchedAtMs = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            diag += "Ошибка: ${e.javaClass.simpleName}: ${e.message}"
            throw e
        } finally {
            diagnostics = diag
        }
    }

    private suspend fun deviceCoordinates(diag: MutableList<String>): Coordinates? {
        if (!hasLocationPermission()) {
            diag += "Нет разрешения на геолокацию — местоположение по IP"
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = manager.getProviders(true)
        diag += "Включённые источники геопозиции: ${providers.ifEmpty { listOf("нет") }.joinToString()}"
        val known = lastKnownLocations(manager, providers)
        known.forEach { diag += "  последняя ${describe(it)}" }
        var location = known.minByOrNull { ageMs(it) }?.takeIf { ageMs(it) <= MAX_FIX_AGE_MS && !isNullIsland(it) }
        if (location == null) {
            diag += "Нет свежей геопозиции (моложе ${MAX_FIX_AGE_MS / 60_000} мин) — запрашиваю новую"
            location = freshLocation(manager, providers)
            diag += location?.let { "  новая ${describe(it)}" } ?: "  новую получить не удалось за ${FRESH_FIX_TIMEOUT_MS / 1000} с"
        }
        if (location == null || isNullIsland(location)) {
            diag += "Геопозиции нет — местоположение по IP"
            return null
        }
        return Coordinates(
            latitude = location.latitude,
            longitude = location.longitude,
            place = placeName(location.latitude, location.longitude),
            source = LocationSource.DEVICE,
        )
    }

    /** Возраст по монотонным часам: на магнитолах системное время часто сбито. */
    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    /** Координаты 0,0 — признак «пустой» позиции у некоторых магнитол. */
    private fun isNullIsland(location: Location) = kotlin.math.abs(location.latitude) < 0.01 && kotlin.math.abs(location.longitude) < 0.01

    private fun describe(location: Location): String = String.format(
        Locale.US, "%s: %.3f, %.3f, точность %.0f м, возраст %d мин",
        location.provider, location.latitude, location.longitude, location.accuracy, ageMs(location) / 60_000,
    )

    @SuppressLint("MissingPermission") // проверено в deviceCoordinates()
    private fun lastKnownLocations(manager: LocationManager, providers: List<String>): List<Location> =
        providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(manager: LocationManager, providers: List<String>): Location? {
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return null
        return withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    manager.getCurrentLocation(provider, cancel, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (cont.isActive) cont.resume(location)
                        }

                        // До Android 11 у этих методов нет реализации по умолчанию — без них AbstractMethodError
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
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
        private const val MAX_FIX_AGE_MS = 60 * 60 * 1000L
        private const val FRESH_FIX_TIMEOUT_MS = 20_000L
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
