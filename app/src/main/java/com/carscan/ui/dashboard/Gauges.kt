package com.carscan.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.data.TripStats
import com.carscan.data.VehicleTelemetry
import com.carscan.ui.NO_VALUE
import com.carscan.ui.fmt
import com.carscan.ui.formatDuration
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Neon
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Аналоговые часы в стиле автомобильных IWC: полированный стальной безель, тёмный циферблат
 * с гильошированным центром, накладные индексы и цифры 12/6, огранённые стальные стрелки.
 * Размер — по меньшей стороне доступного места, поэтому часы не вылезают за край экрана.
 */
@Composable
fun AnalogClock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000) // секундная стрелка прыгает ровно на границе секунды
        }
    }
    val accent = CarScanTheme.palette.accent
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f
        val c = center
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)

        // Тень и тонкое свечение цвета темы вокруг корпуса
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), c + Offset(0f, r * 0.04f), r * 1.12f), r * 1.12f, c + Offset(0f, r * 0.04f))
        drawCircle(accent.copy(alpha = 0.35f), r * 1.015f, c, style = Stroke(width = 1.dp.toPx()))

        // Полированный безель: «блики» стали по кругу
        drawCircle(
            Brush.sweepGradient(
                listOf(STEEL_LIGHT, STEEL_DARK, STEEL_MID, STEEL_SHADOW, STEEL_LIGHT, STEEL_DARK, STEEL_MID, STEEL_LIGHT),
                c,
            ),
            r,
            c,
        )
        // Внутренняя фаска безеля: сверху темнее, снизу светлее — выглядит утопленной
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFF2B2E33), Color(0xFFB9BEC4)), Offset(c.x, c.y - r), Offset(c.x, c.y + r)),
            r * 0.885f,
            c,
        )
        drawCircle(Color(0xFF111316), r * 0.86f, c)

        // Циферблат
        val dial = r * 0.85f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF41454B), Color(0xFF26292D), Color(0xFF1C1E21)), c, dial), dial, c)

        // Гильоше в центре: ромбовидная насечка
        val guilloche = r * 0.42f
        clipPath(Path().apply { addOval(Rect(c, guilloche)) }) {
            val step = r * 0.045f
            var d = -guilloche * 2
            while (d < guilloche * 2) {
                drawLine(Color.White.copy(alpha = 0.07f), Offset(c.x + d - guilloche, c.y - guilloche), Offset(c.x + d + guilloche, c.y + guilloche), 1.dp.toPx())
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(c.x + d + guilloche, c.y - guilloche), Offset(c.x + d - guilloche, c.y + guilloche), 1.dp.toPx())
                d += step
            }
        }
        drawCircle(Color.White.copy(alpha = 0.12f), guilloche, c, style = Stroke(width = 1.dp.toPx()))

        // Минутная дорожка по краю, красная отметка на 12
        for (i in 0 until 60) {
            val hourMark = i % 5 == 0
            drawLine(
                color = if (i == 0) Neon.Red else Color.White.copy(alpha = if (hourMark) 0.9f else 0.55f),
                start = polar(c, dial * (if (hourMark) 0.90f else 0.93f), i * 6f),
                end = polar(c, dial * 0.98f, i * 6f),
                strokeWidth = (if (hourMark) 1.6 else 0.9).dp.toPx(),
            )
        }

        // Накладные индексы: двойные на 3 и 9, цифры 12 и 6
        for (h in 1..11) {
            if (h == 6) continue
            rotate(h * 30f, c) {
                if (h == 3 || h == 9) {
                    appliedBaton(c, r, offset = -r * 0.035f)
                    appliedBaton(c, r, offset = r * 0.035f)
                } else {
                    appliedBaton(c, r, offset = 0f)
                }
            }
        }
        val numeral = TextStyle(color = Color(0xFFF1F3F5), fontSize = (r * 0.26f).toSp(), fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        appliedText(measurer, "12", numeral, Offset(c.x, c.y - r * 0.60f))
        appliedText(measurer, "6", numeral, Offset(c.x, c.y + r * 0.60f))

        // Подпись под 12
        val brand = measurer.measure("CARSCAN", TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.SemiBold, letterSpacing = (r * 0.012f).toSp()))
        drawText(brand, topLeft = Offset(c.x - brand.size.width / 2f, c.y - r * 0.34f))
        val sub = measurer.measure("AUTOMATIC", TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = (r * 0.05f).toSp(), letterSpacing = (r * 0.01f).toSp()))
        drawText(sub, topLeft = Offset(c.x - sub.size.width / 2f, c.y - r * 0.34f + brand.size.height))

        // Стрелки: огранённые стальные «мечи», секундная — тонкая красная с противовесом
        rotate((hours + minutes / 60f) * 30f, c) { swordHand(c, length = r * 0.50f, width = r * 0.06f) }
        rotate((minutes + seconds / 60f) * 6f, c) { swordHand(c, length = r * 0.76f, width = r * 0.045f) }
        rotate(seconds * 6f, c) {
            drawLine(Neon.Red, Offset(c.x, c.y + r * 0.2f), Offset(c.x, c.y - r * 0.80f), 1.4.dp.toPx(), StrokeCap.Round)
            drawCircle(Neon.Red, r * 0.035f, Offset(c.x, c.y + r * 0.16f))
        }
        drawCircle(Brush.radialGradient(listOf(STEEL_LIGHT, STEEL_SHADOW), c - Offset(r * 0.01f, r * 0.01f), r * 0.05f), r * 0.045f, c)
        drawCircle(Color(0xFF1A1C1F), r * 0.012f, c)

        // Блик стекла
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), c.y - dial, c.y),
            topLeft = Offset(c.x - dial * 0.75f, c.y - dial * 0.95f),
            size = Size(dial * 1.5f, dial * 0.9f),
        )
    }
}

