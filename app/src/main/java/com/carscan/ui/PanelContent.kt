package com.carscan.ui

import androidx.compose.ui.graphics.Color
import com.carscan.data.ConnectionState
import com.carscan.data.DataSourceType
import com.carscan.data.FuelType
import com.carscan.data.ProbeVerdict
import com.carscan.data.UpdateRepository
import com.carscan.data.WeatherRepository
import com.carscan.ui.theme.Palettes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Описание правой панели «Информация о ТС» — без Compose, чтобы его можно было тестировать. */
data class PanelSpec(
    val caption: String,
    val title: String,
    val chip: String? = null,
    val sections: List<PanelSection>,
    val actions: List<PanelAction> = emptyList(),
    val closable: Boolean = false,
)

/**
 * Блок панели. Без [title] — просто содержимое без заголовка и сворачивания.
 * [choices] — варианты выбора (тема, источник данных, адаптер), выбранный подсвечивается.
 */
data class PanelSection(
    val title: String? = null,
    val rows: List<PanelRow> = emptyList(),
    val text: String? = null,
    val expanded: Boolean = true,
    val choiceGroup: ChoiceGroup? = null,
    val choices: List<PanelChoice> = emptyList(),
    val monospace: Boolean = false,
    val steppers: List<PanelStepper> = emptyList(),
    val actions: List<PanelAction> = emptyList(),
)

/** Значение с кнопками «−/+». [modified] — отличается от справочника. */
data class PanelStepper(val id: StepperId, val label: String, val value: String, val modified: Boolean)

enum class StepperId { DISPLACEMENT, TANK, VE }

data class PanelRow(val param: String, val value: String, val level: StatusLevel? = null)

/** Вариант выбора. [swatch] — цвета образца для выбора темы. */
data class PanelChoice(
    val id: String,
    val label: String,
    val selected: Boolean,
    val hint: String? = null,
    val swatch: List<Color>? = null,
)

enum class ChoiceGroup { DATA_SOURCE, OBD_DEVICE, THEME, VIEW_MODE, FUEL_TYPE, VEHICLE_MAKE, VEHICLE_MODEL, VEHICLE_VARIANT }

enum class PanelAction {
    XRAY, FOCUS, REFRESH_WEATHER, RESET_TRIP, OBD_LOG, REFRESH_DEVICES, BACK_TO_SETTINGS,
    PICK_VEHICLE, PICKER_BACK, RESET_VEHICLE, CHECK_UPDATES, INSTALL_UPDATE,
    SELF_TEST, SEND_REPORT, SHARE_REPORT,
}

object PanelContent {

    fun build(state: DashboardState): PanelSpec = when (state.selectedPanel) {
        ActivePanel.MAIN -> main(state)
        ActivePanel.SENSORS_ALL -> sensors(state)
        ActivePanel.DIAGNOSTICS_ECU -> ecu(state)
        ActivePanel.EMISSIONS -> emissions(state)
        ActivePanel.NODE -> state.selectedNode?.let { node(state, it) } ?: main(state)
        ActivePanel.SETTINGS -> settings(state)
        ActivePanel.OBD_LOG -> obdLog(state)
        ActivePanel.VEHICLE_PICKER -> vehiclePicker(state)
    }

