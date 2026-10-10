package com.carscan.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Менеджер для работы с Bluetooth-подключением к ELM327 (OBD2) адаптерам.
 */
@SuppressLint("MissingPermission") // В реальном проекте необходимо проверять BLUETOOTH_CONNECT (Android 12+)
class BluetoothManager(
    private val bluetoothAdapter: BluetoothAdapter?
) {

    // Стандартный UUID для SPP (Serial Port Profile)
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
     * Пытается подключиться к устройству тремя способами.
     * Возвращает [BluetoothSocket] в случае успеха, либо null, если все попытки провалились.
     */
    suspend fun connectToDevice(device: BluetoothDevice): BluetoothSocket? = withContext(Dispatchers.IO) {
        bluetoothAdapter?.cancelDiscovery() // Важно для ускорения и стабильности подключения

        // Способ 1: Стандартный RFCOMM (Secure)
        var socket = attemptStandardConnect(device)
        if (socket != null) return@withContext socket

        // Способ 2: Insecure RFCOMM (для некоторых китайских клонов ELM327)
        socket = attemptInsecureConnect(device)
        if (socket != null) return@withContext socket

        // Способ 3: Reflection (канал 1-30) - Самый "жесткий" фоллбэк для старых магнитол
        socket = attemptReflectionConnect(device)
        return@withContext socket
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

    private fun attemptReflectionConnect(device: BluetoothDevice): BluetoothSocket? {
        // Перебираем каналы от 1 до 30. Чаще всего работает канал 1.
        for (channel in 1..30) {
            var socket: BluetoothSocket? = null
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = method.invoke(device, channel) as BluetoothSocket
                socket.connect()
                return socket
            } catch (e: Exception) {
                safeCloseSocket(socket)
            }
        }
        return null
    }

    private fun safeCloseSocket(socket: BluetoothSocket?) {
        try {
            socket?.close()
        } catch (e: IOException) {
            // Игнорируем ошибку при закрытии
        }
    }
}
