package com.carscan.data

/**
 * Команды производителя для того, чего нет в стандартном OBD-II: уровень топлива, температура АКПП и салона.
 *
 * Команды Mitsubishi взяты из профилей Torque владельцев Outlander (форум out-club.ru, файлы OUT3.csv и
 * «Mitsubishi Outlander 2008–2015»). На Outlander XL их никто не сверял с приборкой, поэтому значения
 * проверяются на правдоподобие, а сырой ответ блока попадает в проверку датчиков и в отчёт.
 */
object ManufacturerPids {

    data class Commands(
        val fuelLevel: ExtendedPid? = null,
        val atfTemp: ExtendedPid? = null,
        val cabinTemp: ExtendedPid? = null,
    )

    fun forVehicle(make: String, model: String, generation: String, engine: String): Commands = when {
        make == "Mitsubishi" && model == "Outlander" -> Commands(
            fuelLevel = MMC_FUEL_LEVEL,
            // Вариатор JF011E — у XL с моторами 2.0 и 2.4. У V6 гидромеханический автомат, у Outlander III после 2015
            // года другой вариатор (JF016E) с другой формулой — им эта команда не подходит
            atfTemp = MMC_CVT_TEMP.takeIf { generation.startsWith("XL") && !engine.contains("V6") },
            cabinTemp = MMC_CABIN_TEMP,
        )
        else -> Commands()
    }

    private const val FORUM = "профиль Torque с форума владельцев Outlander, с приборкой не сверено"

    /**
     * Комбинация приборов: запрос 6A0, ответ 514, «21 A3», байт C (третий после «61 A3»).
     * C − 16 — проценты: 16 — пустой бак, 116 — полный.
     */
    val MMC_FUEL_LEVEL = ExtendedPid(
        name = "Уровень топлива (приборы)",
        header = "6A0",
        receive = "514",
        command = "21A3",
        unit = " %",
        range = 0.0..100.0,
        source = FORUM,
    ) { d -> d.getOrNull(2)?.takeIf { it in 10..122 }?.let { (it - 16).toDouble().coerceIn(0.0, 100.0) } }

    /** Климат-контроль: запрос 688, ответ 511, «21 10», байт A (первый после «61 10»): A / 4 − 16 °C. */
    val MMC_CABIN_TEMP = ExtendedPid(
        name = "Темп. в салоне (климат)",
        header = "688",
        receive = "511",
        command = "2110",
        unit = " °C",
        digits = 1,
        range = -16.0..50.0,
        source = FORUM,
    ) { d -> d.getOrNull(0)?.takeIf { it != 0xFF }?.let { it / 4.0 - 16 } }

    /**
     * Вариатор Jatco JF011E: запрос 7E1, ответ 7E9, «21 03», байт N (14-й после «61 03») — отсчёт датчика
     * температуры масла. 0 и FF — обрыв или замыкание датчика.
     */
    val MMC_CVT_TEMP = ExtendedPid(
        name = "Темп. вариатора",
        header = "7E1",
        command = "2103",
        unit = " °C",
        range = -40.0..150.0,
        source = "$FORUM; полином датчика JF011E",
    ) { d -> d.getOrNull(13)?.takeIf { it in 1..254 }?.let(::jf011eTemperature) }

    /**
     * Отсчёт датчика масла JF011E → °C (полином из профиля Torque). Второй независимый вариант формулы,
     * 0.0000286·N³ − 0.00951·N² + 1.46·N − 30.1, расходится с ним не больше чем на 3 °C в рабочем диапазоне.
     */
    fun jf011eTemperature(n: Int): Double {
        val x = n.toDouble()
        return 2.344e-9 * x * x * x * x * x - 1.387e-6 * x * x * x * x + 3.193e-4 * x * x * x - 0.03501 * x * x + 2.302 * x - 36.6
    }
}
