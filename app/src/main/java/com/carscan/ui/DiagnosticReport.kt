package com.carscan.ui

import com.carscan.data.ProbeVerdict
import com.carscan.data.TripStats
import com.carscan.data.VehicleTelemetry
import com.carscan.data.obd.Pid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Снимок показаний для истории поездки в отчёте (раз в минуту). */
data class TelemetrySnapshot(val timeMs: Long, val telemetry: VehicleTelemetry, val trip: TripStats, val fuelLiters: Double?)

/** Что известно об устройстве и приложении: собирает Activity/ViewModel, сюда передаётся готовым. */
data class ReportEnvironment(
    val appVersion: String,
    val device: String,
    val android: String,
    val nowMs: Long,
    val timeZone: TimeZone = TimeZone.getDefault(),
)

/**
 * Текстовый отчёт для отладки на машине: проверка датчиков, всё, что сейчас на экране,
 * и почему каждое «—» пустое. Без Android-зависимостей — тестируется локально.
 */
object DiagnosticReport {

    /** Короткий список проблем: подпись к файлу в Telegram и строка в настройках. */
    fun problems(s: DashboardState): List<String> {
        val result = mutableListOf<String>()
        s.connection.selfTest
            .filter { it.verdict != ProbeVerdict.OK && it.command.startsWith("01") && it.command.substring(2).toInt(16) in KEY_PIDS }
            .forEach { result += "${it.name}: ${it.verdict.label}" + (it.decoded?.let { v -> " ($v)" } ?: "") }
        missingValues(s).forEach { (name, reason) ->
            // Если про этот датчик уже сказала проверка — не повторяем
            if (name in KEY_DISPLAY && result.none { it.startsWith("$name:") }) result += "$name — $reason"
        }
        if (s.weather.error != null) result += "Погода: ${s.weather.error}"
        s.weather.current?.let { w ->
            val car = s.telemetry.ambientAirTemp
            if (car != null && kotlin.math.abs(w.temperatureC - car) >= 5) {
                result += "Погода ${signed(w.temperatureC)} (${w.place ?: "?"}, ${w.locationSource.label}) ≠ датчик ТС ${signed(car.toDouble())}"
            }
        }
        return result.distinct()
    }