private val STEEL_LIGHT = Color(0xFFF2F4F6)
private val STEEL_MID = Color(0xFFC9CDD2)
private val STEEL_DARK = Color(0xFF7D8288)
private val STEEL_SHADOW = Color(0xFF4A4E54)

/** Накладной полированный индекс, направленный вверх (поворачивается снаружи). */
private fun DrawScope.appliedBaton(c: Offset, r: Float, offset: Float) {
    val w = r * 0.045f
    val top = c.y - r * 0.74f
    val height = r * 0.16f
    val left = c.x + offset - w / 2
    drawRect(Color.Black.copy(alpha = 0.5f), Offset(left + w * 0.15f, top + w * 0.2f), Size(w, height))
    drawRect(Brush.horizontalGradient(listOf(STEEL_LIGHT, STEEL_MID, STEEL_DARK), left, left + w), Offset(left, top), Size(w, height))
    drawRect(Color.White.copy(alpha = 0.9f), Offset(left + w * 0.35f, top + height * 0.08f), Size(w * 0.3f, height * 0.84f))
}

/** Накладная цифра: тень, затем светлый металл. */
private fun DrawScope.appliedText(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset) {
    val shadow = measurer.measure(text, style.copy(color = Color.Black.copy(alpha = 0.6f)))
    val face = measurer.measure(text, style)
    val topLeft = Offset(center.x - face.size.width / 2f, center.y - face.size.height / 2f)
    drawText(shadow, topLeft = topLeft + Offset(1.5.dp.toPx(), 2.dp.toPx()))
    drawText(face, topLeft = topLeft)
}