    private fun main(s: DashboardState): PanelSpec {
        val t = s.telemetry
        val statuses = s.nodeStatuses.associateBy { it.node }
        return PanelSpec(
            caption = "ГЛАВНАЯ ПАНЕЛЬ",
            title = "Информация о ТС",
            chip = s.profile.name + " · " + s.profile.engine,
            actions = listOf(PanelAction.RESET_TRIP),
            sections = listOf(
                PanelSection(
                    "Основные показатели",
                    listOf(
                        PanelRow("Темп. ОЖ", t.coolantTemp.orDash(" °C"), statuses[CarNode.ENGINE]?.level),
                        PanelRow("Темп. масла АКПП", t.atfTemp.orDash(" °C"), statuses[CarNode.TRANSMISSION]?.level),
                        PanelRow("Топливо в баке", fuelText(s), statuses[CarNode.FUEL]?.level),
                        PanelRow("Средний расход", s.trip.avgConsumptionL100.orDash(" л/100 км", 1)),
                        PanelRow("Напряжение сети", t.voltage.orDash(" V", 2), statuses[CarNode.BATTERY]?.level),
                        PanelRow("Темп. в салоне", t.cabinTemp.orDash(" °C")),
                        PanelRow("Темп. на улице", signedTemp(s.outsideTempC) + outsideSourceSuffix(s)),
                    ),
                ),
                PanelSection(
                    "Поездка",
                    listOf(
                        PanelRow("Пробег", s.trip.distanceKm.fmt(1) + " км"),
                        PanelRow("Время в пути", formatDuration(s.trip.durationSec)),
                        PanelRow("Средняя скорость", s.trip.avgSpeedKmh.orDash(" км/ч")),
                        PanelRow("Израсходовано", s.trip.fuelUsedL.fmt(2) + " л"),
                        PanelRow("Мгновенный расход", instantConsumption(s)),
                        PanelRow("Запас хода", s.rangeKm?.let { "≈ ${it.roundToInt()} км" } ?: NO_VALUE),
                    ),
                ),
                PanelSection(
                    "Состояние систем",
                    s.nodeStatuses.map { PanelRow(it.node.title, it.status, it.level) },
                    expanded = false,
                ),
            ),
        )
    }

    private fun sensors(s: DashboardState): PanelSpec {
        val t = s.telemetry
        return PanelSpec(
            caption = "ВСЕ ДАТЧИКИ",
            title = "Текущие параметры",
            chip = s.dataSourceName,
            sections = listOf(
                PanelSection(
                    "Двигатель",
                    listOf(
                        PanelRow("Обороты", t.rpm.orDash(" об/мин")),
                        PanelRow("Нагрузка", t.engineLoad.orDash(" %", 1)),
                        PanelRow("Темп. ОЖ", t.coolantTemp.orDash(" °C")),
                        PanelRow("Темп. впуска", t.intakeAirTemp.orDash(" °C")),
                        PanelRow("Давл. в коллекторе", t.intakePressureKpa.orDash(" кПа")),
                    ),
                ),
                PanelSection(
                    "Топливо",
                    listOf(
                        PanelRow("Уровень", t.fuelLevelPercent.orDash(" %", 1)),
                        PanelRow("Расход", t.fuelRateLph.orDash(" л/ч", 2)),
                        PanelRow("Расчёт расхода", t.fuelRateMethod ?: NO_VALUE),
                        PanelRow("Кратк. коррекция", t.shortTermFuelTrim.orDash(" %", 2)),
                        PanelRow("Долг. коррекция", t.longTermFuelTrim.orDash(" %", 2)),
                        PanelRow("Топливная система", t.fuelSystemStatus ?: NO_VALUE),
                    ),
                ),
                PanelSection(
                    "Трансмиссия и прочее",
                    listOf(
                        PanelRow("Скорость", t.speedKmh.orDash(" км/ч")),
                        PanelRow("Передача", t.gear ?: NO_VALUE),
                        PanelRow("Темп. ATF", t.atfTemp.orDash(" °C")),
                        PanelRow("Напряжение (ELM327)", t.voltage.orDash(" V", 2)),
                        PanelRow("Улица (датчик ТС)", signedTemp(t.ambientAirTemp?.toDouble())),
                        PanelRow("Салон", t.cabinTemp.orDash(" °C", 1)),
                    ),
                ),
            ),
        )
    }

