package com.carscan.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.carscan.data.SettingsRepository
import com.carscan.data.SimulatedTelemetrySource
import com.carscan.data.TelegramReporter
import com.carscan.data.TelemetrySource
import com.carscan.data.UpdateRepository
import com.carscan.data.VehicleCatalog
import com.carscan.data.VehicleRepository
import com.carscan.data.WeatherRepository
import com.carscan.data.obd.ObdTelemetrySource
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    // Предоставляем единственный экземпляр (Singleton) нашего BluetoothManager для всего приложения
    single {
        val androidBluetoothManager = androidContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        com.carscan.bluetooth.BluetoothManager(androidContext(), androidBluetoothManager.adapter)
    }

    single { SettingsRepository(androidContext()) }

    // Справочник автомобилей (assets/vehicles.csv) и текущий профиль: марка, двигатель, бак, ручные правки
    single {
        val context = androidContext()
        VehicleRepository({ VehicleCatalog.fromAssets(context) }, get())
    }

    // Источники телеметрии: симулятор и реальный ELM327 по Bluetooth
    single<TelemetrySource>(named(SIMULATOR)) {
        val vehicles = get<VehicleRepository>()
        SimulatedTelemetrySource({ vehicles.profile() })
    }
    single<TelemetrySource>(named(OBD)) {
        val bluetooth = get<com.carscan.bluetooth.BluetoothManager>()
        val settings = get<SettingsRepository>()
        val vehicles = get<VehicleRepository>()
        ObdTelemetrySource({ vehicles.profile() }) { bluetooth.openObdTransport(settings.current.obdDeviceAddress) }
    }

    // Уличная температура из интернета (Open-Meteo)
    single { WeatherRepository(androidContext()) }

    // Обновления приложения из GitHub Releases
    single { UpdateRepository(androidContext()) }

    // Отчёты диагностики в Telegram-бот (если токен задан при сборке)
    single { TelegramReporter() }

    viewModel {
        com.carscan.ui.MainViewModel(
            simulator = get(named(SIMULATOR)),
            obd = get(named(OBD)),
            weatherRepository = get(),
            settingsRepository = get(),
            bluetooth = get(),
            vehicles = get(),
            updates = get(),
            telegram = get(),
        )
    }
}

private const val SIMULATOR = "simulator"
private const val OBD = "obd"
