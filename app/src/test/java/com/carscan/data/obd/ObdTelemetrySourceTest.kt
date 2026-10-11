package com.carscan.data.obd

import com.carscan.data.ConnectionState
import com.carscan.data.ExtendedPid
import com.carscan.data.FuelType
import com.carscan.data.ProbeVerdict
import com.carscan.data.VehicleProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Эмулятор ELM327: принимает команды по "\r" и отвечает как настоящий адаптер на CAN-машине —
 * с эхом до ATE0, «SEARCHING...», приглашением ">" и «NO DATA» на неподдерживаемые PID.
 */
private class FakeElm327(private val car: Map<String, String>) : ObdTransport {
    val commands = mutableListOf<String>()
    private val pending = ArrayDeque<Byte>()
    private val lock = Any()
    private var echo = true
    var closed = false

    override val output = object : OutputStream() {
        private val line = StringBuilder()
        override fun write(b: Int) {
            if (b.toChar() != '\r') {
                line.append(b.toChar())
                return
            }
            val cmd = line.toString().trim().uppercase(Locale.US)
            line.clear()
            commands += cmd
            if (cmd == "ATE0") echo = false
            val reply = car[cmd] ?: if (cmd.startsWith("AT")) "OK" else "NO DATA"
            val text = (if (echo) "$cmd\r" else "") + reply.replace("\n", "\r") + "\r\r>"
            synchronized(lock) { text.toByteArray(Charsets.US_ASCII).forEach { pending.addLast(it) } }
        }
    }

    override val input = object : InputStream() {
        override fun available(): Int = synchronized(lock) { pending.size }
        override fun read(): Int = synchronized(lock) { pending.removeFirstOrNull()?.toInt() ?: -1 }
        override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(lock) {
            var n = 0
            while (n < len && pending.isNotEmpty()) b[off + n++] = pending.removeFirst()
            n
        }
    }

    override fun close() {
        closed = true
    }

    /** Команды обычного опроса — после проверки датчиков (она заканчивается на ATH0). */
    val pollCommands: List<String> get() = commands.drop(commands.lastIndexOf("ATH0") + 1)
}

private fun mask(base: Int, vararg pids: Int): String {
    val bytes = IntArray(4)
    for (pid in pids) {
        val i = pid - base - 1
        bytes[i / 8] = bytes[i / 8] or (1 shl (7 - i % 8))
    }
    return bytes.joinToString(" ") { String.format(Locale.US, "%02X", it) }
}

class ObdTelemetrySourceTest {

    /** Бензиновый автомобиль на CAN 11 бит без PID 5E: расход считается по MAF. */
    private val gasolineCar = mapOf(
        "ATZ" to "\nELM327 v1.5",
        "ATI" to "ELM327 v1.5",
        "ATDPN" to "A6",
        "ATRV" to "14.2V",
        "0100" to "SEARCHING...\n41 00 " + mask(0x00, 0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0F, 0x10, 0x20),
        "0120" to "41 20 " + mask(0x20, 0x2F, 0x40),
        "0140" to "41 40 " + mask(0x40, 0x46),
        "0101" to "41 01 00 07 65 00",
        "0103" to "41 03 02 00",
        "0104" to "41 04 50",          // 31.4 %
        "0105" to "41 05 7B",          // 83 °C
        "0106" to "41 06 80",          // 0 %
        "0107" to "41 07 7E",          // -1.56 %
        "010B" to "41 0B 1E",          // 30 кПа
        "010C" to "41 0C 1A F8",       // 1726 об/мин
        "010D" to "41 0D 3C",          // 60 км/ч
        "010F" to "41 0F 41",          // 25 °C
        "0110" to "41 10 03 E8",       // 10.00 г/с
        "012F" to "41 2F 80",          // 50.2 %
        "0146" to "41 46 32",          // 10 °C
        "06A0" to "46 A0 78 00 00 00", // MID A2…A5 → 4 цилиндра
        "06A2" to "013\n0: 46 A2 0B 24 00 00\n1: 00 00 FF FF A2 0C 24\n2: 00 02 00 00 FF FF 00",
        "06A3" to "013\n0: 46 A3 0B 24 00 00\n1: 00 00 FF FF A3 0C 24\n2: 00 00 00 00 FF FF 00",
        "06A4" to "013\n0: 46 A4 0B 24 00 00\n1: 00 00 FF FF A4 0C 24\n2: 00 00 00 00 FF FF 00",
        "06A5" to "013\n0: 46 A5 0B 24 00 00\n1: 00 00 FF FF A5 0C 24\n2: 00 00 00 00 FF FF 00",
    )

