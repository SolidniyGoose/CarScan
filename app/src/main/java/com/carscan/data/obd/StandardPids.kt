package com.carscan.data.obd

import java.util.Locale

/** Числовой PID режима 01: название, единицы и формула SAE J1979 (байты после «41 PID»). */
class PidDef(
    val pid: Int,
    val name: String,
    val unit: String,
    val digits: Int,
    private val bytes: Int,
    private val formula: (IntArray) -> Double,
) {
    fun decode(d: IntArray): Double? = if (d.size >= bytes) formula(d) else null

    fun format(value: Double): String = String.format(Locale.US, "%.${digits}f", value) + unit
}

/**
 * Все числовые стандартные PID — для списка «Все датчики», как в Car Scanner.
 * Битовые поля (01, 03, 13, 1C, 41, 51) сюда не входят: они расшифрованы отдельно или не нужны водителю.
 */
object StandardPids {
    private fun a(d: IntArray) = d[0].toDouble()
    private fun ab(d: IntArray) = (d[0] * 256 + d[1]).toDouble()
    private fun percent(d: IntArray) = d[0] * 100.0 / 255.0
    private fun trim(d: IntArray) = (d[0] - 128) * 100.0 / 128.0
    private fun temp(d: IntArray) = d[0] - 40.0
    private fun lambda(d: IntArray) = ab(d) * 2.0 / 65536.0

    private fun o2Voltage(pid: Int): PidDef {
        val n = pid - 0x14
        return PidDef(pid, "Лямбда-зонд Б${n / 4 + 1} Д${n % 4 + 1}, напряжение", " V", 3, 1) { it[0] / 200.0 }
    }

    private fun wideLambda(pid: Int): PidDef {
        val n = pid - 0x24
        return PidDef(pid, "Лямбда-зонд (ШДК) Б${n / 4 + 1} Д${n % 4 + 1}, λ", "", 3, 2, ::lambda)
    }

