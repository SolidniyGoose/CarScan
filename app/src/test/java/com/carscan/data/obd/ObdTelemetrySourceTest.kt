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
import kotlinx.coroutines.withTimeout
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
 * Запрос режима 01 с числом ответов («010C2») отвечает как «010C»; [countSuffix] = false — клон, который
 * число ответов не понимает и отвечает «?».
 */
private class FakeElm327(private val car: Map<String, String>, private val countSuffix: Boolean = true) : ObdTransport {
    // Пишет поток опроса, читает тест — список должен быть потокобезопасным
    val commands: MutableList<String> = java.util.concurrent.CopyOnWriteArrayList()
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
            val withCount = MODE01_WITH_COUNT.matches(cmd)
            val key = if (withCount) cmd.dropLast(1) else cmd
            val reply = when {
                withCount && !countSuffix -> "?"
                else -> car[key] ?: if (cmd.startsWith("AT")) "OK" else "NO DATA"
            }
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

    private companion object {
        val MODE01_WITH_COUNT = Regex("^01[0-9A-F]{3}$")
    }
}

/** Память протокола для тестов: что было сохранено между подключениями. */
private class FakeProtocolMemory(var stored: Int?) : ProtocolMemory {
    val saved = mutableListOf<Int>()
    override fun load(): Int? = stored
    override fun save(protocol: Int) {
        saved += protocol
        stored = protocol
    }
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
        // «1» в конце — число ожидаемых ответов: 0C заявил один блок
        val rpm = source.connection.value.log.last { it.command == "010C1" }
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
        var requested = false
        // Опрос идёт в своём потоке и может обгонять сбор на несколько снимков — ждём, а не считаем снимки
        withTimeout(10_000) {
            source.samples().first {
                if (!requested) {
                    requested = true
                    source.requestSelfTest()
                }
                elm.commands.count { it == "ATH1" } >= 2
            }
        }
        assertEquals("проверка при подключении и по запросу", 2, elm.commands.count { it == "ATH1" })
    }

    @Test
    fun `маски поддерживаемых PID читаются до C0 и пробег попадает в проверку`() = runBlocking {
        val car = gasolineCar + mapOf(
            "0140" to "41 40 " + mask(0x40, 0x46, 0x60),
            "0160" to "41 60 " + mask(0x60, 0x80),
            "0180" to "41 80 " + mask(0x80, 0xA0),
            "01A0" to "41 A0 " + mask(0xA0, 0xA6),
            "01A6" to "41 A6 00 1E 84 85", // 2 000 005 → 200 000.5 км
        )
        val source = ObdTelemetrySource({ VehicleProfile() }) { FakeElm327(car) }
        withTimeout(10_000) { source.samples().first { source.connection.value.selfTestAtMs > 0 } }
        val info = source.connection.value
        assertTrue(0xA6 in info.supportedPids)
        val odometer = info.selfTest.single { it.command == "01A6" }
        assertEquals(ProbeVerdict.OK, odometer.verdict)
        assertEquals("200000.5 км", odometer.decoded)
    }

    @Test
    fun `глубокое сканирование сохраняет ответы службы 21 и возвращает заголовок`() = runBlocking {
        val car = gasolineCar + mapOf(
            "2101" to "61 01 AA BB CC",
            "2103" to "013\n0: 61 03 01 02 03 04\n1: 05 06 07 08 09 0A 0B", // многокадровый ответ
            "2102" to "7F 21 12", // нет такого идентификатора
        )
        val elm = FakeElm327(car)
        val source = ObdTelemetrySource({ VehicleProfile() }) { elm }
        source.requestDeepScan()
        withTimeout(10_000) { source.samples().first { source.connection.value.deepScan != null } }
        val scan = source.connection.value.deepScan!!
        // Эмулятор отвечает одинаково на любой заголовок — поэтому по записи от каждого блока
        assertEquals(
            listOf("7E0 2101 AA BB CC", "7E0 2103 01 02 03 04 05 06 07 08 09 0A 0B", "7E1 2101 AA BB CC", "7E1 2103 01 02 03 04 05 06 07 08 09 0A 0B"),
            scan.entries.map { "${it.header} ${it.command} ${it.data}" },
        )
        assertEquals(2, scan.rejected)
        val i = elm.commands.indexOf("21FF")
        assertTrue("после сканирования снова функциональный адрес", elm.commands.subList(i, elm.commands.size).contains("ATSH7DF"))
    }

    /**
     * Mitsubishi Outlander XL 2.0 из отчёта 11.10.2026: отвечают двигатель (7E8) и вариатор (7E9).
     * Маски и ответы — те же байты, что в журнале с машины.
     */
    private val outlander = mapOf(
        "ATZ" to "\nELM327 v1.5",
        "ATI" to "ELM327 v1.5",
        "ATDPN" to "A6",
        "ATRV" to "14.3V",
        "0100" to "SEARCHING...\n4100BE3FB813\n410088180013",
        "0120" to "4120" + mask(0x20, 0x21, 0x2C, 0x2E, 0x30, 0x31, 0x33, 0x40).replace(" ", "") + "\n412000000000",
        "0140" to "4140" + mask(0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x49, 0x4A, 0x4C, 0x5A).replace(" ", ""),
        "0101" to "410100076120\n410100040000",
        "0103" to "41030200",
        "0104" to "41043D",
        "0105" to "41057F\n41057F",
        "0106" to "410684",
        "0107" to "41077F",
        "010B" to "410B21",
        "010C" to "410C0AEC\n410C0AEC",
        "010D" to "410D00\n410D00",
        "010E" to "410E8C",
        "010F" to "410F48",
        "0110" to "411000F0",
        "0111" to "411126",
        "0133" to "413362",
        "0142" to "4142375D",
        "0146" to "414648",
    )

    @Test
    fun `в запросе число ответов - по числу блоков, заявивших PID`() = runBlocking {
        val elm = FakeElm327(outlander)
        val t = ObdTelemetrySource({ VehicleProfile() }) { elm }.samples().first()
        assertEquals(699, t.rpm)
        // Обороты, скорость, ОЖ и статус отдают оба блока, нагрузку и MAF — только двигатель
        for (cmd in listOf("010C2", "010D2", "01052", "01012", "01041", "01101")) {
            assertTrue("ожидался запрос $cmd", cmd in elm.commands)
        }
        assertFalse("маски поддерживаемых PID — без числа ответов", "01001" in elm.commands || "01002" in elm.commands)
    }

    @Test
    fun `адаптер не понимает число ответов - опрос продолжается без него`() = runBlocking {
        val elm = FakeElm327(gasolineCar, countSuffix = false)
        val source = ObdTelemetrySource({ VehicleProfile() }) { elm }
        val t = source.samples().take(2).toList().last()
        assertEquals(1726, t.rpm)
        assertEquals("число ответов пробуем один раз", 1, elm.commands.count { it.length == 5 && it.startsWith("01") })
        assertTrue("010C" in elm.commands)
        assertTrue(source.connection.value.log.any { it.decoded?.contains("не понимает число ответов") == true })
    }

    @Test
    fun `все заявленные PID читаются по кругу для списка «Все датчики»`() = runBlocking {
        val t = ObdTelemetrySource({ VehicleProfile() }) { FakeElm327(outlander) }.samples().take(25).toList().last()
        assertEquals(98.0, t.obdValues.getValue(0x33), 1e-9)      // атм. давление
        assertEquals(14.173, t.obdValues.getValue(0x42), 1e-9)    // напряжение ЭБУ
        assertEquals(6.0, t.obdValues.getValue(0x0E), 1e-9)       // угол опережения
        assertEquals(14.9, t.obdValues.getValue(0x11), 0.01)      // дроссель
        assertEquals(699.0, t.obdValues.getValue(0x0C), 1e-9)     // и то, что опрашивается по расписанию
    }

    @Test
    fun `протокол запоминается и в следующий раз пробуется без поиска`() = runBlocking {
        val memory = FakeProtocolMemory(null)
        val firstRun = FakeElm327(outlander)
        ObdTelemetrySource({ VehicleProfile() }, memory) { firstRun }.samples().first()
        assertTrue("ATSP0" in firstRun.commands)
        assertEquals(listOf(6), memory.saved)

        val secondRun = FakeElm327(outlander)
        ObdTelemetrySource({ VehicleProfile() }, memory) { secondRun }.samples().first()
        assertTrue("ATSPA6" in secondRun.commands)
        assertFalse("ATSP0" in secondRun.commands)
        assertEquals("тот же протокол повторно не сохраняем", listOf(6), memory.saved)
    }

    @Test
    fun `проверка датчиков идёт после первых показаний`() = runBlocking {
        val elm = FakeElm327(gasolineCar)
        withTimeout(10_000) { ObdTelemetrySource({ VehicleProfile() }) { elm }.samples().take(5).toList() }
        val firstPoll = elm.commands.indexOf("010C1")
        val selfTest = elm.commands.indexOf("ATH1")
        assertTrue("опрос $firstPoll раньше проверки $selfTest", firstPoll in 0 until selfTest)
    }
}
