package com.carscan.data

/**
 * Снимок телеметрии автомобиля.
 * null — параметр недоступен: ЭБУ его не поддерживает или нет связи с адаптером.
 */
data class VehicleTelemetry(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val gear: String? = null,
    val engineLoad: Double? = null,         // %, PID 0104
    val coolantTemp: Int? = null,           // °C, PID 0105
    val atfTemp: Int? = null,               // °C, масло АКПП (PID производителя)
    val intakeAirTemp: Int? = null,         // °C, PID 010F
    val ambientAirTemp: Int? = null,        // °C, PID 0146 — датчик наружного воздуха ТС
    val cabinTemp: Double? = null,          // °C, датчик салона (PID производителя)
    val fuelLevelPercent: Double? = null,   // %, PID 012F
    val fuelRateLph: Double? = null,        // л/ч, PID 015E или расчёт по MAF/MAP
    val fuelRateMethod: String? = null,     // откуда взят расход: «PID 5E», «MAF», «MAP × объём»
    val voltage: Double? = null,            // В, AT RV / PID 0142
    val intakePressureKpa: Int? = null,     // кПа, PID 010B
    val shortTermFuelTrim: Double? = null,  // %, PID 0106
    val longTermFuelTrim: Double? = null,   // %, PID 0107
    val fuelSystemStatus: String? = null,   // PID 0103
    val checkEngineOn: Boolean = false,
    val dtcCount: Int = 0,
    val misfires: List<Int> = emptyList(),  // счётчики пропусков по цилиндрам (Mode 06, MID A2..)
    val readiness: List<ReadinessTest> = emptyList(),
)

data class ReadinessTest(val name: String, val passed: Boolean)

/**
 * Параметры конкретного автомобиля, которых нет в стандартном OBD.
 * Берутся из справочника (… → Автомобиль) с ручными правками — см. [VehicleRepository].
 */
data class VehicleProfile(
    val make: String = "Mercedes-Benz",
    val model: String = "GLS",
    val generation: String = "X167 (2019–)",
    val engine: String = "580 4MATIC",
    /** Объём двигателя: нужен для расчёта расхода по датчику давления (MAP), если нет датчика MAF. */
    val displacementL: Double = 4.0,
    /** Бензин/гибрид — расход можно считать по воздуху; дизель — только по PID 5E. */
    val fuelType: FuelType = FuelType.PETROL,
    val cylinders: Int = 8,
    val powerHp: Int? = 489,
    val tankLiters: Double = 90.0,
    /** Коэффициент наполнения цилиндров для расчёта по MAP (в Car Scanner по умолчанию 85 %). */
    val volumetricEfficiency: Double = DEFAULT_VE,
    /** Температура масла АКПП — только командой производителя (в SAE J1979 её нет). */
    val atfTempCommand: ExtendedPid? = null,
    /** Температура в салоне — только командой производителя. */
    val cabinTempCommand: ExtendedPid? = null,
) {
    val name: String get() = "$make $model"

    companion object {
        const val DEFAULT_VE = 0.85
    }
}

/**
 * Команда производителя (обычно UDS 22 xx xx) к конкретному блоку по CAN 11 бит.
 * Пример: header = "7E1" (АКПП), command = "221234", decode = { it[0] - 40.0 }.
 * [decode] получает байты после эха запроса (62 12 34 …).
 */
data class ExtendedPid(
    val header: String,
    val command: String,
    val decode: (IntArray) -> Double,
)