    @Test
    fun `инициализация и первый снимок с реальными формулами`() = runBlocking {
        val elm = FakeElm327(gasolineCar)
        val source = ObdTelemetrySource({ VehicleProfile() }) { elm }
        val t = source.samples().first()

        assertEquals(1726, t.rpm)
        assertEquals(60, t.speedKmh)
        assertEquals(83, t.coolantTemp)
        assertEquals(25, t.intakeAirTemp)
        assertEquals(10, t.ambientAirTemp)
        assertEquals(31.4, t.engineLoad!!, 0.1)
        assertEquals(50.2, t.fuelLevelPercent!!, 0.1)
        assertEquals(14.2, t.voltage!!, 1e-9)
        assertEquals(30, t.intakePressureKpa)
        assertEquals(-1.56, t.longTermFuelTrim!!, 0.01)
        assertEquals("Замкнутый контур (датчик O₂)", t.fuelSystemStatus)
        // Расход по MAF: 10 г/с → 3.287 л/ч с поправкой на коррекцию -1.56 %
        assertEquals(3.287 * (1 - 0.0156), t.fuelRateLph!!, 0.01)
        assertFalse(t.checkEngineOn)
        assertEquals(listOf(2, 0, 0, 0), t.misfires)
        assertTrue(t.readiness.isNotEmpty() && t.readiness.all { it.passed })
        // Чего нет в стандартном OBD — честно null
        assertNull(t.atfTemp)
        assertNull(t.cabinTemp)
        assertNull(t.gear)

        val info = source.connection.value
        assertEquals("ELM327 v1.5", info.adapter)
        assertEquals("ISO 15765-4 CAN (11 бит, 500 кбит/с)", info.protocol)
        assertTrue(0x2F in info.supportedPids && 0x46 in info.supportedPids)
        assertTrue(elm.closed) // после отмены сбора сокет закрыт
    }

    @Test
    fun `неподдерживаемые PID не запрашиваются`() = runBlocking {
        val elm = FakeElm327(gasolineCar)
        ObdTelemetrySource({ VehicleProfile() }) { elm }.samples().take(25).toList()
        assertFalse("015E не заявлен — не спрашиваем", "015E" in elm.pollCommands)
        assertFalse("0142 не нужен, AT RV работает", "0142" in elm.pollCommands)
        assertEquals("ATE0 до первого запроса данных", "ATE0", elm.commands[1])
    }

    @Test
    fun `без MAF расход считается по MAP и объёму двигателя из профиля`() = runBlocking {
        val car = gasolineCar - "0110" + mapOf(
            "0100" to "SEARCHING...\n41 00 " + mask(0x00, 0x01, 0x03, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0F, 0x20),
            "010C" to "41 0C 0C 80", // 800 об/мин
            "010B" to "41 0B 1E",    // 30 кПа
            "010F" to "41 0F 41",    // 25 °C
        )
        val profile = VehicleProfile(displacementL = 1.6, volumetricEfficiency = 0.85)
        val elm = FakeElm327(car)
        val t = ObdTelemetrySource({ profile }) { elm }.samples().first()
        // 3.18 г/с воздуха → 3.18 / 14.7 / 745 × 3600 ≈ 1.045 л/ч, с коррекцией -1.56 %
        assertEquals(1.045 * (1 - 0.0156), t.fuelRateLph!!, 0.01)
        assertEquals("MAP × 1.6 л", t.fuelRateMethod)
        assertFalse("MAF не заявлен — не спрашиваем", "0110" in elm.pollCommands)
    }

