package com.carscan.data.obd

import com.carscan.data.ReadinessTest

/** PID режима 01 (SAE J1979), которые читает приложение. */
object Pid {
    const val SUPPORTED_01_20 = 0x00
    const val MONITOR_STATUS = 0x01
    const val FUEL_SYSTEM_STATUS = 0x03
    const val ENGINE_LOAD = 0x04
    const val COOLANT_TEMP = 0x05
    const val SHORT_TERM_FUEL_TRIM = 0x06
    const val LONG_TERM_FUEL_TRIM = 0x07
    const val INTAKE_PRESSURE = 0x0B
    const val RPM = 0x0C
    const val SPEED = 0x0D
    const val INTAKE_AIR_TEMP = 0x0F
    const val MAF = 0x10
    const val SUPPORTED_21_40 = 0x20
    const val FUEL_LEVEL = 0x2F
    const val SUPPORTED_41_60 = 0x40
    const val MODULE_VOLTAGE = 0x42
    const val AMBIENT_AIR_TEMP = 0x46
    const val FUEL_RATE = 0x5E
    /** Последняя маска, которую запрашиваем (A1–C0): дальше PID для легковых почти не бывает. */
    const val SUPPORTED_A1_C0 = 0xA0
    const val ODOMETER = 0xA6
}

data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    /** Дизель: другой набор мониторов и нельзя считать расход по MAF. */
    val compressionIgnition: Boolean,
    val readiness: List<ReadinessTest>,
)

/** Одна запись теста режима 06 (CAN): MID, TID, единицы и значение с пределами. */
data class MonitorRecord(val mid: Int, val tid: Int, val unitScaling: Int, val value: Int, val min: Int, val max: Int)

/**
 * Формулы SAE J1979 (те же, что в Car Scanner, Torque и python-OBD).
 * На вход — байты данных после «41 PID», на выход — значение или null, если байтов не хватает.
 */
object ObdDecode {
    fun engineLoad(d: IntArray): Double? = d.need(1) { it[0] * 100.0 / 255.0 }
    fun temperature(d: IntArray): Int? = d.need(1) { it[0] - 40 }
    fun fuelTrim(d: IntArray): Double? = d.need(1) { (it[0] - 128) * 100.0 / 128.0 }
    fun intakePressure(d: IntArray): Int? = d.need(1) { it[0] }
    fun rpm(d: IntArray): Double? = d.need(2) { (it[0] * 256 + it[1]) / 4.0 }
    fun speed(d: IntArray): Int? = d.need(1) { it[0] }
    fun maf(d: IntArray): Double? = d.need(2) { (it[0] * 256 + it[1]) / 100.0 }
    fun fuelLevel(d: IntArray): Double? = d.need(1) { it[0] * 100.0 / 255.0 }
    fun moduleVoltage(d: IntArray): Double? = d.need(2) { (it[0] * 256 + it[1]) / 1000.0 }
    fun fuelRate(d: IntArray): Double? = d.need(2) { (it[0] * 256 + it[1]) / 20.0 }

    /** Ответ ELM327 на AT RV, например «14.2V». */
    fun adapterVoltage(text: String): Double? = text.trim().removeSuffix("V").removeSuffix("v").trim().toDoubleOrNull()

    /** Битовая маска поддерживаемых номеров (PID 00/20/40… или MID A0 режима 06): старший бит — base+1. */
    fun supportedNumbers(base: Int, d: IntArray): Set<Int> {
        if (d.size < 4) return emptySet()
        val result = mutableSetOf<Int>()
        for (i in 0 until 32) {
            if (((d[i / 8] shr (7 - i % 8)) and 1) == 1) result += base + i + 1
        }
        return result
    }

    fun fuelSystemStatus(d: IntArray): String? = d.need(1) {
        when (it[0]) {
            0 -> "Не используется"
            1 -> "Разомкнутый контур (прогрев)"
            2 -> "Замкнутый контур (датчик O₂)"
            4 -> "Разомкнутый контур (нагрузка/торможение)"
            8 -> "Разомкнутый контур (неисправность)"
            16 -> "Замкнутый контур (неисправность датчика)"
            else -> "Код ${it[0]}"
        }
    }