    private fun ecu(s: DashboardState): PanelSpec {
        val t = s.telemetry
        val misfireRows = t.misfires.mapIndexed { i, count ->
            PanelRow("Цилиндр ${i + 1}", count.toString(), if (count == 0) StatusLevel.OK else StatusLevel.WARN)
        }
        return PanelSpec(
            caption = "МОНИТОРИНГ ЭБУ",
            title = "Пропуски зажигания",
            chip = "Mode 06",
            sections = listOf(
                PanelSection("Счётчики по цилиндрам", misfireRows.ifEmpty { listOf(PanelRow("Данные", NO_VALUE)) }),
                PanelSection(
                    "Ошибки",
                    listOf(
                        PanelRow(
                            "Check Engine",
                            if (t.checkEngineOn) "Горит" else "Не горит",
                            if (t.checkEngineOn) StatusLevel.WARN else StatusLevel.OK,
                        ),
                        PanelRow("Коды DTC", t.dtcCount.toString(), if (t.dtcCount == 0) StatusLevel.OK else StatusLevel.WARN),
                    ),
                ),
            ),
        )
    }

    private fun emissions(s: DashboardState): PanelSpec = PanelSpec(
        caption = "ТЕСТЫ НА ВЫБРОСЫ",
        title = "Готовность систем",
        chip = "С момента сброса DTC",
        sections = listOf(
            PanelSection(
                "Мониторы OBD-II",
                s.telemetry.readiness.map {
                    PanelRow(it.name, if (it.passed) "ПРОЙДЕН" else "НЕ ПРОЙДЕН", if (it.passed) StatusLevel.OK else StatusLevel.CRIT)
                }.ifEmpty { listOf(PanelRow("Данные", NO_VALUE)) },
            ),
        ),
    )

    private fun node(s: DashboardState, node: CarNode): PanelSpec {
        val t = s.telemetry
        val status = CarNodes.evaluate(s, node)
        val statusRow = PanelRow("Состояние", status.status, status.level)
        val (rows, purpose) = when (node) {
            CarNode.ENGINE -> listOf(
                statusRow,
                PanelRow("Темп. ОЖ", t.coolantTemp.orDash(" °C"), status.level),
                PanelRow("Обороты", t.rpm.orDash(" об/мин")),
                PanelRow("Нагрузка", t.engineLoad.orDash(" %", 1)),
                PanelRow("Темп. впуска", t.intakeAirTemp.orDash(" °C")),
                PanelRow("Давл. во впуске", t.intakePressureKpa.orDash(" кПа")),
            ) to "Контроль теплового режима и нагрузки двигателя. Рабочая температура охлаждающей жидкости обычно 85–105 °C."
            CarNode.TRANSMISSION -> listOf(
                statusRow,
                PanelRow("Передача", t.gear ?: NO_VALUE),
                PanelRow("Темп. ATF", t.atfTemp.orDash(" °C"), status.level),
                PanelRow("Скорость", t.speedKmh.orDash(" км/ч")),
                PanelRow("Обороты", t.rpm.orDash(" об/мин")),
            ) to "Температура масла АКПП читается по протоколу производителя (в стандартном OBD-II её нет). Рабочий диапазон обычно 70–100 °C."
            CarNode.FUEL -> listOf(
                PanelRow("Уровень", t.fuelLevelPercent.orDash(" %", 1), status.level),
                PanelRow("Остаток", s.fuelLiters.orDash(" л", 1)),
                PanelRow("Объём бака", s.profile.tankLiters.fmt(0) + " л"),
                PanelRow("Средний расход", s.trip.avgConsumptionL100.orDash(" л/100 км", 1)),
                PanelRow("Расчёт расхода", t.fuelRateMethod ?: NO_VALUE),
                PanelRow("Мгновенный расход", instantConsumption(s)),
                PanelRow("Запас хода", s.rangeKm?.let { "≈ ${it.roundToInt()} км" } ?: NO_VALUE),
                PanelRow("За поездку", s.trip.fuelUsedL.fmt(2) + " л"),
            ) to "Уровень топлива (PID 2F) пересчитывается в литры по объёму бака. Средний расход считает бортовой компьютер приложения с начала поездки."
            CarNode.BATTERY -> listOf(
                statusRow,
                PanelRow("Напряжение сети", t.voltage.orDash(" V", 2), status.level),
                PanelRow(
                    "Двигатель",
                    when {
                        t.rpm == null -> NO_VALUE
                        t.rpm > CarNodes.ENGINE_RUNNING_RPM -> "Работает"
                        else -> "Заглушен"
                    },
                ),
            ) to "Напряжение бортовой сети (команда ELM327 AT RV). При работающем двигателе норма 13.2–14.8 В, при заглушенном — 12.4–12.8 В."
            CarNode.CLIMATE -> {
                val w = s.weather.current
                listOf(
                    PanelRow("Темп. в салоне", t.cabinTemp.orDash(" °C", 1)),
                    PanelRow("Темп. на улице", signedTemp(w?.temperatureC)),
                    PanelRow("Погода", w?.let { WeatherRepository.describeWeatherCode(it.weatherCode) } ?: NO_VALUE),
                    PanelRow("Местоположение", w?.let { listOfNotNull(it.place, it.locationSource.label).joinToString(" · ") } ?: NO_VALUE),
                    PanelRow("Обновлено", w?.let { timeOf(it.fetchedAtMs) } ?: (s.weather.error ?: NO_VALUE)),
                    PanelRow("Датчик ТС (улица)", signedTemp(t.ambientAirTemp?.toDouble())),
                ) to "Уличная температура берётся из интернета (Open-Meteo) по местоположению устройства и обновляется раз в 15 минут. Температура салона читается с датчика климат-контроля."
            }
            CarNode.EXHAUST -> listOf(statusRow) + t.readiness.map {
                PanelRow(it.name, if (it.passed) "Пройден" else "Не пройден", if (it.passed) StatusLevel.OK else StatusLevel.CRIT)
            } to "Готовность систем контроля выбросов (OBD-II, Mode 01 PID 01)."
        }
        return PanelSpec(
            caption = "ИНФОРМАЦИЯ О ДЕТАЛИ",
            title = node.title,
            chip = "УЗЕЛ: ${node.id}",
            actions = listOf(PanelAction.XRAY, PanelAction.FOCUS),
            closable = true,
            sections = listOf(
                PanelSection(rows = rows),
                PanelSection(text = purpose),
            ),
        )
    }