    @Test
    fun `для дизеля расход по воздуху не считается`() = runBlocking {
        val profile = VehicleProfile(fuelType = FuelType.DIESEL)
        val t = ObdTelemetrySource({ profile }) { FakeElm327(gasolineCar) }.samples().first()
        assertNull(t.fuelRateLph)
    }

    @Test
    fun `PID 5E используется вместо расчёта по MAF`() = runBlocking {
        val car = gasolineCar + mapOf(
            "0140" to "41 40 " + mask(0x40, 0x46, 0x5E),
            "015E" to "41 5E 00 C8", // 10 л/ч
        )
        val t = ObdTelemetrySource({ VehicleProfile() }) { FakeElm327(car) }.samples().first()
        assertEquals(10.0, t.fuelRateLph!!, 1e-9)
        assertEquals("PID 5E", t.fuelRateMethod)
    }

    @Test
    fun `команда производителя для температуры АКПП`() = runBlocking {
        val car = gasolineCar + mapOf("221940" to "62 19 40 7D") // 0x7D - 40 = 85 °C
        val profile = VehicleProfile(atfTempCommand = ExtendedPid("7E1", "221940") { it[0] - 40.0 })
        val elm = FakeElm327(car)
        val t = ObdTelemetrySource({ profile }) { elm }.samples().first()
        assertEquals(85, t.atfTemp)
        val i = elm.commands.indexOf("221940")
        assertEquals("ATSH7E1", elm.commands[i - 1])
        assertEquals("ATSH7DF", elm.commands[i + 1]) // заголовок вернули
    }

    @Test
    fun `зажигание выключено - понятная ошибка`() = runBlocking {
        val car = gasolineCar + mapOf("0100" to "SEARCHING...\nUNABLE TO CONNECT")
        val source = ObdTelemetrySource({ VehicleProfile() }) { FakeElm327(car) }
        try {
            source.samples().first()
            fail("ожидалась ошибка")
        } catch (e: ObdException) {
            assertTrue(e.message!!.contains("включите зажигание"))
        }
        assertEquals(ConnectionState.ERROR, source.connection.value.state)
    }

    @Test
    fun `журнал хранит сырые ответы и расшифровку`() = runBlocking {
        val source = ObdTelemetrySource({ VehicleProfile() }) { FakeElm327(gasolineCar) }
        source.samples().take(2).toList()
        val rpm = source.connection.value.log.last { it.command == "010C" }
        assertEquals("410C1AF8", rpm.response.replace(" ", ""))
        assertEquals("1726 об/мин", rpm.decoded)
    }

    @Test
    fun `проверка датчиков находит незаявленный уровень топлива и включает его опрос`() = runBlocking {
        // ЭБУ не заявляет 2F в маске, но на прямой запрос отвечает (так бывает у части машин)
        val car = gasolineCar + mapOf("0120" to "41 20 " + mask(0x20, 0x40))
        val elm = FakeElm327(car)
        val source = ObdTelemetrySource({ VehicleProfile() }) { elm }
        val t = source.samples().take(21).toList().last()

        val probes = source.connection.value.selfTest.associateBy { it.command }
        assertEquals(ProbeVerdict.UNDECLARED, probes.getValue("012F").verdict)
        assertEquals("50.2 %", probes.getValue("012F").decoded)
        assertEquals(ProbeVerdict.OK, probes.getValue("010C").verdict)
        assertEquals(ProbeVerdict.NOT_SUPPORTED, probes.getValue("015E").verdict)
        assertEquals(ProbeVerdict.OK, probes.getValue("ATRV").verdict)
        assertTrue(source.connection.value.selfTestAtMs > 0)
        assertTrue("012F опрашивается после проверки", "012F" in elm.pollCommands)
        assertEquals(50.2, t.fuelLevelPercent!!, 0.1)
    }

    @Test
    fun `повторная проверка по запросу во время поездки`() = runBlocking {
        val elm = FakeElm327(gasolineCar)
        val source = ObdTelemetrySource({ VehicleProfile() }) { elm }
        var n = 0
        source.samples().take(3).collect {
            if (n++ == 0) source.requestSelfTest()
        }
        assertEquals("проверка при подключении и по запросу", 2, elm.commands.count { it == "ATH1" })
    }
}