/** Огранённая стрелка-«меч» вверх от центра: левая грань светлая, правая тёмная. */
private fun DrawScope.swordHand(c: Offset, length: Float, width: Float) {
    val tail = length * 0.16f
    val shoulder = c.y - length * 0.72f
    val tip = c.y - length
    val left = Path().apply {
        moveTo(c.x, c.y + tail); lineTo(c.x - width * 0.35f, c.y + tail)
        lineTo(c.x - width / 2, shoulder); lineTo(c.x, tip); close()
    }
    val right = Path().apply {
        moveTo(c.x, c.y + tail); lineTo(c.x + width * 0.35f, c.y + tail)
        lineTo(c.x + width / 2, shoulder); lineTo(c.x, tip); close()
    }
    translate(1.5.dp.toPx(), 2.5.dp.toPx()) {
        drawPath(left, Color.Black.copy(alpha = 0.45f))
        drawPath(right, Color.Black.copy(alpha = 0.45f))
    }
    drawPath(left, Brush.horizontalGradient(listOf(STEEL_MID, STEEL_LIGHT), c.x - width / 2, c.x))
    drawPath(right, Brush.horizontalGradient(listOf(STEEL_DARK, STEEL_SHADOW), c.x, c.x + width / 2))
    drawLine(Color(0xFF2A2D31), Offset(c.x, c.y), Offset(c.x, shoulder), 0.8.dp.toPx())
}

/** Точка на окружности; угол по часовой стрелке от 12 часов. */
private fun polar(center: Offset, r: Float, degrees: Float): Offset {
    val a = Math.toRadians((degrees - 90).toDouble())
    return Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
}

/**
 * Карточка скорости с бортовым компьютером: пробег, время поездки, остаток топлива в литрах
 * (по объёму бака выбранного автомобиля) и шкала заполнения бака.
 */
@Composable
fun SpeedCard(telemetry: VehicleTelemetry, trip: TripStats, fuelLiters: Double?, modifier: Modifier = Modifier) {
    val palette = CarScanTheme.palette
    val speed by animateFloatAsState((telemetry.speedKmh ?: 0).toFloat(), tween(300), label = "speed")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .neonPanel(accent = palette.active, fill = palette.panelFill, shape = shape, borderAlpha = 0.7f, glowAlpha = 0.4f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(
                NeonIconKind.ENGINE,
                tint = if (telemetry.checkEngineOn) Neon.Amber else Neon.TextDim.copy(alpha = 0.6f),
                size = 18.dp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (telemetry.speedKmh == null) NO_VALUE else speed.roundToInt().toString(),
                color = Neon.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            Text("км/ч", color = Neon.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonIcon(NeonIconKind.PIN, Neon.TextMuted, size = 13.dp)
            Spacer(Modifier.width(3.dp))
            Text(trip.distanceKm.fmt(1) + " км", color = Neon.Text, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            NeonIcon(NeonIconKind.CLOCK, Neon.TextMuted, size = 13.dp)
            Spacer(Modifier.width(3.dp))
            Text(formatDuration(trip.durationSec), color = Neon.Text, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            NeonIcon(NeonIconKind.FUEL, Neon.TextMuted, size = 13.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                fuelLiters?.let { it.fmt(1) + " л" } ?: NO_VALUE,
                color = Neon.Text,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        SegmentedBar(
            fraction = ((telemetry.fuelLevelPercent ?: 0.0) / 100.0).toFloat(),
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )
    }
}

/** Сегментированная шкала: красный в резерве, затем янтарный и цвет темы. */
@Composable
fun SegmentedBar(fraction: Float, modifier: Modifier = Modifier, segments: Int = 12) {
    val accent = CarScanTheme.palette.accent
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until segments) {
            val start = i.toFloat() / segments
            val filled = fraction > start
            val color = when {
                fraction < 0.1f -> Neon.Red
                fraction < 0.2f -> Neon.Amber
                else -> accent
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .neonPanel(
                        accent = color,
                        shape = RoundedCornerShape(2.dp),
                        fill = if (filled) color else Color.White.copy(alpha = 0.08f),
                        borderAlpha = 0f,
                        glowAlpha = if (filled) 0.5f else 0f,
                    ),
            )
        }
    }
}