    private fun settings(s: DashboardState): PanelSpec {
        val w = s.weather
        val bluetooth = s.settings.dataSource == DataSourceType.BLUETOOTH
        return PanelSpec(
            caption = "НАСТРОЙКИ",
            title = "Параметры",
            actions = listOf(PanelAction.OBD_LOG, PanelAction.REFRESH_DEVICES, PanelAction.REFRESH_WEATHER),
            closable = true,
            sections = listOf(
                vehicleSection(s),
                diagnosticsSection(s),
                updateSection(s),
                PanelSection(
                    "Источник данных",
                    rows = listOf(
                        PanelRow("Состояние", s.connection.message.ifEmpty { NO_VALUE }, connectionLevel(s)),
                        PanelRow("Поток данных", if (s.streaming) "Включён" else "Выключен", if (s.streaming) StatusLevel.OK else StatusLevel.OFF),
                    ),
                    choiceGroup = ChoiceGroup.DATA_SOURCE,
                    choices = DataSourceType.entries.map {
                        PanelChoice(it.name, it.title, selected = it == s.settings.dataSource)
                    },
                ),
                PanelSection(
                    "Адаптер ELM327",
                    text = when {
                        s.pairedDevices.isNotEmpty() -> if (bluetooth) null else "Выберите адаптер и переключите источник на Bluetooth."
                        s.bluetoothIssue != null -> s.bluetoothIssue + ". Нажмите «Адаптеры»."
                        else -> "Нет сопряжённых устройств. Сопрягите адаптер в настройках Bluetooth Android " +
                            "(PIN обычно 1234 или 0000) и нажмите «Адаптеры»."
                    },
                    choiceGroup = ChoiceGroup.OBD_DEVICE,
                    choices = s.pairedDevices.map {
                        PanelChoice(
                            id = it.address,
                            label = it.name,
                            selected = it.address == s.settings.obdDeviceAddress,
                            hint = it.address + if (it.looksLikeObd) " · похоже на OBD" else "",
                        )
                    },
                    expanded = bluetooth,
                ),
                PanelSection(
                    "Цветовая гамма",
                    choiceGroup = ChoiceGroup.THEME,
                    choices = Palettes.all.map {
                        PanelChoice(it.id, it.title, selected = it.id == s.settings.themeId, swatch = listOf(it.accent, it.active))
                    },
                ),
                PanelSection(
                    "Отображение машины",
                    choiceGroup = ChoiceGroup.VIEW_MODE,
                    choices = listOf(
                        PanelChoice(MainViewModel.VIEW_XRAY, "Рентген", selected = s.xrayMode),
                        PanelChoice(MainViewModel.VIEW_NORMAL, "Обычный вид", selected = !s.xrayMode),
                    ),
                ),
                PanelSection(
                    "Погода",
                    listOf(
                        PanelRow("Сервис", "Open-Meteo"),
                        PanelRow(
                            "Местоположение",
                            w.current?.let { listOfNotNull(it.place, it.locationSource.label).joinToString(" · ") } ?: NO_VALUE,
                        ),
                        PanelRow(
                            "Обновлено",
                            when {
                                w.loading -> "Обновление…"
                                w.current != null -> timeOf(w.current.fetchedAtMs)
                                else -> NO_VALUE
                            },
                        ),
                        PanelRow("Статус", w.error ?: "OK", if (w.error != null) StatusLevel.WARN else StatusLevel.OK),
                    ),
                    expanded = false,
                ),
            ),
        )
    }

