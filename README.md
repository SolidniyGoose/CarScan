# CarScan

Android-приложение для магнитол и планшетов: телеметрия автомобиля через адаптер ELM327 (OBD2) и 3D-модель машины с подсветкой узлов.

- **Язык:** Kotlin.
- **Интерфейс:** Jetpack Compose (Material 3).
- **Архитектура:** MVVM, внедрение зависимостей через Koin.
- **3D:** Google `<model-viewer>` во встроенном WebView, работает без интернета.
- **Обновления:** через GitHub Releases.

## Сборка

Нужны JDK 21+ и Android SDK с платформой API 37. Путь к SDK указывается в `local.properties` (`sdk.dir=...`); Android Studio создаёт этот файл сама.

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit-тесты (без устройства)
```

Локальные файлы с секретами в git не попадают:

| Файл | Зачем | Инструкция |
|---|---|---|
| `keystore.properties` + `*.jks` | ключ подписи (тот же, что у релизов) | [docs/UPDATES.md](docs/UPDATES.md) |
| `telegram.properties` | бот для отчётов диагностики | [docs/DEBUG_REPORT.md](docs/DEBUG_REPORT.md) |

Без них проект тоже собирается. APK тогда подписан отладочным ключом, и отправка отчёта в бот отключена.

## Выпуск версии

1. Поднять `appVersion` в `gradle.properties`.
2. Закоммитить.
3. `git tag vX.Y.Z` и `git push origin main vX.Y.Z`.

GitHub Actions ([release.yml](.github/workflows/release.yml)) соберёт подписанный APK и создаст релиз. Приложение найдёт его в «…» → «Обновления». Если сборка упала, причина видна в аннотациях запуска на странице Actions.

## Структура

```
app/src/main/
├── java/com/carscan/
│   ├── MainActivity.kt          полный экран, разрешения, установка обновлений, «Поделиться»
│   ├── CarScanApp.kt            запуск Koin
│   ├── di/AppModule.kt          все зависимости (источники данных, репозитории, ViewModel)
│   ├── bluetooth/               сопряжённые адаптеры, SPP-сокет до ELM327
│   ├── data/
│   │   ├── TelemetrySource.kt   интерфейс источника + состояние подключения, проверка датчиков, глубокий скан
│   │   ├── VehicleTelemetry.kt  снимок показаний, профиль автомобиля, команды производителя (ExtendedPid)
│   │   ├── obd/                 ELM327: протокол (Elm327.kt), формулы SAE J1979 (ObdDecode.kt), опрос (ObdTelemetrySource.kt)
│   │   ├── SimulatedTelemetrySource.kt  симулятор езды
│   │   ├── TripComputer.kt      бортовой компьютер: пробег, расход, запас хода
│   │   ├── AmbientTempFilter.kt датчик наружного воздуха без нагрева от двигателя
│   │   ├── VehicleCatalog.kt    справочник марок/двигателей (assets/vehicles.csv)
│   │   ├── WeatherRepository.kt погода: Open-Meteo, запасной MET Norway; геопозиция
│   │   ├── UpdateRepository.kt  обновления из GitHub Releases (докачка, проверка подписи)
│   │   ├── TelegramReporter.kt  отправка отчёта в Telegram-бот
│   │   └── SettingsRepository.kt настройки (SharedPreferences)
│   └── ui/
│       ├── MainViewModel.kt     состояние экрана (DashboardState) и все действия
│       ├── PanelContent.kt      содержимое правой панели как данные (тестируется без Compose)
│       ├── DiagnosticReport.kt  текст отчёта диагностики
│       ├── CarNodes.kt          статусы узлов для подписей на 3D-модели
│       ├── dashboard/           Compose: экран, приборы, часы, панель, мост к WebView (CarSceneView.kt)
│       └── theme/               цветовые темы
├── assets/
│   ├── viewer.html              3D-сцена: рентген, подписи, камера, адаптивное разрешение
│   ├── js/model-viewer.min.js   model-viewer локально
│   ├── models/car.glb           модель (после tools/add_car_parts.py и tools/optimize_model.py)
│   └── vehicles.csv             справочник автомобилей
app/src/test/                    unit-тесты: формулы OBD, эмулятор ELM327, отчёт, погода, загрузка обновлений
docs/
├── OBD_CHECK.md                 как проверить показания на реальной машине
├── UPDATES.md                   ключ подписи и выпуск обновлений
└── DEBUG_REPORT.md              Telegram-бот для отчётов
tools/
├── add_car_parts.py             добавляет в car.glb двигатель и бак (идемпотентно)
├── optimize_model.py            облегчает car.glb без изменения геометрии
└── create_release_key.ps1       создание ключа подписи (Windows)
```

Папка `uav-viewer/` — отдельный прототип, к CarScan не относится. Она попала в первый коммит.

## Как устроены данные

1. `ObdTelemetrySource` подключается к ELM327 и определяет протокол и поддерживаемые PID. Затем идёт проверка датчиков (`selfTest`) и цикл опроса: быстрые параметры опрашиваются каждый цикл, медленные — каждый 5-й или 20-й.
2. `MainViewModel` собирает снимки в `DashboardState`, ведёт бортовой компьютер и историю для отчёта. При обрыве связи он переподключается.
3. Compose рисует панели по `DashboardState`. `CarSceneView` передаёт в `viewer.html` только подписи и статусы узлов. Сцена перерисовывается лишь при заметных изменениях.

Данные производителя (то, чего нет в стандарте OBD2) задаются через `ExtendedPid` в `VehicleProfile`: адрес блока, команда, формула. Как найти такие команды для своей машины — в [CLAUDE.md](CLAUDE.md), раздел «Открытые задачи».
