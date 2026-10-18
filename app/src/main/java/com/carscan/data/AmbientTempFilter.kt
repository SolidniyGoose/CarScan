package com.carscan.data

/**
 * Датчик наружного воздуха (PID 46) обычно стоит за решёткой радиатора: на стоянке и в пробке его
 * греет двигатель (в отчёте с Outlander XL — +32 °C при +11 °C на улице). Поэтому, как бортовой
 * компьютер машины:
 * - на холодном двигателе показание верное — принимаем;
 * - после [SETTLE_MS] езды от [MIN_SPEED_KMH] км/ч датчик обдувается — принимаем любое значение;
 * - в остальное время принимаем только понижение: прогретый подкапотник может лишь завышать.
 * До первого надёжного показания — null («—»), а не заведомо завышенная цифра.
 */
class AmbientTempFilter {
    private var accepted: Double? = null
    private var movingSinceMs: Long? = null

    fun onSample(ambientC: Int?, speedKmh: Int?, coolantC: Int?, nowMs: Long): Double? {
        val value = ambientC?.toDouble() ?: return accepted
        val moving = speedKmh != null && speedKmh >= MIN_SPEED_KMH
        movingSinceMs = if (moving) movingSinceMs ?: nowMs else null
        val trusted = (coolantC != null && coolantC < COLD_ENGINE_C) ||
            (moving && nowMs - movingSinceMs!! >= SETTLE_MS)
        accepted = when {
            trusted -> value
            accepted != null -> minOf(accepted!!, value)
            else -> null
        }
        return accepted
    }

    companion object {
        const val MIN_SPEED_KMH = 30
        const val SETTLE_MS = 60_000L
        /** Двигатель ещё не прогрел подкапотное пространство. */
        const val COLD_ENGINE_C = 45
    }
}
