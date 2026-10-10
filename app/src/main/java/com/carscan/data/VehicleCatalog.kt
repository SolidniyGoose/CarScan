package com.carscan.data

import android.content.Context
import java.util.Locale

enum class FuelType(val code: Char, val title: String) {
    PETROL('P', "Бензин"),
    DIESEL('D', "Дизель"),
    HYBRID('H', "Гибрид"),
    ;

    companion object {
        fun fromCode(code: Char): FuelType? = entries.firstOrNull { it.code == code }
    }
}

/** Вариант автомобиля из справочника: марка → модель → поколение → двигатель. */
data class VehicleSpec(
    val make: String,
    val model: String,
    val generation: String,
    val engine: String,
    val displacementL: Double,
    val fuel: FuelType,
    val cylinders: Int,
    val powerHp: Int,
    val tankLiters: Double,
) {
    /** Мощность входит в ключ: один и тот же мотор бывает в нескольких форсировках. */
    val id: String get() = listOf(make, model, generation, engine, powerHp).joinToString("|")
    val modelKey: String get() = "$model|$generation"

    /** «1.6 л · 106 л.с. · бензин · бак 55 л» */
    val summary: String
        get() = String.format(Locale.US, "%.1f л · %d л.с. · %s · бак %.0f л", displacementL, powerHp, fuel.title.lowercase(), tankLiters)
}

data class CatalogModel(val make: String, val model: String, val generation: String, val variants: Int) {
    val key: String get() = "$model|$generation"
}

/** Справочник автомобилей из assets/vehicles.csv (как профили подключения в Car Scanner). */
class VehicleCatalog(val vehicles: List<VehicleSpec>) {
    private val byId = vehicles.associateBy { it.id }

    fun find(id: String?): VehicleSpec? = id?.let(byId::get)

    fun makes(): List<String> = vehicles.map { it.make }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Модели марки в порядке файла (поколения идут от старых к новым). */
    fun models(make: String): List<CatalogModel> = vehicles
        .filter { it.make == make }
        .groupBy { it.modelKey }
        .map { (_, list) -> CatalogModel(make, list[0].model, list[0].generation, list.size) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.model })

    fun variants(make: String, modelKey: String): List<VehicleSpec> =
        vehicles.filter { it.make == make && it.modelKey == modelKey }

    companion object {
        const val DEFAULT_ID = "Mercedes-Benz|GLS|X167 (2019–)|580 4MATIC|489"

        fun fromAssets(context: Context): VehicleCatalog =
            context.assets.open("vehicles.csv").bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }

        /** Строка: марка;модель;поколение;двигатель;объём;топливо P/D/H;цилиндры;л.с.;бак. Ошибка — с номером строки. */
        fun parse(text: String): VehicleCatalog {
            val vehicles = text.lineSequence().mapIndexedNotNull { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
                val f = line.split(';').map { it.trim() }
                require(f.size == 9) { "vehicles.csv:${index + 1}: ожидалось 9 полей, а не ${f.size}" }
                fun <T> field(i: Int, name: String, parse: (String) -> T?): T =
                    parse(f[i]) ?: throw IllegalArgumentException("vehicles.csv:${index + 1}: неверное поле «$name»: ${f[i]}")
                VehicleSpec(
                    make = f[0], model = f[1], generation = f[2], engine = f[3],
                    displacementL = field(4, "объём") { it.toDoubleOrNull() },
                    fuel = field(5, "топливо") { s -> s.singleOrNull()?.let(FuelType::fromCode) },
                    cylinders = field(6, "цилиндры") { it.toIntOrNull() },
                    powerHp = field(7, "мощность") { it.toIntOrNull() },
                    tankLiters = field(8, "бак") { it.toDoubleOrNull() },
                )
            }.toList()
            return VehicleCatalog(vehicles)
        }
    }
}

/**
 * Профиль автомобиля = вариант из справочника + ручные правки пользователя
 * (если у конкретной машины другой бак или справочник ошибся).
 */
class VehicleRepository(private val catalogLoader: () -> VehicleCatalog, private val settings: SettingsRepository) {
    val catalog: VehicleCatalog by lazy(catalogLoader)

    fun spec(s: AppSettings = settings.current): VehicleSpec =
        catalog.find(s.vehicleId) ?: catalog.find(VehicleCatalog.DEFAULT_ID) ?: catalog.vehicles.first()

    fun profile(s: AppSettings = settings.current): VehicleProfile = profileOf(spec(s), s)

    companion object {
        fun profileOf(spec: VehicleSpec, s: AppSettings) = VehicleProfile(
            make = spec.make,
            model = spec.model,
            generation = spec.generation,
            engine = spec.engine,
            displacementL = s.displacementOverride ?: spec.displacementL,
            fuelType = s.fuelOverride ?: spec.fuel,
            cylinders = spec.cylinders,
            powerHp = spec.powerHp,
            tankLiters = s.tankOverride ?: spec.tankLiters,
            volumetricEfficiency = s.veOverride ?: VehicleProfile.DEFAULT_VE,
            // Команды производителя (температура АКПП/салона) подключаются здесь по марке,
            // когда найдена проверенная команда — см. docs/OBD_CHECK.md, шаг 5
            atfTempCommand = null,
            cabinTempCommand = null,
        )
    }
}