    val all: List<PidDef> = listOf(
        PidDef(0x04, "Нагрузка двигателя", " %", 1, 1, ::percent),
        PidDef(0x05, "Темп. охлаждающей жидкости", " °C", 0, 1, ::temp),
        PidDef(0x06, "Кратк. коррекция топлива, банк 1", " %", 1, 1, ::trim),
        PidDef(0x07, "Долг. коррекция топлива, банк 1", " %", 1, 1, ::trim),
        PidDef(0x08, "Кратк. коррекция топлива, банк 2", " %", 1, 1, ::trim),
        PidDef(0x09, "Долг. коррекция топлива, банк 2", " %", 1, 1, ::trim),
        PidDef(0x0A, "Давление топлива", " кПа", 0, 1) { a(it) * 3 },
        PidDef(0x0B, "Давление во впуске (MAP)", " кПа", 0, 1, ::a),
        PidDef(0x0C, "Обороты двигателя", " об/мин", 0, 2) { ab(it) / 4 },
        PidDef(0x0D, "Скорость", " км/ч", 0, 1, ::a),
        PidDef(0x0E, "Угол опережения зажигания", "°", 1, 1) { a(it) / 2 - 64 },
        PidDef(0x0F, "Темп. воздуха на впуске", " °C", 0, 1, ::temp),
        PidDef(0x10, "Массовый расход воздуха (MAF)", " г/с", 2, 2) { ab(it) / 100 },
        PidDef(0x11, "Положение дроссельной заслонки", " %", 1, 1, ::percent),
    ) + (0x14..0x1B).map(::o2Voltage) + listOf(
        PidDef(0x1F, "Время с запуска двигателя", " с", 0, 2, ::ab),
        PidDef(0x21, "Пробег с горящим Check Engine", " км", 0, 2, ::ab),
        PidDef(0x22, "Давление в рампе (отн. впуска)", " кПа", 1, 2) { ab(it) * 0.079 },
        PidDef(0x23, "Давление в топливной рампе", " кПа", 0, 2) { ab(it) * 10 },
    ) + (0x24..0x2B).map(::wideLambda) + listOf(
        PidDef(0x2C, "Заданная рециркуляция ОГ (EGR)", " %", 1, 1, ::percent),
        PidDef(0x2D, "Ошибка рециркуляции ОГ (EGR)", " %", 1, 1, ::trim),
        PidDef(0x2E, "Заданная продувка адсорбера", " %", 1, 1, ::percent),
        PidDef(0x2F, "Уровень топлива", " %", 1, 1, ::percent),
        PidDef(0x30, "Прогревов после сброса ошибок", "", 0, 1, ::a),
        PidDef(0x31, "Пробег после сброса ошибок", " км", 0, 2, ::ab),
        PidDef(0x32, "Давление паров топлива (EVAP)", " Па", 1, 2) { (ab(it).toInt().toShort()) / 4.0 },
        PidDef(0x33, "Атмосферное давление", " кПа", 0, 1, ::a),
        PidDef(0x3C, "Темп. катализатора Б1 Д1", " °C", 0, 2) { ab(it) / 10 - 40 },
        PidDef(0x3D, "Темп. катализатора Б2 Д1", " °C", 0, 2) { ab(it) / 10 - 40 },
        PidDef(0x3E, "Темп. катализатора Б1 Д2", " °C", 0, 2) { ab(it) / 10 - 40 },
        PidDef(0x3F, "Темп. катализатора Б2 Д2", " °C", 0, 2) { ab(it) / 10 - 40 },
        PidDef(0x42, "Напряжение блока управления", " V", 2, 2) { ab(it) / 1000 },
        PidDef(0x43, "Абсолютная нагрузка", " %", 1, 2) { ab(it) * 100 / 255 },
        PidDef(0x44, "Заданный состав смеси, λ", "", 3, 2, ::lambda),
        PidDef(0x45, "Относит. положение дросселя", " %", 1, 1, ::percent),
        PidDef(0x46, "Темп. наружного воздуха", " °C", 0, 1, ::temp),
        PidDef(0x47, "Положение дросселя B", " %", 1, 1, ::percent),
        PidDef(0x48, "Положение дросселя C", " %", 1, 1, ::percent),
        PidDef(0x49, "Положение педали газа D", " %", 1, 1, ::percent),
        PidDef(0x4A, "Положение педали газа E", " %", 1, 1, ::percent),
        PidDef(0x4B, "Положение педали газа F", " %", 1, 1, ::percent),
        PidDef(0x4C, "Заданное положение дросселя", " %", 1, 1, ::percent),
        PidDef(0x4D, "Время с горящим Check Engine", " мин", 0, 2, ::ab),
        PidDef(0x4E, "Время после сброса ошибок", " мин", 0, 2, ::ab),
        PidDef(0x52, "Доля этанола в топливе", " %", 1, 1, ::percent),
        PidDef(0x59, "Абс. давление в топливной рампе", " кПа", 0, 2) { ab(it) * 10 },
        PidDef(0x5A, "Относит. положение педали газа", " %", 1, 1, ::percent),
        PidDef(0x5B, "Заряд гибридной батареи", " %", 1, 1, ::percent),
        PidDef(0x5C, "Темп. масла двигателя", " °C", 0, 1, ::temp),
        PidDef(0x5D, "Угол начала впрыска", "°", 1, 2) { ab(it) / 128 - 210 },
        PidDef(0x5E, "Расход топлива", " л/ч", 2, 2) { ab(it) / 20 },
        PidDef(0x61, "Требуемый крутящий момент", " %", 0, 1) { a(it) - 125 },
        PidDef(0x62, "Фактический крутящий момент", " %", 0, 1) { a(it) - 125 },
        PidDef(0x63, "Номинальный крутящий момент", " Н·м", 0, 2, ::ab),
        PidDef(0xA6, "Одометр", " км", 1, 4) {
            ((it[0].toLong() shl 24) + (it[1] shl 16) + (it[2] shl 8) + it[3]) / 10.0
        },
    )

    val byPid: Map<Int, PidDef> = all.associateBy { it.pid }
}
