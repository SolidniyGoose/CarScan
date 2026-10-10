package com.carscan

import android.app.Application
import com.carscan.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CarScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Инициализируем Koin для всего приложения
        startKoin {
            androidContext(this@CarScanApp)
            modules(appModule)
        }
    }
}
