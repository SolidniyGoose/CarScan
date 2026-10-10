package com.carscan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carscan.bluetooth.BluetoothManager
import com.carscan.bluetooth.PairedDevice
import com.carscan.data.AppRelease
import com.carscan.data.AppSettings
import com.carscan.data.ConnectionInfo
import com.carscan.data.ConnectionState
import com.carscan.data.CatalogModel
import com.carscan.data.DataSourceType
import com.carscan.data.FuelType
import com.carscan.data.OutsideWeather
import com.carscan.data.SettingsRepository
import com.carscan.data.TelemetrySource
import com.carscan.data.TripComputer
import com.carscan.data.TripStats
import com.carscan.data.UpdateRepository
import com.carscan.data.VehicleProfile
import com.carscan.data.VehicleRepository
import com.carscan.data.VehicleSpec
import com.carscan.data.VehicleTelemetry
import com.carscan.data.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class WeatherState(
    val current: OutsideWeather? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Обновление приложения из GitHub Releases. */
data class UpdateState(
    val installedVersion: String = "",
    val latest: AppRelease? = null,
    val checking: Boolean = false,
    /** 0..1 во время загрузки APK. */
    val downloadProgress: Float? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val available: Boolean
        get() = latest != null && UpdateRepository.isNewer(latest.version, installedVersion)
}

/** Пошаговый выбор автомобиля: марка → модель/поколение → двигатель. */
data class VehiclePickerState(
    val make: String? = null,
    val modelKey: String? = null,
    val makes: List<String> = emptyList(),
    val models: List<CatalogModel> = emptyList(),
    val variants: List<VehicleSpec> = emptyList(),
)

data class DashboardState(
    val telemetry: VehicleTelemetry = VehicleTelemetry(),
    val trip: TripStats = TripStats(),
    val weather: WeatherState = WeatherState(),
    val profile: VehicleProfile = VehicleProfile(),
    val settings: AppSettings = AppSettings(),
    val connection: ConnectionInfo = ConnectionInfo(),
    /** Поток телеметрии включён кнопкой питания. */
    val streaming: Boolean = false,
    val pairedDevices: List<PairedDevice> = emptyList(),
    /** Почему список адаптеров пуст: нет разрешения, Bluetooth выключен и т. п. */
    val bluetoothIssue: String? = null,

    // Навигация
    val selectedPanel: ActivePanel = ActivePanel.MAIN,
    val selectedNode: CarNode? = null,
    val vehiclePicker: VehiclePickerState = VehiclePickerState(),
    val update: UpdateState = UpdateState(),
) {
    val xrayMode: Boolean get() = settings.xray
    val dataSourceName: String get() = settings.dataSource.title

    val fuelLiters: Double?
        get() = telemetry.fuelLevelPercent?.let { it / 100.0 * profile.tankLiters }

    /** Запас хода по среднему расходу поездки. */
    val rangeKm: Double?
        get() {
            val liters = fuelLiters ?: return null
            val avg = trip.avgConsumptionL100?.takeIf { it > 0.5 } ?: return null
            return liters / avg * 100.0
        }

    /** Улица: приоритет у интернета, запасной вариант — датчик наружного воздуха ТС (PID 0146). */
    val outsideTempC: Double?
        get() = weather.current?.temperatureC ?: telemetry.ambientAirTemp?.toDouble()

    val nodeStatuses: List<NodeStatus>
        get() = CarNodes.evaluate(this)
}

enum class ActivePanel {
    MAIN, SENSORS_ALL, DIAGNOSTICS_ECU, EMISSIONS, NODE, SETTINGS, OBD_LOG, VEHICLE_PICKER
}

/** Действия пользователя на приборной панели. */
interface DashboardActions {
    fun selectPanel(panel: ActivePanel)
    fun closeNodePanel()
    fun onPanelAction(action: PanelAction)
    fun onChoice(group: ChoiceGroup, id: String)
    fun onStep(id: StepperId, direction: Int)
    fun togglePower()
}

/** Команды 3D-сцене, которые не являются состоянием (движение камеры). */
sealed interface SceneCommand {
    data class Focus(val node: CarNode) : SceneCommand
    data object ResetCamera : SceneCommand
}

class MainViewModel(
    private val simulator: TelemetrySource,
    private val obd: TelemetrySource,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val bluetooth: BluetoothManager,
    private val vehicles: VehicleRepository,
    private val updates: UpdateRepository,
) : ViewModel(), DashboardActions {

    private val _uiState = MutableStateFlow(
        DashboardState(profile = vehicles.profile(settingsRepository.current), settings = settingsRepository.current),
    )
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    private val _sceneCommands = MutableSharedFlow<SceneCommand>(extraBufferCapacity = 8)
    val sceneCommands: SharedFlow<SceneCommand> = _sceneCommands.asSharedFlow()

    /** Скачанный APK, который Activity передаёт системному установщику. */
    private val _installRequests = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val installRequests: SharedFlow<File> = _installRequests.asSharedFlow()
    private var updateJob: Job? = null
    private var downloadedApk: Pair<String, File>? = null

    /** Просьба к Activity запросить разрешение Bluetooth (Android 12+). */
    private val _bluetoothPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val bluetoothPermissionRequests: SharedFlow<Unit> = _bluetoothPermissionRequests.asSharedFlow()

    private val tripComputer = TripComputer()
    private var streamJob: Job? = null
    private var weatherJob: Job? = null

    private val activeSource: TelemetrySource
        get() = if (settingsRepository.current.dataSource == DataSourceType.BLUETOOTH) obd else simulator

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { s -> _uiState.update { it.copy(settings = s, profile = vehicles.profile(s)) } }
        }
        startStreaming()
        startWeatherUpdates()
        _uiState.update { it.copy(update = UpdateState(installedVersion = updates.installedVersion())) }
        viewModelScope.launch {
            delay(UPDATE_CHECK_DELAY_MS) // не мешаем загрузке 3D-сцены
            checkForUpdates()
        }
    }

    // ---------- Обновления ----------

    private fun setUpdate(transform: (UpdateState) -> UpdateState) = _uiState.update { it.copy(update = transform(it.update)) }

    private fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            setUpdate { it.copy(checking = true, message = null, isError = false) }
            try {
                val latest = updates.latestRelease()
                setUpdate { u ->
                    val next = u.copy(latest = latest, checking = false)
                    next.copy(
                        message = when {
                            latest == null -> "На GitHub пока нет релизов"
                            next.available -> "Доступна версия ${latest.version}"
                            else -> "Установлена последняя версия"
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpdate { it.copy(checking = false, message = "Не удалось проверить: ${e.message ?: "нет связи"}", isError = true) }
            }
        }
    }

    private fun downloadAndInstall() {
        val release = _uiState.value.update.latest ?: return
        if (updateJob?.isActive == true) return
        // Уже скачан (например, вернулись из настроек «Установка неизвестных приложений»)
        downloadedApk?.takeIf { it.first == release.version && it.second.exists() }?.let {
            _installRequests.tryEmit(it.second)
            return
        }
        updateJob = viewModelScope.launch {
            setUpdate { it.copy(downloadProgress = 0f, message = "Загрузка ${release.version}…", isError = false) }
            try {
                var lastPercent = -1
                val apk = updates.download(release) { progress ->
                    val percent = (progress * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        setUpdate { it.copy(downloadProgress = progress) }
                    }
                }
                if (updates.sameSignature(apk) == false) {
                    setUpdate {
                        it.copy(
                            message = "Релиз подписан другим ключом, чем установленная версия (из Android Studio). " +
                                "Один раз удалите приложение и установите APK со страницы релиза — дальше обновления пойдут отсюда.",
                            isError = true,
                        )
                    }
                } else {
                    downloadedApk = release.version to apk
                    setUpdate { it.copy(message = "Подтвердите установку в системном окне") }
                    _installRequests.tryEmit(apk)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpdate { it.copy(message = "Ошибка загрузки: ${e.message ?: "нет связи"}", isError = true) }
            } finally {
                setUpdate { it.copy(downloadProgress = null) }
            }
        }
    }

    /** Activity не смогла запустить установку: нужно разрешение «Установка неизвестных приложений». */
    fun onInstallPermissionMissing() {
        setUpdate { it.copy(message = "Разрешите CarScan установку приложений и нажмите «Обновить» ещё раз", isError = true) }
    }

    // ---------- Телеметрия ----------

    override fun togglePower() {
        if (_uiState.value.streaming) stopStreaming() else startStreaming()
    }

    private fun startStreaming() {
        if (streamJob?.isActive == true) return
        val source = activeSource
        _uiState.update { it.copy(streaming = true) }
        streamJob = viewModelScope.launch {
            launch { source.connection.collect { info -> _uiState.update { it.copy(connection = info) } } }
            // Машину заводят и глушат, адаптер теряет связь — переподключаемся, пока поток включён
            while (isActive) {
                try {
                    source.samples().collect { sample ->
                        val trip = tripComputer.onSample(sample, System.currentTimeMillis())
                        _uiState.update { it.copy(telemetry = sample, trip = trip) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Текст ошибки источник уже положил в connection
                }
                tripComputer.pause()
                _uiState.update { it.copy(telemetry = VehicleTelemetry()) }
                delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        tripComputer.pause()
        _uiState.update {
            it.copy(
                streaming = false,
                telemetry = VehicleTelemetry(),
                connection = ConnectionInfo(ConnectionState.IDLE, "Поток выключен"),
            )
        }
    }

    private fun restartStreaming() {
        if (!_uiState.value.streaming) return
        stopStreaming()
        startStreaming()
    }

    private fun resetTrip() {
        tripComputer.reset()
        _uiState.update { it.copy(trip = TripStats()) }
    }

    // ---------- Bluetooth ----------

    private fun refreshPairedDevices(askPermission: Boolean) {
        if (!bluetooth.hasConnectPermission()) {
            _uiState.update { it.copy(pairedDevices = emptyList(), bluetoothIssue = "Нужно разрешение «Устройства поблизости»") }
            if (askPermission) _bluetoothPermissionRequests.tryEmit(Unit)
            return
        }
        val result = runCatching { bluetooth.pairedDevices() }
        _uiState.update {
            it.copy(pairedDevices = result.getOrDefault(emptyList()), bluetoothIssue = result.exceptionOrNull()?.message)
        }
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        refreshPairedDevices(askPermission = false)
        if (granted) restartStreaming()
    }

    // ---------- Погода ----------

    /** Перезапускает цикл обновления (например, после выдачи разрешения на геолокацию). */
    fun refreshWeather() {
        weatherJob?.cancel()
        startWeatherUpdates()
    }

    private fun startWeatherUpdates() {
        weatherJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(weather = it.weather.copy(loading = true)) }
                try {
                    val weather = weatherRepository.fetchCurrent()
                    _uiState.update { it.copy(weather = WeatherState(current = weather)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Старое значение оставляем на экране, просто помечаем ошибку
                    _uiState.update { it.copy(weather = it.weather.copy(loading = false, error = "Нет связи с сервисом погоды")) }
                }
                delay(if (_uiState.value.weather.error == null) WEATHER_REFRESH_MS else WEATHER_RETRY_MS)
            }
        }
    }

    // ---------- Навигация и настройки ----------

    override fun selectPanel(panel: ActivePanel) {
        val hadNode = _uiState.value.selectedNode != null
        _uiState.update { it.copy(selectedPanel = panel, selectedNode = null) }
        if (hadNode) _sceneCommands.tryEmit(SceneCommand.ResetCamera)
        if (panel == ActivePanel.SETTINGS) refreshPairedDevices(askPermission = false)
    }

    fun selectNode(node: CarNode) {
        _uiState.update { it.copy(selectedPanel = ActivePanel.NODE, selectedNode = node) }
        _sceneCommands.tryEmit(SceneCommand.Focus(node))
    }

    /** Тап по подписи узла на 3D-модели. */
    fun onNodeTapped(id: String) {
        CarNode.fromId(id)?.let(::selectNode)
    }

    override fun closeNodePanel() = selectPanel(ActivePanel.MAIN)

    override fun onPanelAction(action: PanelAction) {
        when (action) {
            PanelAction.XRAY -> settingsRepository.update { it.copy(xray = !it.xray) }
            PanelAction.FOCUS -> _uiState.value.selectedNode?.let { _sceneCommands.tryEmit(SceneCommand.Focus(it)) }
            PanelAction.REFRESH_WEATHER -> refreshWeather()
            PanelAction.RESET_TRIP -> resetTrip()
            PanelAction.OBD_LOG -> selectPanel(ActivePanel.OBD_LOG)
            PanelAction.REFRESH_DEVICES -> refreshPairedDevices(askPermission = true)
            PanelAction.BACK_TO_SETTINGS -> selectPanel(ActivePanel.SETTINGS)
            PanelAction.PICK_VEHICLE -> {
                // Шаг 1 — список марок (текущая отмечена)
                _uiState.update { it.copy(vehiclePicker = VehiclePickerState(makes = vehicles.catalog.makes())) }
                selectPanel(ActivePanel.VEHICLE_PICKER)
            }
            PanelAction.PICKER_BACK -> {
                val picker = _uiState.value.vehiclePicker
                when {
                    picker.modelKey != null -> _uiState.update { it.copy(vehiclePicker = picker.copy(modelKey = null, variants = emptyList())) }
                    picker.make != null -> _uiState.update { it.copy(vehiclePicker = picker.copy(make = null, models = emptyList())) }
                    else -> selectPanel(ActivePanel.SETTINGS)
                }
            }
            PanelAction.CHECK_UPDATES -> checkForUpdates()
            PanelAction.INSTALL_UPDATE -> downloadAndInstall()
            PanelAction.RESET_VEHICLE -> settingsRepository.update {
                it.copy(displacementOverride = null, tankOverride = null, fuelOverride = null, veOverride = null)
            }
        }
    }

    override fun onStep(id: StepperId, direction: Int) {
        val spec = vehicles.spec()
        val profile = _uiState.value.profile
        // Значение, совпавшее со справочником, хранится как «нет правки»
        fun override(value: Double, base: Double): Double? = value.takeIf { kotlin.math.abs(it - base) > 1e-6 }
        settingsRepository.update { s ->
            when (id) {
                StepperId.DISPLACEMENT -> {
                    val v = Math.round((profile.displacementL + 0.1 * direction).coerceIn(0.6, 8.5) * 10) / 10.0
                    s.copy(displacementOverride = override(v, spec.displacementL))
                }
                StepperId.TANK -> {
                    val v = (profile.tankLiters + direction).coerceIn(20.0, 200.0)
                    s.copy(tankOverride = override(v, spec.tankLiters))
                }
                StepperId.VE -> {
                    val v = Math.round((profile.volumetricEfficiency + 0.05 * direction).coerceIn(0.5, 1.2) * 100) / 100.0
                    s.copy(veOverride = override(v, VehicleProfile.DEFAULT_VE))
                }
            }
        }
    }

    override fun onChoice(group: ChoiceGroup, id: String) {
        when (group) {
            ChoiceGroup.THEME -> settingsRepository.update { it.copy(themeId = id) }
            ChoiceGroup.VIEW_MODE -> settingsRepository.update { it.copy(xray = id == VIEW_XRAY) }
            ChoiceGroup.DATA_SOURCE -> {
                val source = DataSourceType.entries.firstOrNull { it.name == id } ?: return
                if (source == settingsRepository.current.dataSource) return
                settingsRepository.update { it.copy(dataSource = source) }
                if (source == DataSourceType.BLUETOOTH) refreshPairedDevices(askPermission = true)
                restartStreaming()
            }
            ChoiceGroup.FUEL_TYPE -> {
                val fuel = FuelType.entries.firstOrNull { it.name == id } ?: return
                settingsRepository.update { it.copy(fuelOverride = fuel.takeIf { f -> f != vehicles.spec().fuel }) }
            }
            ChoiceGroup.VEHICLE_MAKE -> _uiState.update {
                it.copy(vehiclePicker = it.vehiclePicker.copy(make = id, modelKey = null, models = vehicles.catalog.models(id)))
            }
            ChoiceGroup.VEHICLE_MODEL -> _uiState.update {
                val make = it.vehiclePicker.make ?: return
                it.copy(vehiclePicker = it.vehiclePicker.copy(modelKey = id, variants = vehicles.catalog.variants(make, id)))
            }
            ChoiceGroup.VEHICLE_VARIANT -> {
                if (vehicles.catalog.find(id) == null) return
                // Новый автомобиль — ручные правки от прежнего не переносим
                settingsRepository.update {
                    it.copy(vehicleId = id, displacementOverride = null, tankOverride = null, fuelOverride = null, veOverride = null)
                }
                selectPanel(ActivePanel.SETTINGS)
            }
            ChoiceGroup.OBD_DEVICE -> {
                val device = _uiState.value.pairedDevices.firstOrNull { it.address == id } ?: return
                settingsRepository.update { it.copy(obdDeviceAddress = device.address, obdDeviceName = device.name) }
                if (settingsRepository.current.dataSource == DataSourceType.BLUETOOTH) restartStreaming()
            }
        }
    }

    companion object {
        const val VIEW_XRAY = "xray"
        const val VIEW_NORMAL = "normal"
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val UPDATE_CHECK_DELAY_MS = 5_000L
        private const val WEATHER_REFRESH_MS = 15 * 60 * 1000L
        private const val WEATHER_RETRY_MS = 60 * 1000L
    }
}
