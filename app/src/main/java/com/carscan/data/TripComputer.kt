package com.carscan.data

data class TripStats(
    val distanceKm: Double = 0.0,
    val fuelUsedL: Double = 0.0,
    val durationSec: Long = 0,
    /** Средний расход с начала поездки; null, пока не проехали [TripComputer.MIN_DISTANCE_KM]. */
    val avgConsumptionL100: Double? = null,
    /** Мгновенный расход в движении. */
    val instantL100: Double? = null,
    /** Мгновенный расход на месте (на холостых л/100 км не имеет смысла). */
    val instantLph: Double? = null,
    val avgSpeedKmh: Double? = null,
)

/**
 * Бортовой компьютер: интегрирует скорость и расход топлива по времени.
 * Работает одинаково для симулятора и для реального OBD2 — так же считает Car Scanner.
 */
class TripComputer {
    private var distanceKm = 0.0
    private var fuelUsedL = 0.0
    private var elapsedMs = 0L
    private var lastSampleMs: Long? = null

    fun onSample(sample: VehicleTelemetry, nowMs: Long): TripStats {
        val last = lastSampleMs
        lastSampleMs = nowMs
        if (last != null) {
            val dtMs = (nowMs - last).coerceIn(0, MAX_GAP_MS)
            val dtHours = dtMs / 3_600_000.0
            elapsedMs += dtMs
            sample.speedKmh?.let { distanceKm += it * dtHours }
            sample.fuelRateLph?.let { fuelUsedL += it * dtHours }
        }
        return snapshot(sample)
    }

    /** Разрыв связи: следующий снимок не должен «дорисовывать» пропущенное время. */
    fun pause() {
        lastSampleMs = null
    }

    fun reset() {
        distanceKm = 0.0
        fuelUsedL = 0.0
        elapsedMs = 0
        lastSampleMs = null
    }

    private fun snapshot(sample: VehicleTelemetry): TripStats {
        val speed = sample.speedKmh
        val rate = sample.fuelRateLph
        val moving = speed != null && speed >= MIN_MOVING_SPEED_KMH
        val hours = elapsedMs / 3_600_000.0
        return TripStats(
            distanceKm = distanceKm,
            fuelUsedL = fuelUsedL,
            durationSec = elapsedMs / 1000,
            avgConsumptionL100 = if (distanceKm >= MIN_DISTANCE_KM) fuelUsedL / distanceKm * 100.0 else null,
            instantL100 = if (moving && rate != null) rate / speed!! * 100.0 else null,
            instantLph = if (!moving) rate else null,
            avgSpeedKmh = if (hours > 0 && distanceKm >= MIN_DISTANCE_KM) distanceKm / hours else null,
        )
    }

    companion object {
        const val MIN_DISTANCE_KM = 0.1
        const val MIN_MOVING_SPEED_KMH = 3
        /** Пауза дольше этого считается разрывом связи, а не движением. */
        const val MAX_GAP_MS = 5_000L
    }
}