    fun build(s: DashboardState, env: ReportEnvironment, weatherDiagnostics: List<String>, history: List<TelemetrySnapshot>): String {
        val sb = StringBuilder()
        fun line(text: String = "") = sb.append(text).append('\n')
        fun row(name: String, value: Any?) = line("  ${name.padEnd(26)} ${value ?: NO_VALUE}")
        fun header(title: String) {
            line()
            line("== $title ==")
        }
        val c = s.connection
        val t = s.telemetry
        val p = s.profile

        line("CarScan — отчёт диагностики")
        row("Время", time(env.nowMs, env.timeZone, "dd.MM.yyyy HH:mm:ss") + " (" + env.timeZone.id + ")")
        row("Версия приложения", env.appVersion)
        row("Устройство", env.device)
        row("Android", env.android)

        header("Проблемы")
        problems(s).ifEmpty { listOf("не найдено") }.forEach { line("  • $it") }

        header("Подключение")
        row("Источник", s.dataSourceName)
        row("Адаптер (выбран)", listOfNotNull(s.settings.obdDeviceName, s.settings.obdDeviceAddress).joinToString(" · ").ifEmpty { null })
        row("Поток данных", if (s.streaming) "включён" else "выключен")
        row("Состояние", "${c.state} · ${c.message}")
        row("Адаптер (AT I)", c.adapter)
        row("Протокол", c.protocol)
        row("Заявленные PID", c.supportedPids.sorted().joinToString(" ") { hex(it) }.ifEmpty { null })

        header("Проверка датчиков" + if (c.selfTestAtMs > 0) " (" + time(c.selfTestAtMs, env.timeZone, "HH:mm:ss") + ")" else "")
        if (c.selfTest.isEmpty()) {
            line("  не проводилась (нужно подключение к ELM327)")
        } else {
            c.selfTest.forEach { probe ->
                val declared = when (probe.declared) {
                    true -> "заявлен"
                    false -> "не заявлен"
                    null -> ""
                }
                line(
                    "  [${probe.verdict.label}] ${probe.name} · ${probe.command}" +
                        (if (declared.isNotEmpty()) " · $declared" else "") +
                        (probe.decoded?.let { " = $it" } ?: "") +
                        (probe.note?.let { " · $it" } ?: ""),
                )
                line("      ответ: ${probe.response}")
            }
        }

        header("Автомобиль")
        row("Профиль", "${p.name} ${p.generation} · ${p.engine}")
        row("Объём / топливо", "${fmt(p.displacementL, 1)} л · ${p.fuelType.title}")
        row("Бак", "${fmt(p.tankLiters, 0)} л" + if (s.settings.tankOverride != null) " (изменён вручную)" else "")
        row("Коэф. наполнения", "${fmt(p.volumetricEfficiency * 100, 0)} %")

        header("Показания сейчас")
        row("Обороты", t.rpm)
        row("Скорость", t.speedKmh?.let { "$it км/ч" })
        row("Нагрузка", t.engineLoad?.let { fmt(it, 1) + " %" })
        row("Темп. ОЖ", t.coolantTemp?.let { "$it °C" })
        row("Темп. впуска", t.intakeAirTemp?.let { "$it °C" })
        row("Давление во впуске", t.intakePressureKpa?.let { "$it кПа" })
        row("Уровень топлива", t.fuelLevelPercent?.let { fmt(it, 1) + " %" })
        row("Расход", t.fuelRateLph?.let { fmt(it, 2) + " л/ч" })
        row("Способ расчёта расхода", t.fuelRateMethod)
        row("Коррекции кратк./долг.", listOf(t.shortTermFuelTrim, t.longTermFuelTrim).joinToString(" / ") { it?.let { v -> fmt(v, 1) } ?: NO_VALUE })
        row("Топливная система", t.fuelSystemStatus)
        row("Напряжение", t.voltage?.let { fmt(it, 2) + " V" })
        row("Темп. наружная (ТС)", t.ambientAirTemp?.let { "$it °C" })
        row("Темп. АКПП", t.atfTemp?.let { "$it °C" })
        row("Темп. салона", t.cabinTemp?.let { fmt(it, 1) + " °C" })
        row("Check Engine / DTC", "${if (t.checkEngineOn) "горит" else "не горит"} / ${t.dtcCount}")
        row("Пропуски по цилиндрам", t.misfires.joinToString(" ").ifEmpty { null })

        header("Бортовой компьютер")
        val trip = s.trip
        row("Пробег", fmt(trip.distanceKm, 2) + " км")
        row("Время в пути", "${trip.durationSec / 60} мин ${trip.durationSec % 60} с")
        row("Израсходовано", fmt(trip.fuelUsedL, 3) + " л")
        row("Средний расход", trip.avgConsumptionL100?.let { fmt(it, 1) + " л/100 км" })
        row("Мгновенный", trip.instantL100?.let { fmt(it, 1) + " л/100 км" } ?: trip.instantLph?.let { fmt(it, 2) + " л/ч" })
        row("Остаток топлива", s.fuelLiters?.let { fmt(it, 1) + " л" })
        row("Запас хода", s.rangeKm?.let { fmt(it, 0) + " км" })

        header("Почему пусто («—» на экране)")
        missingValues(s).ifEmpty { listOf("все значения есть" to "") }
            .forEach { (name, reason) -> line("  $name" + if (reason.isNotEmpty()) " — $reason" else "") }

        header("Погода")
        val w = s.weather.current
        row("Показано на экране", s.outsideTempC?.let { signed(it) + if (w != null) " (интернет)" else " (датчик ТС)" })
        row("Open-Meteo", w?.let { "${signed(it.temperatureC)}, код ${it.weatherCode}" })
        row("Место", w?.let { listOfNotNull(it.place, it.locationSource.label).joinToString(" · ") })
        row("Обновлено", w?.let { time(it.fetchedAtMs, env.timeZone, "dd.MM HH:mm") })
        row("Ошибка", s.weather.error ?: "нет")
        weatherDiagnostics.forEach { line("  $it") }

        header("История поездки (раз в минуту)")
        if (history.isEmpty()) {
            line("  пока пусто")
        } else {
            line("  время   км/ч  об/мин  ОЖ   топл%   л/ч    км     л     ср.л/100  ост.л")
            history.forEach { h ->
                val ht = h.telemetry
                line(
                    "  " + listOf(
                        time(h.timeMs, env.timeZone, "HH:mm").padEnd(6),
                        (ht.speedKmh?.toString() ?: NO_VALUE).padStart(5),
                        (ht.rpm?.toString() ?: NO_VALUE).padStart(7),
                        (ht.coolantTemp?.toString() ?: NO_VALUE).padStart(4),
                        (ht.fuelLevelPercent?.let { fmt(it, 1) } ?: NO_VALUE).padStart(6),
                        (ht.fuelRateLph?.let { fmt(it, 2) } ?: NO_VALUE).padStart(6),
                        fmt(h.trip.distanceKm, 1).padStart(6),
                        fmt(h.trip.fuelUsedL, 2).padStart(5),
                        (h.trip.avgConsumptionL100?.let { fmt(it, 1) } ?: NO_VALUE).padStart(9),
                        (h.fuelLiters?.let { fmt(it, 1) } ?: NO_VALUE).padStart(6),
                    ).joinToString(" "),
                )
            }
        }

        header("Журнал OBD (последние ${c.log.size}, старые сверху)")
        c.log.forEach { e -> line("  ${e.command} → ${e.response}" + (e.decoded?.let { " = $it" } ?: "")) }
        return sb.toString()
    }

