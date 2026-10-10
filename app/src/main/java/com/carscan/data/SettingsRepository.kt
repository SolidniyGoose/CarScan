package com.carscan.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DataSourceType(val title: String) {
    SIMULATOR("Симулятор"),
    BLUETOOTH("Bluetooth ELM327"),
}

data class AppSettings(
    val themeId: String = "cyan",
    val dataSource: DataSourceType = DataSourceType.SIMULATOR,
    val obdDeviceAddress: String? = null,
    val obdDeviceName: String? = null,
    val xray: Boolean = true,
    /** Выбранный вариант из справочника (VehicleSpec.id). */
    val vehicleId: String = VehicleCatalog.DEFAULT_ID,
    // Ручные правки поверх справочника; null — значение из справочника
    val displacementOverride: Double? = null,
    val tankOverride: Double? = null,
    val fuelOverride: FuelType? = null,
    val veOverride: Double? = null,
) {
    val hasVehicleOverrides: Boolean
        get() = displacementOverride != null || tankOverride != null || fuelOverride != null || veOverride != null
}

/** Настройки пользователя, переживают перезапуск приложения (SharedPreferences). */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("carscan_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current)
        if (next == current) return
        prefs.edit()
            .putString(KEY_THEME, next.themeId)
            .putString(KEY_SOURCE, next.dataSource.name)
            .putString(KEY_DEVICE_ADDRESS, next.obdDeviceAddress)
            .putString(KEY_DEVICE_NAME, next.obdDeviceName)
            .putBoolean(KEY_XRAY, next.xray)
            .putString(KEY_VEHICLE, next.vehicleId)
            .putString(KEY_DISPLACEMENT, next.displacementOverride?.toString())
            .putString(KEY_TANK, next.tankOverride?.toString())
            .putString(KEY_FUEL, next.fuelOverride?.name)
            .putString(KEY_VE, next.veOverride?.toString())
            .apply()
        _settings.value = next
    }

    private fun load() = AppSettings(
        themeId = prefs.getString(KEY_THEME, null) ?: AppSettings().themeId,
        dataSource = prefs.getString(KEY_SOURCE, null)
            ?.let { name -> DataSourceType.entries.firstOrNull { it.name == name } }
            ?: DataSourceType.SIMULATOR,
        obdDeviceAddress = prefs.getString(KEY_DEVICE_ADDRESS, null),
        obdDeviceName = prefs.getString(KEY_DEVICE_NAME, null),
        xray = prefs.getBoolean(KEY_XRAY, true),
        vehicleId = prefs.getString(KEY_VEHICLE, null) ?: VehicleCatalog.DEFAULT_ID,
        displacementOverride = prefs.getString(KEY_DISPLACEMENT, null)?.toDoubleOrNull(),
        tankOverride = prefs.getString(KEY_TANK, null)?.toDoubleOrNull(),
        fuelOverride = prefs.getString(KEY_FUEL, null)?.let { name -> FuelType.entries.firstOrNull { it.name == name } },
        veOverride = prefs.getString(KEY_VE, null)?.toDoubleOrNull(),
    )

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_SOURCE = "data_source"
        const val KEY_DEVICE_ADDRESS = "obd_device_address"
        const val KEY_DEVICE_NAME = "obd_device_name"
        const val KEY_XRAY = "xray"
        const val KEY_VEHICLE = "vehicle_id"
        const val KEY_DISPLACEMENT = "vehicle_displacement"
        const val KEY_TANK = "vehicle_tank"
        const val KEY_FUEL = "vehicle_fuel"
        const val KEY_VE = "vehicle_ve"
    }
}