    private fun vehicleSection(s: DashboardState): PanelSection {
        val p = s.profile
        val o = s.settings
        return PanelSection(
            "Автомобиль",
            rows = listOf(
                PanelRow("Марка и модель", p.name),
                PanelRow("Поколение", p.generation),
                PanelRow("Двигатель", p.engine + (p.powerHp?.let { " · $it л.с." } ?: "")),
                PanelRow("Цилиндров", p.cylinders.toString()),
            ),
            steppers = listOf(
                PanelStepper(StepperId.DISPLACEMENT, "Объём двигателя", p.displacementL.fmt(1) + " л", o.displacementOverride != null),
                PanelStepper(StepperId.TANK, "Объём бака", p.tankLiters.fmt(0) + " л", o.tankOverride != null),
                PanelStepper(StepperId.VE, "Коэф. наполнения", (p.volumetricEfficiency * 100).fmt(0) + " %", o.veOverride != null),
            ),
            choiceGroup = ChoiceGroup.FUEL_TYPE,
            choices = FuelType.entries.map { PanelChoice(it.name, it.title, selected = it == p.fuelType) },
            actions = listOfNotNull(PanelAction.PICK_VEHICLE, PanelAction.RESET_VEHICLE.takeIf { o.hasVehicleOverrides }),
            text = "Бак нужен для пересчёта % в литры. Объём двигателя, тип топлива и коэффициент наполнения — " +
                "для расчёта расхода, если у машины нет датчика MAF. Значения, изменённые вручную, отмечены «•».",
        )
    }

    private fun diagnosticsSection(s: DashboardState): PanelSection {
        val d = s.diagnostics
        val probes = s.connection.selfTest
        val bad = probes.count { it.verdict != ProbeVerdict.OK }
        return PanelSection(
            "Отладка",
            rows = listOfNotNull(
                PanelRow(
                    "Telegram-бот",
                    if (d.telegramConfigured) "Настроен" else "Не настроен",
                    if (d.telegramConfigured) StatusLevel.OK else StatusLevel.OFF,
                ),
                PanelRow(
                    "Проверка датчиков",
                    when {
                        probes.isEmpty() -> NO_VALUE
                        bad == 0 -> "Все ${probes.size} в порядке"
                        else -> "Замечаний: $bad из ${probes.size}"
                    },
                    when {
                        probes.isEmpty() -> null
                        bad == 0 -> StatusLevel.OK
                        else -> StatusLevel.WARN
                    },
                ),
                d.message?.let { PanelRow("Статус", it, if (d.isError) StatusLevel.WARN else StatusLevel.OK) },
            ),
            actions = if (d.busy) emptyList() else listOf(PanelAction.SEND_REPORT, PanelAction.SELF_TEST, PanelAction.SHARE_REPORT),
            text = "Отчёт: проверка всех датчиков, текущие показания, почему пустые значения пустые, погода " +
                "и история поездки. Его можно отправить в любой момент, в том числе на ходу. " +
                if (d.telegramConfigured) "Отчёт уходит файлом в ваш Telegram-бот." else "Без бота — через «Поделиться».",
            expanded = d.message != null,
        )
    }

