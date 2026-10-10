package com.carscan.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Foreground Service для удержания активного соединения с ELM327.
 * Гарантирует, что система не убьет процесс чтения данных при сворачивании приложения.
 */
class ObdConnectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager: BluetoothManager by inject()
    
    companion object {
        private const val CHANNEL_ID = "ObdConnectionChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startConnection()
            ACTION_STOP -> stopConnection()
        }
        return START_STICKY
    }

    private fun startConnection() {
        val notification = createNotification("Подключение к ELM327...")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            // TODO: Здесь будет логика извлечения нужного устройства и вызова bluetoothManager.connectToDevice()
            // val device = bluetoothManager.getBondedObdDevices().firstOrNull()
            // val socket = device?.let { bluetoothManager.connectToDevice(it) }
            
            // Если сокет получен успешно, переходим в цикл чтения InputStream и отправки данных во ViewModel
            updateNotification("Подключено к OBD2")
        }
    }

    private fun stopConnection() {
        // TODO: Закрыть сокет и остановить чтение
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = createNotification(text)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CarScan OBD2")
            .setContentText(contentText)
            // .setSmallIcon(R.drawable.ic_obd_connected) // Временно закомментировано, нужен ресурс
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Соединение с OBD2",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Используется для поддержания фонового соединения с автомобилем"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Мы используем Started Service, а не Bound Service
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
