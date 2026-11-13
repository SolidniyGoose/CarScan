package com.carscan.bluetooth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Включение Bluetooth перед подключением к адаптеру. */
class BluetoothPowerTest {

    /**
     * Bluetooth магнитолы: [turnsOnAfterChecks] — через сколько проверок после просьбы он включится
     * (null — не включится никогда). [refuse] — система не даёт включить без окна, [throws] — нет разрешения.
     */
    private class FakeRadio(
        var on: Boolean = false,
        private val refuse: Boolean = false,
        private val throws: Boolean = false,
        private val turnsOnAfterChecks: Int? = 3,
    ) : BluetoothRadio {
        var enableRequests = 0
        private var checksAfterRequest = 0

        override val isOn: Boolean
            get() {
                if (!on && enableRequests > 0 && turnsOnAfterChecks != null && ++checksAfterRequest >= turnsOnAfterChecks) on = true
                return on
            }

        override fun requestEnable(): Boolean {
            if (throws) throw SecurityException("нет BLUETOOTH_CONNECT")
            enableRequests++
            return !refuse
        }
    }

    private fun ensureOn(radio: FakeRadio) = runBlocking { BluetoothPower.ensureOn(radio, timeoutMs = 200, pollMs = 1) }

    @Test
    fun `включённый Bluetooth не трогаем`() {
        val radio = FakeRadio(on = true)
        assertEquals(PowerResult.ALREADY_ON, ensureOn(radio))
        assertEquals(0, radio.enableRequests)
        assertNull(BluetoothPower.failure(PowerResult.ALREADY_ON))
    }

    @Test
    fun `выключенный Bluetooth включаем и ждём, пока заработает`() {
        val radio = FakeRadio(turnsOnAfterChecks = 5)
        assertEquals(PowerResult.TURNED_ON, ensureOn(radio))
        assertEquals("просим один раз", 1, radio.enableRequests)
        assertTrue(radio.on)
        assertNull(BluetoothPower.failure(PowerResult.TURNED_ON))
    }

    @Test
    fun `система не даёт включить - просим пользователя в окне`() {
        assertEquals(PowerResult.REFUSED, ensureOn(FakeRadio(refuse = true)))
        assertEquals("нет разрешения — тоже отказ", PowerResult.REFUSED, ensureOn(FakeRadio(throws = true)))
        val error = BluetoothPower.failure(PowerResult.REFUSED)!!
        assertTrue(error.canAskUser)
        assertTrue(error.message!!.contains("настройках магнитолы"))
    }

    @Test
    fun `Bluetooth не включился - понятная ошибка без окна`() {
        val radio = FakeRadio(turnsOnAfterChecks = null)
        assertEquals(PowerResult.TIMEOUT, ensureOn(radio))
        assertFalse(radio.on)
        val error = BluetoothPower.failure(PowerResult.TIMEOUT)!!
        assertFalse("окно не поможет: система согласилась, но не включила", error.canAskUser)
        assertEquals("Bluetooth не включился за 10 с. Включите его в настройках магнитолы", error.message)
    }
}
