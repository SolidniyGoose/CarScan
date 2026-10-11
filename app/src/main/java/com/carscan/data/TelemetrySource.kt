package com.carscan.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Источник телеметрии: [SimulatedTelemetrySource] или реальный ELM327 ([com.carscan.data.obd.ObdTelemetrySource]).
 */
interface TelemetrySource {
    /** Название для интерфейса: «Симулятор», «OBD2 (ELM327)». */
    val name: String

    /** Состояние подключения и журнал обмена с адаптером. */
    val connection: StateFlow<ConnectionInfo>

    /**
     * Холодный поток снимков. Сбор начинается при подписке и останавливается при её отмене.
     * При потере связи поток завершается исключением — переподключением занимается ViewModel.
     */
    fun samples(): Flow<VehicleTelemetry>

    /** Повторить проверку датчиков ([ConnectionInfo.selfTest]) в текущем сеансе. Симулятору нечего проверять. */
    fun requestSelfTest() {}
}

enum class ConnectionState { IDLE, CONNECTING, INITIALIZING, CONNECTED, ERROR }

data class ConnectionInfo(
    val state: ConnectionState = ConnectionState.IDLE,
    val message: String = "",
    /** Ответ на AT I, например «ELM327 v1.5». */
    val adapter: String? = null,
    /** Протокол, выбранный адаптером (AT DPN). */
    val protocol: String? = null,
    val supportedPids: Set<Int> = emptySet(),
    val log: List<ObdLogEntry> = emptyList(),
    /** Результат проверки датчиков после подключения: каждый нужный PID опрошен один раз. */
    val selfTest: List<SensorProbe> = emptyList(),
    /** Когда проверка завершилась (0 — ещё не было). */
    val selfTestAtMs: Long = 0,
)

/** Строка журнала: запрос, «сырой» ответ адаптера и что из него получилось. */
data class ObdLogEntry(val command: String, val response: String, val decoded: String? = null)

/**
 * Проверка одного датчика: что ЭБУ заявил в маске поддерживаемых PID, что ответил на прямой запрос
 * и правдоподобно ли значение.
 */
data class SensorProbe(
    val name: String,
    val command: String,
    /** PID есть в маске 0100/0120/0140; null — для команд адаптера (AT RV). */
    val declared: Boolean?,
    val response: String,
    val decoded: String?,
    val verdict: ProbeVerdict,
    val note: String? = null,
)

enum class ProbeVerdict(val label: String) {
    OK("OK"),
    SUSPICIOUS("подозрительно"),
    NO_DATA("нет данных"),
    NOT_SUPPORTED("не поддерживается"),
    UNDECLARED("отвечает, но не заявлен"),
    ERROR("ошибка"),
}
