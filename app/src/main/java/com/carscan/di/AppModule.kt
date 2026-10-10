package com.carscan.di

import android.bluetooth.BluetoothManager
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Предоставляем единственный экземпляр (Singleton) нашего BluetoothManager для всего приложения
    single {
        val androidBluetoothManager = androidContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        com.carscan.bluetooth.BluetoothManager(androidBluetoothManager.adapter)
    }
    
    // Добавляем ViewModel
    viewModel { com.carscan.ui.MainViewModel() }
}
