package com.carscan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.carscan.data.UpdateRepository
import com.carscan.ui.MainViewModel
import com.carscan.ui.dashboard.CarScanDashboard
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Palettes
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()
    private val updates: UpdateRepository by inject()

    // Геолокация нужна только для уличной температуры; без неё погода определяется по IP
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) viewModel.refreshWeather()
    }

    // Android 12+: список сопряжённых устройств и подключение к ELM327 требуют «Устройства поблизости»
    private val bluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onBluetoothPermissionResult(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterImmersiveMode()
        // Приборная панель: экран не гаснет, пока приложение открыто
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestLocationIfNeeded()

        lifecycleScope.launch {
            viewModel.bluetoothPermissionRequests.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        }

        // Скачанное обновление → системный установщик (нужно разрешение «Установка неизвестных приложений»)
        lifecycleScope.launch {
            viewModel.installRequests.collect { apk ->
                if (updates.canInstallPackages()) {
                    startActivity(updates.installIntent(apk))
                } else {
                    viewModel.onInstallPermissionMissing()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    }
                }
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
            CarScanTheme(Palettes.byId(state.settings.themeId)) {
                CarScanDashboard(viewModel)
            }
        }
    }

    /** Без системных панелей; они появляются временно по свайпу от края. */
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestLocationIfNeeded() {
        val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        val granted = permissions.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) locationPermission.launch(permissions)
    }
}
