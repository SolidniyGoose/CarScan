package com.carscan.data.obd

import com.carscan.data.ConnectionInfo
import com.carscan.data.ConnectionState
import com.carscan.data.ExtendedPid
import com.carscan.data.FuelType
import com.carscan.data.ObdLogEntry
import com.carscan.data.ProbeVerdict
import com.carscan.data.SensorProbe
import com.carscan.data.TelemetrySource
import com.carscan.data.VehicleProfile
import com.carscan.data.VehicleTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Реальный автомобиль через адаптер ELM327 (стандартные PID SAE J1979).
 *
 * Опрос по группам: обороты/скорость/нагрузка/расход — каждый цикл, температуры и коррекции — каждый 5-й,
 * уровень топлива, напряжение, Check Engine — каждый 20-й, пропуски зажигания (режим 06) — каждый 60-й.
 * PID, которые ЭБУ не поддерживает, не запрашиваются: значение остаётся null и на экране «—».
 */
class ObdTelemetrySource(
    /** Текущий профиль: пользователь может сменить автомобиль в настройках на ходу. */
    private val profile: () -> VehicleProfile,
    private val openTransport: suspend () -> ObdTransport,
) : TelemetrySource {

    override val name: String = "OBD2 (ELM327)"

    private val _connection = MutableStateFlow(ConnectionInfo())
    override val connection: StateFlow<ConnectionInfo> = _connection.asStateFlow()

    private val selfTestRequested = AtomicBoolean(false)

    override fun requestSelfTest() = selfTestRequested.set(true)

    override fun samples(): Flow<VehicleTelemetry> = flow {
        _connection.value = ConnectionInfo(ConnectionState.CONNECTING, "Подключение к адаптеру…")
        val transport = try {
            openTransport()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
            throw e
        }
        try {
            val session = Session(Elm327(transport.input, transport.output))
            session.initialize()
            session.selfTest()
            while (true) {
                if (selfTestRequested.getAndSet(false)) session.selfTest()
                emit(session.poll())
                _connection.update { it.copy(log = session.logSnapshot()) }
            }
        } catch (e: CancellationException) {
            _connection.update { it.copy(state = ConnectionState.IDLE, message = "Отключено") }
            throw e
        } catch (e: Exception) {
            fail(e)
            throw e
        } finally {
            runCatching { transport.close() }
        }
    }.flowOn(Dispatchers.IO)

    private fun fail(e: Exception) {
        val message = (e as? ObdException)?.message ?: "Ошибка связи: ${e.message ?: e.javaClass.simpleName}"
        _connection.update { it.copy(state = ConnectionState.ERROR, message = message) }
    }

    private inner class Session(private val elm: Elm327) {
        private val log = ArrayDeque<ObdLogEntry>()
        private var supported: MutableSet<Int> = mutableSetOf()
        private val noDataCount = mutableMapOf<Int, Int>()
        private val disabled = mutableSetOf<Int>()
        private var protocol: Int? = null
        private var diesel = false
        private var cylinderMids: List<Int> = emptyList()
        private var adapterVoltageWorks = true
        private var cycle = 0L
        private var t = VehicleTelemetry()

        fun logSnapshot(): List<ObdLogEntry> = log.toList()

        private suspend fun send(command: String, timeoutMs: Long = Elm327.DEFAULT_TIMEOUT_MS): ElmResponse =
            elm.send(command, timeoutMs).also { record(it, null) }

        private fun record(r: ElmResponse, decoded: String?, replaceLast: Boolean = false) {
            val response = r.error ?: r.lines.joinToString(" | ")
            if (replaceLast && log.lastOrNull()?.command == r.command) log.removeLast()
            log.addLast(ObdLogEntry(r.command, response, decoded))
            while (log.size > LOG_SIZE) log.removeFirst()
        }

        private fun status(state: ConnectionState, message: String) {
            _connection.update { it.copy(state = state, message = message, log = logSnapshot()) }
        }

        suspend fun initialize() {
            status(ConnectionState.INITIALIZING, "Сброс адаптера…")
            send("ATZ", 5_000)
            // Эхо и переводы строк выключены, пробелы и заголовки тоже: разбирать проще и быстрее
            for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATAT1", "ATSP0")) send(cmd)
            val adapter = send("ATI").lines.firstOrNull()
            _connection.update { it.copy(adapter = adapter) }

            status(ConnectionState.INITIALIZING, "Поиск протокола автомобиля…")
            // Первый запрос запускает автоопределение протокола: на старых шинах это до ~10 секунд
            val first = send("0100", 15_000)
            if (!first.ok) {
                throw ObdException(
                    when {
                        first.error == "UNABLE TO CONNECT" -> "ЭБУ не отвечает: включите зажигание или заведите двигатель"
                        first.isNoData -> "ЭБУ не ответил (NO DATA): включите зажигание"
                        else -> "Ошибка адаптера: ${first.error}"
                    },
                )
            }
            supported = discoverSupported(first)

            protocol = send("ATDPN").lines.firstOrNull()?.trim()?.removePrefix("A")?.toIntOrNull(16)
            pid(Pid.MONITOR_STATUS, ObdDecode::monitorStatus)?.let { diesel = it.compressionIgnition }
            if (isCan) cylinderMids = discoverMisfireMonitors()

            _connection.update {
                it.copy(
                    state = ConnectionState.CONNECTED,
                    message = "Подключено",
                    protocol = protocolName(protocol),
                    supportedPids = supported,
                    log = logSnapshot(),
                )
            }
        }

        private suspend fun discoverSupported(first: ElmResponse): MutableSet<Int> {
            val result = ObdParser.mode01All(first.lines, Pid.SUPPORTED_01_20)
                .flatMap { ObdDecode.supportedNumbers(0x00, it) }.toMutableSet()
            record(first, "${result.size} PID в диапазоне 01–20", replaceLast = true)
            var base = Pid.SUPPORTED_21_40
            while (base <= Pid.SUPPORTED_A1_C0 && base in result) {
                val r = elm.send(String.format(Locale.US, "01%02X", base))
                val found = if (r.ok) ObdParser.mode01All(r.lines, base).flatMap { ObdDecode.supportedNumbers(base, it) } else emptyList()
                record(r, "${found.size} PID в диапазоне ${hex(base + 1)}–${hex(base + 0x20)}")
                result += found
                base += 0x20
            }
            return result
        }

        private suspend fun discoverMisfireMonitors(): List<Int> {
            val r = elm.send("06A0")
            val mids = ObdParser.messages(r.lines)
                .filter { it.size >= 6 && it[0] == 0x46 && it[1] == 0xA0 }
                .flatMap { ObdDecode.supportedNumbers(0xA0, it.copyOfRange(2, 6)) }
                .filter { it in 0xA2..0xAD } // MID A2…AD — цилиндры 1…12
                .sorted()
            record(r, if (mids.isEmpty()) "режим 06 не поддерживается" else "цилиндров с мониторингом: ${mids.size}")
            return mids
        }

        private val isCan: Boolean get() = protocol in 6..9

        /**
         * Каждый нужный PID запрашивается напрямую, даже если ЭБУ его не заявил: так видно,
         * почему на экране «—». PID, который ответил без заявления, дальше опрашивается как обычно.
         */
        suspend fun selfTest() {
            status(ConnectionState.CONNECTED, "Проверка датчиков…")
            val declaredMask = supported.toSet()
            val probes = PROBES.map { probe ->
                val command = String.format(Locale.US, "01%02X", probe.pid)
                val declared = probe.pid in declaredMask
                val r = elm.send(command, PROBE_TIMEOUT_MS)
                val response = r.error ?: r.lines.joinToString(" | ")
                val data = if (r.ok) ObdParser.mode01(r.lines, probe.pid) else null
                val value = data?.let { runCatching { probe.decode(it) }.getOrNull() }
                record(r, value?.let { "${probe.format(it)} (проверка)" })
                val verdict = when {
                    value == null && !declared -> ProbeVerdict.NOT_SUPPORTED
                    value == null -> if (r.isNoData || data != null) ProbeVerdict.NO_DATA else ProbeVerdict.ERROR
                    !declared -> ProbeVerdict.UNDECLARED
                    probe.range != null && value !in probe.range -> ProbeVerdict.SUSPICIOUS
                    else -> ProbeVerdict.OK
                }
                if (value != null) {
                    // Ответил — значит, можно опрашивать (и снова, если раньше отключили после NO DATA)
                    supported += probe.pid
                    disabled -= probe.pid
                    noDataCount -= probe.pid
                }
                SensorProbe(
                    name = probe.name,
                    command = command,
                    declared = declared,
                    response = response,
                    decoded = value?.let(probe.format),
                    verdict = verdict,
                    note = probe.range?.takeIf { verdict == ProbeVerdict.SUSPICIOUS }
                        ?.let { "ожидается ${probe.format(it.start)}…${probe.format(it.endInclusive)}" },
                )
            } + adapterVoltageProbe() + headerProbes()
            _connection.update {
                it.copy(
                    state = ConnectionState.CONNECTED,
                    message = "Подключено",
                    supportedPids = supported.toSet(),
                    selfTest = probes,
                    selfTestAtMs = System.currentTimeMillis(),
                    log = logSnapshot(),
                )
            }
        }

        private suspend fun adapterVoltageProbe(): SensorProbe {
            val r = elm.send("ATRV")
            val volts = r.lines.firstOrNull()?.let(ObdDecode::adapterVoltage)
            record(r, volts?.let { String.format(Locale.US, "%.1f V (проверка)", it) })
            return SensorProbe(
                name = "Напряжение (адаптер)",
                command = "ATRV",
                declared = null,
                response = r.error ?: r.lines.joinToString(" | "),
                decoded = volts?.let { String.format(Locale.US, "%.2f V", it) },
                verdict = when {
                    volts == null -> ProbeVerdict.ERROR
                    volts !in 9.0..16.0 -> ProbeVerdict.SUSPICIOUS
                    else -> ProbeVerdict.OK
                },
            )
        }

        /** Какие блоки отвечают (с заголовками): видно, от кого приходит уровень топлива и есть ли он вообще. */
        private suspend fun headerProbes(): List<SensorProbe> = try {
            send("ATH1")
            listOf("0100", "012F").map { command ->
                val r = elm.send(command, PROBE_TIMEOUT_MS)
                record(r, "с заголовками")
                SensorProbe(
                    name = "Ответившие блоки ($command)",
                    command = "ATH1 $command",
                    declared = null,
                    response = r.error ?: r.lines.joinToString(" | "),
                    decoded = null,
                    verdict = if (r.ok) ProbeVerdict.OK else ProbeVerdict.NO_DATA,
                )
            }
        } finally {
            send("ATH0")
        }

        suspend fun poll(): VehicleTelemetry {
            val c = cycle++
            val medium = c % 5 == 0L
            val slow = c % 20 == 0L

            val rpm = pid(Pid.RPM, ObdDecode::rpm)
            val speed = pid(Pid.SPEED, ObdDecode::speed)
            val load = pid(Pid.ENGINE_LOAD, ObdDecode::engineLoad)
            t = t.copy(
                rpm = rpm?.roundToInt() ?: t.rpm.takeIf { isSupported(Pid.RPM) },
                speedKmh = speed ?: t.speedKmh.takeIf { isSupported(Pid.SPEED) },
                engineLoad = load ?: t.engineLoad.takeIf { isSupported(Pid.ENGINE_LOAD) },
            )

            // Без MAF расход считается по давлению во впуске — тогда MAP нужен каждый цикл
            val mapEveryCycle = !isSupported(Pid.MAF) && isSupported(Pid.INTAKE_PRESSURE)
            if (medium || mapEveryCycle) {
                t = t.copy(intakePressureKpa = pid(Pid.INTAKE_PRESSURE, ObdDecode::intakePressure) ?: t.intakePressureKpa)
            }
            if (medium) {
                t = t.copy(
                    coolantTemp = pid(Pid.COOLANT_TEMP, ObdDecode::temperature) ?: t.coolantTemp,
                    shortTermFuelTrim = pid(Pid.SHORT_TERM_FUEL_TRIM, ObdDecode::fuelTrim) ?: t.shortTermFuelTrim,
                    longTermFuelTrim = pid(Pid.LONG_TERM_FUEL_TRIM, ObdDecode::fuelTrim) ?: t.longTermFuelTrim,
                    fuelSystemStatus = pid(Pid.FUEL_SYSTEM_STATUS, ObdDecode::fuelSystemStatus) ?: t.fuelSystemStatus,
                )
            }

            val fuel = readFuelRate()
            t = t.copy(fuelRateLph = fuel?.first ?: t.fuelRateLph, fuelRateMethod = fuel?.second ?: t.fuelRateMethod)

            if (slow) {
                val monitor = pid(Pid.MONITOR_STATUS, ObdDecode::monitorStatus)
                t = t.copy(
                    fuelLevelPercent = pid(Pid.FUEL_LEVEL, ObdDecode::fuelLevel) ?: t.fuelLevelPercent,
                    intakeAirTemp = pid(Pid.INTAKE_AIR_TEMP, ObdDecode::temperature) ?: t.intakeAirTemp,
                    ambientAirTemp = pid(Pid.AMBIENT_AIR_TEMP, ObdDecode::temperature) ?: t.ambientAirTemp,
                    voltage = readVoltage() ?: t.voltage,
                    checkEngineOn = monitor?.milOn ?: t.checkEngineOn,
                    dtcCount = monitor?.dtcCount ?: t.dtcCount,
                    readiness = monitor?.readiness ?: t.readiness,
                    atfTemp = profile().atfTempCommand?.let { extended(it) }?.roundToInt() ?: t.atfTemp,
                    cabinTemp = profile().cabinTempCommand?.let { extended(it) } ?: t.cabinTemp,
                )
            }
            if (c % 60 == 0L && cylinderMids.isNotEmpty()) {
                t = t.copy(misfires = readMisfires() ?: t.misfires)
            }
            return t
        }

        /**
         * Расход и способ его получения: PID 5E, если есть; иначе для бензина — по MAF,
         * а без MAF — по давлению во впуске и объёму двигателя из профиля автомобиля.
         */
        private suspend fun readFuelRate(): Pair<Double, String>? {
            if (isSupported(Pid.FUEL_RATE)) return pid(Pid.FUEL_RATE, ObdDecode::fuelRate)?.let { it to "PID 5E" }
            val p = profile()
            if (diesel || p.fuelType == FuelType.DIESEL) return null
            val (maf, method) = if (isSupported(Pid.MAF)) {
                (pid(Pid.MAF, ObdDecode::maf) ?: return null) to "MAF"
            } else {
                val map = t.intakePressureKpa ?: return null
                val rpm = t.rpm ?: return null
                ObdDecode.mafFromMap(
                    map.toDouble(), (t.intakeAirTemp ?: 25).toDouble(), rpm.toDouble(), p.displacementL, p.volumetricEfficiency,
                ) to String.format(Locale.US, "MAP × %.1f л", p.displacementL)
            }
            // Принудительный холостой ход (торможение двигателем): форсунки отключены, топливо не тратится
            val fuelCut = t.fuelSystemStatus?.contains("торможение") == true && (t.engineLoad ?: 100.0) < 20.0
            return (if (fuelCut) 0.0 else ObdDecode.fuelRateFromMaf(maf, t.shortTermFuelTrim, t.longTermFuelTrim)) to method
        }

        /** Напряжение: AT RV (измеряет сам адаптер на 16-м контакте), запасной вариант — PID 42. */
        private suspend fun readVoltage(): Double? {
            if (adapterVoltageWorks) {
                val r = elm.send("ATRV")
                val volts = r.lines.firstOrNull()?.let(ObdDecode::adapterVoltage)
                record(r, volts?.let { String.format(Locale.US, "%.1f V", it) })
                if (volts != null) return volts
                adapterVoltageWorks = false
            }
            return pid(Pid.MODULE_VOLTAGE, ObdDecode::moduleVoltage)
        }

        private suspend fun readMisfires(): List<Int>? {
            val counts = cylinderMids.map { mid ->
                val r = elm.send(String.format(Locale.US, "06%02X", mid))
                val records = ObdParser.messages(r.lines).flatMap(ObdDecode::monitorRecords).filter { it.mid == mid }
                // TID 0C — пропуски в текущем цикле езды, TID 0B — сглаженное среднее за 10 циклов
                val count = (records.firstOrNull { it.tid == 0x0C } ?: records.firstOrNull { it.tid == 0x0B })?.value
                record(r, count?.let { "цилиндр ${mid - 0xA1}: $it пропусков" })
                count ?: return null
            }
            return counts
        }

        /** Команда производителя по CAN 11 бит: временно меняем заголовок на адрес нужного блока. */
        private suspend fun extended(p: ExtendedPid): Double? {
            if (protocol != 6 && protocol != 8) return null
            send("ATSH${p.header}")
            return try {
                val r = elm.send(p.command)
                val data = if (r.ok) ObdParser.responseData(r.lines, ObdParser.hexBytes(p.command)) else null
                val value = data?.let { runCatching { p.decode(it) }.getOrNull() }
                record(r, value?.let { String.format(Locale.US, "%.1f", it) })
                value
            } finally {
                send("ATSH7DF") // функциональный адрес «всем ЭБУ»
            }
        }

        private fun isSupported(pid: Int): Boolean =
            pid !in disabled && (supported.isEmpty() || pid in supported)

        private suspend fun <T> pid(pid: Int, decode: (IntArray) -> T?): T? {
            if (!isSupported(pid)) return null
            val r = elm.send(String.format(Locale.US, "01%02X", pid))
            if (!r.ok) {
                record(r, null)
                // ЭБУ заявил PID, но не отвечает — после трёх попыток перестаём спрашивать
                if (r.isNoData && (noDataCount.merge(pid, 1, Int::plus) ?: 0) >= 3) disabled += pid
                return null
            }
            val value = ObdParser.mode01(r.lines, pid)?.let(decode)
            record(r, value?.let { describe(pid, it) })
            return value
        }
    }

    /** PID для проверки: [range] — правдоподобные значения, вне них — «подозрительно». */
    private class Probe(
        val pid: Int,
        val name: String,
        val range: ClosedFloatingPointRange<Double>?,
        val unit: String,
        val digits: Int = 0,
        val decode: (IntArray) -> Double?,
    ) {
        val format: (Double) -> String = { String.format(Locale.US, "%.${digits}f", it) + unit }
    }

    companion object {
        private const val LOG_SIZE = 80
        private const val PROBE_TIMEOUT_MS = 3_000L

        private fun t(d: IntArray) = ObdDecode.temperature(d)?.toDouble()

        private val PROBES = listOf(
            Probe(Pid.RPM, "Обороты", 0.0..8000.0, " об/мин") { ObdDecode.rpm(it) },
            Probe(Pid.SPEED, "Скорость", 0.0..260.0, " км/ч") { ObdDecode.speed(it)?.toDouble() },
            Probe(Pid.ENGINE_LOAD, "Нагрузка", 0.0..100.0, " %", 1) { ObdDecode.engineLoad(it) },
            Probe(Pid.COOLANT_TEMP, "Темп. ОЖ", -40.0..130.0, " °C") { t(it) },
            Probe(Pid.FUEL_LEVEL, "Уровень топлива", 0.5..100.0, " %", 1) { ObdDecode.fuelLevel(it) },
            Probe(Pid.FUEL_RATE, "Расход (PID 5E)", 0.0..80.0, " л/ч", 2) { ObdDecode.fuelRate(it) },
            Probe(Pid.MAF, "Расход воздуха MAF", 0.0..400.0, " г/с", 2) { ObdDecode.maf(it) },
            Probe(Pid.INTAKE_PRESSURE, "Давление во впуске", 10.0..255.0, " кПа") { ObdDecode.intakePressure(it)?.toDouble() },
            Probe(Pid.INTAKE_AIR_TEMP, "Темп. впуска", -40.0..90.0, " °C") { t(it) },
            Probe(Pid.AMBIENT_AIR_TEMP, "Темп. наружного воздуха", -45.0..60.0, " °C") { t(it) },
            Probe(Pid.MODULE_VOLTAGE, "Напряжение ЭБУ", 9.0..16.0, " V", 2) { ObdDecode.moduleVoltage(it) },
            Probe(Pid.SHORT_TERM_FUEL_TRIM, "Кратк. коррекция", -30.0..30.0, " %", 1) { ObdDecode.fuelTrim(it) },
            Probe(Pid.LONG_TERM_FUEL_TRIM, "Долг. коррекция", -30.0..30.0, " %", 1) { ObdDecode.fuelTrim(it) },
            Probe(Pid.MONITOR_STATUS, "Check Engine / DTC", null, " DTC") { d -> ObdDecode.monitorStatus(d)?.dtcCount?.toDouble() },
            Probe(Pid.FUEL_SYSTEM_STATUS, "Топливная система", null, "") { d -> d.firstOrNull()?.toDouble() },
            Probe(0x33, "Атм. давление", 70.0..110.0, " кПа") { d -> d.firstOrNull()?.toDouble() },
            Probe(0x5C, "Темп. масла двигателя", -40.0..160.0, " °C") { t(it) },
            Probe(0x51, "Тип топлива (код)", null, "") { d -> d.firstOrNull()?.toDouble() },
            Probe(Pid.ODOMETER, "Пробег (одометр)", 0.0..2_000_000.0, " км", 1) { d ->
                if (d.size >= 4) ((d[0].toLong() shl 24) + (d[1] shl 16) + (d[2] shl 8) + d[3]) / 10.0 else null
            },
        )

        private fun hex(n: Int) = String.format(Locale.US, "%02X", n)

        fun protocolName(number: Int?): String? = when (number) {
            null -> null
            1 -> "SAE J1850 PWM"
            2 -> "SAE J1850 VPW"
            3 -> "ISO 9141-2"
            4 -> "ISO 14230-4 KWP (5 бод)"
            5 -> "ISO 14230-4 KWP (быстрая инициализация)"
            6 -> "ISO 15765-4 CAN (11 бит, 500 кбит/с)"
            7 -> "ISO 15765-4 CAN (29 бит, 500 кбит/с)"
            8 -> "ISO 15765-4 CAN (11 бит, 250 кбит/с)"
            9 -> "ISO 15765-4 CAN (29 бит, 250 кбит/с)"
            10 -> "SAE J1939 CAN"
            else -> "Протокол $number"
        }

        /** Расшифровка для журнала — чтобы сверить с ручным расчётом по формуле. */
        private fun describe(pid: Int, value: Any): String {
            fun d(v: Any, digits: Int) = if (v is Double) String.format(Locale.US, "%.${digits}f", v) else v.toString()
            return when (pid) {
                Pid.RPM -> "${d(value, 0)} об/мин"
                Pid.SPEED -> "$value км/ч"
                Pid.ENGINE_LOAD -> "${d(value, 1)} %"
                Pid.COOLANT_TEMP, Pid.INTAKE_AIR_TEMP, Pid.AMBIENT_AIR_TEMP -> "$value °C"
                Pid.SHORT_TERM_FUEL_TRIM, Pid.LONG_TERM_FUEL_TRIM -> "${d(value, 1)} %"
                Pid.INTAKE_PRESSURE -> "$value кПа"
                Pid.MAF -> "${d(value, 2)} г/с"
                Pid.FUEL_LEVEL -> "${d(value, 1)} %"
                Pid.MODULE_VOLTAGE -> "${d(value, 2)} V"
                Pid.FUEL_RATE -> "${d(value, 2)} л/ч"
                Pid.MONITOR_STATUS -> (value as MonitorStatus).let { "MIL ${if (it.milOn) "вкл" else "выкл"}, DTC ${it.dtcCount}" }
                else -> value.toString()
            }
        }
    }
}
