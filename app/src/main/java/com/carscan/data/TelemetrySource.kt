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
)

/** Строка журнала: запрос, «сырой» ответ адаптера и что из него получилось. */
data class ObdLogEntry(val command: String, val response: String, val decoded: String? = null)