    /** PID 01: лампа Check Engine, число ошибок и готовность мониторов (бит «incomplete» = 0 → тест пройден). */
    fun monitorStatus(d: IntArray): MonitorStatus? = d.need(4) {
        val (a, b, c, incomplete) = listOf(it[0], it[1], it[2], it[3])
        val diesel = (b and 0x08) != 0
        val tests = mutableListOf<ReadinessTest>()
        CONTINUOUS_MONITORS.forEachIndexed { i, name ->
            if (((b shr i) and 1) == 1) tests += ReadinessTest(name, passed = ((b shr (i + 4)) and 1) == 0)
        }
        (if (diesel) DIESEL_MONITORS else SPARK_MONITORS).forEachIndexed { i, name ->
            if (name != null && ((c shr i) and 1) == 1) tests += ReadinessTest(name, passed = ((incomplete shr i) and 1) == 0)
        }
        MonitorStatus(milOn = (a and 0x80) != 0, dtcCount = a and 0x7F, compressionIgnition = diesel, readiness = tests)
    }

    /** Режим 06 (CAN): после байта 46 идут записи по 9 байт: MID TID UASID V(2) MIN(2) MAX(2). */
    fun monitorRecords(message: IntArray): List<MonitorRecord> {
        if (message.isEmpty() || message[0] != 0x46) return emptyList()
        val records = mutableListOf<MonitorRecord>()
        var i = 1
        while (i + 9 <= message.size) {
            val r = message
            records += MonitorRecord(
                mid = r[i], tid = r[i + 1], unitScaling = r[i + 2],
                value = r[i + 3] * 256 + r[i + 4],
                min = r[i + 5] * 256 + r[i + 6],
                max = r[i + 7] * 256 + r[i + 8],
            )
            i += 9
        }
        return records
    }

    /**
     * Расход бензина по массовому расходу воздуха: стехиометрия 14.7, плотность 745 г/л,
     * поправка на топливные коррекции. Для дизеля не применяется (там смесь бедная и переменная).
     */
    fun fuelRateFromMaf(mafGramsPerSec: Double, shortTrim: Double?, longTrim: Double?): Double {
        val correction = 1.0 + ((shortTrim ?: 0.0) + (longTrim ?: 0.0)) / 100.0
        return mafGramsPerSec / STOICHIOMETRIC_AFR / GASOLINE_DENSITY_G_PER_L * 3600.0 * correction
    }

    /**
     * Массовый расход воздуха по давлению во впуске (speed-density), если датчика MAF нет:
     * m = P / (R·T) × Vₕ × об/мин / 120 × КН. Так же считают Car Scanner и Torque (КН по умолчанию 85 %).
     */
    fun mafFromMap(mapKpa: Double, intakeTempC: Double, rpm: Double, displacementL: Double, volumetricEfficiency: Double): Double {
        val kelvin = intakeTempC + 273.15
        return mapKpa * displacementL * rpm * volumetricEfficiency / (AIR_GAS_CONSTANT * kelvin * 120.0) * 1000.0
    }

    /** Удельная газовая постоянная воздуха, Дж/(кг·К). */
    const val AIR_GAS_CONSTANT = 287.05
    const val STOICHIOMETRIC_AFR = 14.7
    const val GASOLINE_DENSITY_G_PER_L = 745.0

    private val CONTINUOUS_MONITORS = listOf("Пропуски зажигания", "Топливная система", "Компоненты")
    private val SPARK_MONITORS = listOf(
        "Катализатор", "Подогрев катализатора", "Система EVAP", "Вторичный воздух",
        null, "Датчик кислорода", "Подогрев датчика O₂", "Система EGR/VVT",
    )
    private val DIESEL_MONITORS = listOf(
        "Катализатор NMHC", "Нейтрализатор NOx/SCR", null, "Давление наддува",
        null, "Датчик ОГ", "Сажевый фильтр", "Система EGR/VVT",
    )

    private inline fun <T> IntArray.need(bytes: Int, decode: (IntArray) -> T): T? =
        if (size >= bytes) decode(this) else null
}
