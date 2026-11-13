package com.carscan.bluetooth

import com.carscan.data.obd.ObdException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Bluetooth устройства без Android API — чтобы логику включения можно было проверить тестом. */
interface BluetoothRadio {
    val isOn: Boolean

    /** Просит систему включить Bluetooth без окна. false — система отказала (Android 13+, запрет на устройстве). */
    fun requestEnable(): Boolean
}

enum class PowerResult {
    ALREADY_ON,
    TURNED_ON,
    /** Система не даёт включить без пользователя — нужно системное окно «Включить Bluetooth?». */
    REFUSED,
    /** Система согласилась, но Bluetooth так и не включился. */
    TIMEOUT,
}

/** Bluetooth выключен, и само приложение включить его не смогло. */
class BluetoothOffException(message: String, val canAskUser: Boolean) : ObdException(message)

object BluetoothPower {
    const val ENABLE_TIMEOUT_MS = 10_000L
    private const val POLL_MS = 200L

    /**
     * Включает Bluetooth, если он выключен, и ждёт, пока он заработает. На магнитоле Bluetooth бывает выключен
     * после запуска, и без этого адаптер подключался только после стороннего приложения, которое его включало.
     */
    suspend fun ensureOn(radio: BluetoothRadio, timeoutMs: Long = ENABLE_TIMEOUT_MS, pollMs: Long = POLL_MS): PowerResult {
        if (radio.isOn) return PowerResult.ALREADY_ON
        // SecurityException — нет разрешения на включение: то же, что отказ системы
        val accepted = runCatching { radio.requestEnable() }.getOrDefault(false)
        if (!accepted) return PowerResult.REFUSED
        val turnedOn = withTimeoutOrNull(timeoutMs) {
            while (!radio.isOn) delay(pollMs)
            true
        }
        return if (turnedOn == true) PowerResult.TURNED_ON else PowerResult.TIMEOUT
    }

    /** Ошибка для экрана и отчёта; null — Bluetooth работает. */
    fun failure(result: PowerResult): BluetoothOffException? = when (result) {
        PowerResult.ALREADY_ON, PowerResult.TURNED_ON -> null
        PowerResult.REFUSED -> BluetoothOffException(
            "Bluetooth выключен, и система не дала приложению включить его само. " +
                "Разрешите включение в системном окне или включите Bluetooth в настройках магнитолы",
            canAskUser = true,
        )
        PowerResult.TIMEOUT -> BluetoothOffException(
            "Bluetooth не включился за ${ENABLE_TIMEOUT_MS / 1000} с. Включите его в настройках магнитолы",
            canAskUser = false,
        )
    }
}