    private fun updateSection(s: DashboardState): PanelSection {
        val u = s.update
        val latest = u.latest
        val rows = listOfNotNull(
            PanelRow("Установлена", u.installedVersion.ifEmpty { NO_VALUE }),
            PanelRow(
                "На GitHub",
                when {
                    u.checking -> "Проверка…"
                    latest != null -> latest.version
                    else -> NO_VALUE
                },
                if (u.available) StatusLevel.INFO else null,
            ),
            u.downloadProgress?.let { PanelRow("Загрузка", (it * 100.0).fmt(0) + " %", StatusLevel.INFO) },
            u.message?.let { PanelRow("Статус", it, if (u.isError) StatusLevel.WARN else if (u.available) StatusLevel.INFO else StatusLevel.OK) },
        )
        return PanelSection(
            title = if (u.available) "Обновление ${latest?.version}" else "Обновления",
            rows = rows,
            actions = listOfNotNull(
                PanelAction.CHECK_UPDATES,
                PanelAction.INSTALL_UPDATE.takeIf { u.available && u.downloadProgress == null },
            ),
            text = when {
                u.isError && u.message != null && u.message.length > 40 -> u.message
                u.available && !latest?.notes.isNullOrBlank() -> latest!!.notes.take(400)
                else -> "Новые версии публикуются в релизах GitHub: ${UpdateRepository.RELEASES_PAGE}"
            },
            expanded = u.available || u.downloadProgress != null,
        )
    }

