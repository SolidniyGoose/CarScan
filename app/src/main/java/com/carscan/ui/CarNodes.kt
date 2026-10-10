package com.carscan.ui

import kotlin.math.roundToInt

/** Узлы автомобиля, которые подсвечиваются на 3D-модели. id совпадают с NODES в viewer.html. */
enum class CarNode(val id: String, val title: String) {
    ENGINE("engine", "Двигатель ДВС"),
    TRANSMISSION("transmission", "АКПП"),
    FUEL("fuel", "Топливная система"),
    BATTERY("battery", "Электросеть"),
    CLIMATE("climate", "Климат и салон"),
    EXHAUST("exhaust", "Выхлопная система");

    companion object {
        fun fromId(id: String): CarNode? = entries.firstOrNull { it.id == id }
    }
}

enum class StatusLevel(val js: String) {
    OK("ok"), INFO("info"), WARN("warn"), CRIT("crit"), OFF("off")
}

/**
 * Состояние узла для подписи на модели.
 * [heat] 0..1 — интенсивность «тепловой» подсветки в рентген-режиме.
 */
data class NodeStatus(
    val node: CarNode,
    val status: String,
    val value: String,
    val level: StatusLevel,
    val heat: Float,
)

object CarNodes {
    const val ENGINE_RUNNING_RPM = 400

    fun evaluate(state: DashboardState): List<NodeStatus> = CarNode.entries.map { evaluate(state, it) }

    fun evaluate(state: DashboardState, node: CarNode): NodeStatus {
        val t = state.telemetry
        return when (node) {
            CarNode.ENGINE -> {
                val c = t.coolantTemp
                val (status, level) = when {
                    c == null -> "Нет данных" to StatusLevel.OFF
                    c > 112 -> "Перегрев" to StatusLevel.CRIT
                    c > 104 -> "Горячий" to StatusLevel.WARN
                    t.checkEngineOn -> "Check Engine" to StatusLevel.WARN
                    c < 60 -> "Прогрев" to StatusLevel.INFO
                    else -> "Исправен" to StatusLevel.OK
                }
                NodeStatus(node, status, if (c != null) "$c °C" else "", level, heat(c, 20.0, 110.0))
            }
            CarNode.TRANSMISSION -> {
                val a = t.atfTemp
                val (status, level) = when {
                    a == null -> "Нет данных" to StatusLevel.OFF
                    a > 115 -> "Перегрев" to StatusLevel.CRIT
                    a > 100 -> "Горячая" to StatusLevel.WARN
                    a < 40 -> "Прогрев" to StatusLevel.INFO
                    else -> "Норма" to StatusLevel.OK
                }
                NodeStatus(node, status, if (a != null) "ATF $a °C" else "", level, heat(a, 20.0, 120.0))
            }
            CarNode.FUEL -> {
                val pct = t.fuelLevelPercent
                val level = when {
                    pct == null -> StatusLevel.OFF
                    pct < 10 -> StatusLevel.CRIT
                    pct < 20 -> StatusLevel.WARN
                    else -> StatusLevel.OK
                }
                val status = when {
                    pct == null -> "Нет данных"
                    pct < 10 -> "Резерв ${pct.roundToInt()} %"
                    else -> "${pct.roundToInt()} %"
                }
                val liters = state.fuelLiters
                NodeStatus(node, status, if (liters != null) liters.fmt(1) + " л" else "", level, ((pct ?: 0.0) / 100).toFloat())
            }
            CarNode.BATTERY -> {
                val (status, level) = batteryStatus(t.voltage, t.rpm)
                val heat = when (level) {
                    StatusLevel.CRIT -> 0.95f
                    StatusLevel.WARN -> 0.65f
                    StatusLevel.OFF -> 0f
                    else -> 0.4f
                }
                NodeStatus(node, status, t.voltage?.let { it.fmt(1) + " V" } ?: "", level, heat)
            }
            CarNode.CLIMATE -> {
                val cabin = t.cabinTemp
                val outside = state.outsideTempC
                // Салон в стандартном OBD недоступен — тогда подпись показывает хотя бы улицу
                NodeStatus(
                    node,
                    status = cabin?.let { "${it.roundToInt()} °C" } ?: NO_VALUE,
                    value = if (outside != null) "ул. ${signedTemp(outside)}" else "",
                    level = if (cabin != null || outside != null) StatusLevel.INFO else StatusLevel.OFF,
                    heat = heat(cabin?.roundToInt(), 0.0, 40.0),
                )
            }
            CarNode.EXHAUST -> {
                val tests = t.readiness
                val passed = tests.count { it.passed }
                val (status, level) = when {
                    tests.isEmpty() -> "Нет данных" to StatusLevel.OFF
                    passed == tests.size -> "В норме" to StatusLevel.OK
                    else -> "Тесты $passed/${tests.size}" to StatusLevel.WARN
                }
                NodeStatus(node, status, "", level, ((t.engineLoad ?: 0.0) / 100).toFloat().coerceIn(0.1f, 1f))
            }
        }
    }

    fun batteryStatus(voltage: Double?, rpm: Int?): Pair<String, StatusLevel> {
        if (voltage == null) return "Нет данных" to StatusLevel.OFF
        val engineRunning = rpm != null && rpm > ENGINE_RUNNING_RPM
        return if (engineRunning) when {
            voltage < 13.0 -> "Слабая зарядка" to StatusLevel.WARN
            voltage > 14.9 -> "Перезаряд" to StatusLevel.WARN
            else -> "Зарядка" to StatusLevel.OK
        } else when {
            voltage < 12.0 -> "Разряд" to StatusLevel.CRIT
            voltage < 12.4 -> "Низкое" to StatusLevel.WARN
            else -> "Норма" to StatusLevel.OK
        }
    }

    private fun heat(value: Int?, cold: Double, hot: Double): Float =
        if (value == null) 0f else ((value - cold) / (hot - cold)).toFloat().coerceIn(0f, 1f)
}
