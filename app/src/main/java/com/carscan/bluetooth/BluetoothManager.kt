package com.carscan.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.carscan.data.obd.ObdException
import com.carscan.data.obd.ObdTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Сопряжённое устройство для выбора адаптера в настройках. */
data class PairedDevice(val name: String, val address: String) {
    /** Типичные имена ELM327-адаптеров — такие показываем первыми. */
    val looksLikeObd: Boolean
        get() = OBD_NAME_HINTS.any { name.contains(it, ignoreCase = true) }

    private companion object {
        val OBD_NAME_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "SCAN")
    }
}

/**
 * Менеджер для работы с Bluetooth-подключением к ELM327 (OBD2) адаптерам.
 */
@SuppressLint("MissingPermission") // Разрешение BLUETOOTH_CONNECT проверяется в hasConnectPermission()
class BluetoothManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?
) {

    // Стандартный UUID для SPP (Serial Port Profile)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** На Android 12+ для списка устройств и подключения нужно разрешение «Устройства поблизости». */
    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Все сопряжённые устройства; похожие на OBD-адаптеры — первыми. */
    fun pairedDevices(): List<PairedDevice> {
        val adapter = requireReadyAdapter()
        return adapter.bondedDevices
            .map { PairedDevice(name = it.name ?: it.address, address = it.address) }
            .sortedWith(compareByDescending<PairedDevice> { it.looksLikeObd }.thenBy { it.name })
    }

    /** Открывает SPP-канал к выбранному адаптеру. */
    suspend fun openObdTransport(address: String?): ObdTransport {
        val adapter = requireReadyAdapter()
        if (address == null) throw ObdException("Адаптер не выбран: «…» → Настройки → Адаптер ELM327")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw ObdException("Неверный адрес адаптера: $address", e)
        }
        val socket = connectToDevice(device)
            ?: throw ObdException("Не удалось подключиться к «${device.name ?: address}». Адаптер вставлен в разъём OBD и включён?")
        return SocketTransport(socket)
    }

    private fun requireReadyAdapter(): BluetoothAdapter {
        val adapter = bluetoothAdapter ?: throw ObdException("На устройстве нет Bluetooth")
        if (!hasConnectPermission()) throw ObdException("Нет разрешения «Устройства поблизости» для Bluetooth")
        if (!adapter.isEnabled) throw ObdException("Bluetooth выключен")
        return adapter
    }

    /**
     * Возвращает список сопряженных (bonded) устройств, отфильтрованный по имени (содержит "OBD").
     * Если фильтр пустой, возвращает все устройства.
     */
    fun getBondedObdDevices(nameFilter: String = "OBD"): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }
        val pairedDevices = bluetoothAdapter.bondedDevices
        return pairedDevices.filter { device ->
            device.name?.contains(nameFilter, ignoreCase = true) == true
        }
    }

    /**
     * Способы подключения по порядку: защищённый SPP, незащищённый SPP (часть клонов ELM327),
     * прямой канал RFCOMM 1–3 (старые магнитолы; так подключился и Car Scanner к адаптеру из отчёта).
     */
    private enum class ConnectMethod { SECURE, INSECURE, CHANNEL_1, CHANNEL_2, CHANNEL_3 }

    private val prefs = context.getSharedPreferences("carscan_bluetooth", Context.MODE_PRIVATE)

    /**
     * Пытается подключиться к устройству всеми способами. Сработавший запоминается и в следующий раз
     * пробуется первым: неудачная попытка у клона стоит несколько секунд.
     * Возвращает [BluetoothSocket] в случае успеха, либо null, если все попытки провалились.
     */
    suspend fun connectToDevice(device: BluetoothDevice): BluetoothSocket? = withContext(Dispatchers.IO) {
        bluetoothAdapter?.cancelDiscovery() // Важно для ускорения и стабильности подключения

        val key = KEY_METHOD_PREFIX + device.address
        val saved = prefs.getString(key, null)?.let { name -> ConnectMethod.entries.firstOrNull { it.name == name } }
        val order = listOfNotNull(saved) + ConnectMethod.entries.filter { it != saved }
        for (method in order) {
            val socket = when (method) {
                ConnectMethod.SECURE -> attemptStandardConnect(device)
                ConnectMethod.INSECURE -> attemptInsecureConnect(device)
                ConnectMethod.CHANNEL_1 -> attemptChannelConnect(device, 1)
                ConnectMethod.CHANNEL_2 -> attemptChannelConnect(device, 2)
                ConnectMethod.CHANNEL_3 -> attemptChannelConnect(device, 3)
            } ?: continue
            if (method != saved) prefs.edit().putString(key, method.name).apply()
            return@withContext socket
        }
        null
    }

    private fun attemptStandardConnect(device: BluetoothDevice): BluetoothSocket? {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            return socket
        } catch (e: IOException) {
            safeCloseSocket(socket)
            return null
        }
    }

    private fun attemptInsecureConnect(device: BluetoothDevice): BluetoothSocket? {
        var socket: BluetoothSocket? = null
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            return socket
        } catch (e: IOException) {
            safeCloseSocket(socket)
            return null
        }
    }

    /**
     * Прямой канал RFCOMM через скрытый метод. Адаптеры ELM327 слушают канал 1 (реже 2–3). Перебор всех 30 каналов
     * при выключенном адаптере занимал минуты, прежде чем пользователь видел ошибку.
     */
    private fun attemptChannelConnect(device: BluetoothDevice, channel: Int): BluetoothSocket? {
        var socket: BluetoothSocket? = null
        try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            socket = method.invoke(device, channel) as BluetoothSocket
            socket.connect()
            return socket
        } catch (e: Exception) {
            safeCloseSocket(socket)
            return null
        }
    }

    private fun safeCloseSocket(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Игнорируем ошибку при закрытии
        }
    }

    private companion object {
        const val KEY_METHOD_PREFIX = "connect_method_"
    }

    private class SocketTransport(private val socket: BluetoothSocket) : ObdTransport {
        override val input: InputStream = socket.inputStream
        override val output: OutputStream = socket.outputStream
        override fun close() = socket.close()
    }
}