    /** Выбор автомобиля в три шага, как профили подключения в Car Scanner. */
    private fun vehiclePicker(s: DashboardState): PanelSpec {
        val picker = s.vehiclePicker
        val current = s.settings.vehicleId
        val p = s.profile
        val (title, step, section) = when {
            picker.make == null -> Triple(
                "Марка",
                "Шаг 1 из 3",
                PanelSection(
                    choiceGroup = ChoiceGroup.VEHICLE_MAKE,
                    choices = picker.makes.map { PanelChoice(it, it, selected = it == p.make) },
                ),
            )
            picker.modelKey == null -> Triple(
                picker.make,
                "Шаг 2 из 3: модель",
                PanelSection(
                    choiceGroup = ChoiceGroup.VEHICLE_MODEL,
                    choices = picker.models.map {
                        PanelChoice(
                            id = it.key,
                            label = it.model,
                            selected = picker.make == p.make && it.model == p.model && it.generation == p.generation,
                            hint = it.generation + " · " + plural(it.variants, "двигатель", "двигателя", "двигателей"),
                        )
                    },
                ),
            )
            else -> Triple(
                picker.make + " " + picker.modelKey.substringBefore('|'),
                "Шаг 3 из 3: двигатель",
                PanelSection(
                    choiceGroup = ChoiceGroup.VEHICLE_VARIANT,
                    choices = picker.variants.map {
                        PanelChoice(it.id, "${it.engine} · ${it.powerHp} л.с.", selected = it.id == current, hint = it.summary)
                    },
                ),
            )
        }
        return PanelSpec(
            caption = "ВЫБОР АВТОМОБИЛЯ",
            title = title,
            chip = step,
            actions = listOf(PanelAction.PICKER_BACK),
            closable = true,
            sections = listOf(section),
        )
    }

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val word = when {
            n % 10 == 1 && n % 100 != 11 -> one
            n % 10 in 2..4 && n % 100 !in 12..14 -> few
            else -> many
        }
        return "$n $word"
    }

    /** Журнал обмена с адаптером: сырые ответы рядом с расшифровкой — для сверки формул на машине. */
    private fun obdLog(s: DashboardState): PanelSpec {
        val c = s.connection
        val pids = c.supportedPids.sorted().joinToString(" ") { String.format(Locale.US, "%02X", it) }
        val log = c.log.asReversed().joinToString("\n") { e ->
            e.command + " → " + e.response + (e.decoded?.let { " = $it" } ?: "")
        }
        return PanelSpec(
            caption = "ДИАГНОСТИКА АДАПТЕРА",
            title = "Журнал OBD",
            chip = s.dataSourceName,
            actions = listOf(PanelAction.BACK_TO_SETTINGS, PanelAction.SELF_TEST, PanelAction.SEND_REPORT),
            closable = true,
            sections = listOf(
                PanelSection(
                    "Проверка датчиков",
                    rows = c.selfTest.map { probe ->
                        PanelRow(
                            probe.name,
                            probe.decoded?.let { "$it · ${probe.verdict.label}" } ?: probe.verdict.label,
                            when (probe.verdict) {
                                ProbeVerdict.OK -> StatusLevel.OK
                                ProbeVerdict.UNDECLARED -> StatusLevel.INFO
                                ProbeVerdict.SUSPICIOUS, ProbeVerdict.NO_DATA -> StatusLevel.WARN
                                ProbeVerdict.NOT_SUPPORTED -> StatusLevel.OFF
                                ProbeVerdict.ERROR -> StatusLevel.CRIT
                            },
                        )
                    }.ifEmpty { listOf(PanelRow("Результат", "Появится после подключения")) },
                ),
                PanelSection(
                    "Подключение",
                    listOf(
                        PanelRow("Состояние", c.message.ifEmpty { NO_VALUE }, connectionLevel(s)),
                        PanelRow("Адаптер (AT I)", c.adapter ?: NO_VALUE),
                        PanelRow("Протокол", c.protocol ?: NO_VALUE),
                        PanelRow("Поддерж. PID", if (c.supportedPids.isEmpty()) NO_VALUE else c.supportedPids.size.toString()),
                        PanelRow("Напряжение (AT RV)", s.telemetry.voltage.orDash(" V", 1)),
                    ),
                ),
                PanelSection("PID режима 01", text = pids.ifEmpty { "Появятся после подключения" }, monospace = true),
                PanelSection(
                    "Обмен (новые сверху)",
                    text = log.ifEmpty {
                        if (s.settings.dataSource == DataSourceType.SIMULATOR) "Журнал ведётся только при подключении к ELM327"
                        else "Пока пусто"
                    },
                    monospace = true,
                ),
            ),
        )
    }

    private fun connectionLevel(s: DashboardState): StatusLevel = when (s.connection.state) {
        ConnectionState.CONNECTED -> StatusLevel.OK
        ConnectionState.CONNECTING, ConnectionState.INITIALIZING -> StatusLevel.INFO
        ConnectionState.ERROR -> StatusLevel.CRIT
        ConnectionState.IDLE -> StatusLevel.OFF
    }

    private fun fuelText(s: DashboardState): String {
        val liters = s.fuelLiters ?: return NO_VALUE
        val pct = s.telemetry.fuelLevelPercent ?: return NO_VALUE
        return "${liters.fmt(1)} л · ${pct.roundToInt()} %"
    }

    private fun instantConsumption(s: DashboardState): String = when {
        s.trip.instantL100 != null -> s.trip.instantL100.fmt(1) + " л/100 км"
        s.trip.instantLph != null -> s.trip.instantLph.fmt(1) + " л/ч"
        else -> NO_VALUE
    }

    private fun outsideSourceSuffix(s: DashboardState): String = when {
        s.weather.current != null -> " (интернет)"
        s.telemetry.ambientAirTemp != null -> " (датчик ТС)"
        else -> ""
    }

    private fun timeOf(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
}