    /** Значения, которые на экране «—», и причина. */
    fun missingValues(s: DashboardState): List<Pair<String, String>> {
        val t = s.telemetry
        val obd = s.settings.dataSource == com.carscan.data.DataSourceType.BLUETOOTH
        val probes = s.connection.selfTest.associateBy { it.command }
        fun pidReason(pid: Int): String {
            val probe = probes["01" + hex(pid)]
            return when {
                !s.streaming -> "поток данных выключен"
                s.connection.state != com.carscan.data.ConnectionState.CONNECTED && obd -> "нет связи с адаптером"
                probe == null -> "PID ${hex(pid)} не проверялся"
                else -> "PID ${hex(pid)}: ${probe.verdict.label}, ответ «${probe.response}»"
            }
        }
        val result = mutableListOf<Pair<String, String>>()
        if (t.fuelLevelPercent == null) result += "Уровень топлива" to pidReason(Pid.FUEL_LEVEL)
        if (s.fuelLiters == null) result += "Остаток топлива (л)" to "нет уровня топлива в %"
        if (t.fuelRateLph == null) {
            result += "Расход" to when {
                s.profile.fuelType == com.carscan.data.FuelType.DIESEL -> "дизель: нужен PID 5E (${pidReason(Pid.FUEL_RATE)})"
                else -> "нет PID 5E, MAF и MAP/оборотов (${pidReason(Pid.MAF)}; ${pidReason(Pid.INTAKE_PRESSURE)})"
            }
        }
        if (s.trip.avgConsumptionL100 == null) {
            result += "Средний расход" to "проехано ${fmt(s.trip.distanceKm, 2)} км (нужно ≥ 0.1 км со скоростью)"
        }
        if (s.rangeKm == null) {
            result += "Запас хода" to when {
                s.fuelLiters == null -> "нет остатка топлива в литрах (см. «Уровень топлива»)"
                s.trip.avgConsumptionL100 == null -> "нет среднего расхода"
                else -> "средний расход ${fmt(s.trip.avgConsumptionL100, 2)} л/100 км — слишком мал (≤ 0.5)"
            }
        }
        if (t.coolantTemp == null) result += "Темп. ОЖ" to pidReason(Pid.COOLANT_TEMP)
        if (t.voltage == null) result += "Напряжение" to "ни AT RV, ни PID 42 не ответили"
        if (t.atfTemp == null) result += "Темп. АКПП" to "в стандартном OBD-II нет, команда производителя не задана"
        if (t.cabinTemp == null) result += "Темп. салона" to "в стандартном OBD-II нет, команда производителя не задана"
        if (t.ambientAirTemp == null) result += "Темп. наружная (ТС)" to pidReason(Pid.AMBIENT_AIR_TEMP)
        if (s.outsideTempC == null) result += "Темп. на улице" to (s.weather.error ?: "нет ни интернета, ни датчика ТС")
        return result
    }

    private val KEY_PIDS = setOf(
        Pid.RPM, Pid.SPEED, Pid.COOLANT_TEMP, Pid.FUEL_LEVEL, Pid.MAF, Pid.INTAKE_PRESSURE, Pid.FUEL_RATE, Pid.AMBIENT_AIR_TEMP,
    )
    private val KEY_DISPLAY = setOf("Уровень топлива", "Остаток топлива (л)", "Расход", "Запас хода", "Темп. на улице")

    private fun hex(n: Int) = String.format(Locale.US, "%02X", n)
    private fun fmt(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v)
    private fun signed(v: Double) = (if (v > 0.05) "+" else "") + fmt(v, 1) + " °C"
    private fun time(ms: Long, zone: TimeZone, pattern: String) =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(ms))
}
