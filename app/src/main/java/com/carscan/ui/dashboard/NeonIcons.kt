package com.carscan.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NeonIconKind {
    CAR, GAUGE, PULSE, LEAF, DOTS, POWER, PIN, CLOCK, CLOSE, CHEVRON_DOWN, CHEVRON_RIGHT,
    FOCUS, XRAY, THERMO, BATTERY, FUEL, ENGINE, CLOUD, SUN, REFRESH, CHEVRON_LEFT, MINUS, PLUS, DOWNLOAD, SEND,
}

/**
 * Линейные иконки, нарисованные на Canvas в сетке 24×24.
 * Так не нужна тяжёлая библиотека material-icons-extended, а стиль совпадает с неоновым UI.
 */
@Composable
fun NeonIcon(kind: NeonIconKind, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun poly(vararg pts: Float, close: Boolean = false) {
            val path = Path().apply {
                moveTo(pts[0] * u, pts[1] * u)
                for (i in 2 until pts.size step 2) lineTo(pts[i] * u, pts[i + 1] * u)
                if (close) close()
            }
            drawPath(path, tint, style = stroke)
        }
        fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false) =
            if (fill) drawCircle(tint, r * u, p(cx, cy)) else drawCircle(tint, r * u, p(cx, cy), style = stroke)

        when (kind) {
            NeonIconKind.CAR -> {
                poly(2.5f, 15.5f, 2.5f, 12.5f, 5f, 11.5f, 7.5f, 7.5f, 15.5f, 7.5f, 19f, 11.5f, 21.5f, 12.5f, 21.5f, 15.5f)
                line(9.5f, 15.5f, 14.5f, 15.5f)
                circle(7f, 16f, 2.2f)
                circle(17f, 16f, 2.2f)
            }
            NeonIconKind.GAUGE -> {
                drawArc(tint, 150f, 240f, false, p(3f, 4f), Size(18f * u, 18f * u), style = stroke)
                line(12f, 13f, 16.5f, 8.5f)
                circle(12f, 13f, 1.4f, fill = true)
            }
            NeonIconKind.PULSE -> poly(2f, 12f, 7f, 12f, 9f, 7f, 12f, 17f, 15f, 9.5f, 17f, 12f, 22f, 12f)
            NeonIconKind.LEAF -> {
                val leaf = Path().apply {
                    moveTo(5f * u, 19f * u)
                    cubicTo(5f * u, 9f * u, 11f * u, 5f * u, 20f * u, 4f * u)
                    cubicTo(20f * u, 13f * u, 15f * u, 19f * u, 5f * u, 19f * u)
                    close()
                }
                drawPath(leaf, tint, style = stroke)
                line(5f, 19f, 14f, 10f)
            }
            NeonIconKind.DOTS -> {
                circle(6f, 12f, 1.7f, fill = true)
                circle(12f, 12f, 1.7f, fill = true)
                circle(18f, 12f, 1.7f, fill = true)
            }
            NeonIconKind.POWER -> {
                drawArc(tint, 300f, 300f, false, p(4.5f, 5f), Size(15f * u, 15f * u), style = stroke)
                line(12f, 3f, 12f, 11.5f)
            }
            NeonIconKind.PIN -> {
                circle(12f, 9.5f, 4.5f)
                poly(8.2f, 12.2f, 12f, 20.5f, 15.8f, 12.2f)
            }
            NeonIconKind.CLOCK -> {
                circle(12f, 12f, 8.5f)
                poly(12f, 7.5f, 12f, 12f, 15f, 13.8f)
            }
            NeonIconKind.CLOSE -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            NeonIconKind.CHEVRON_DOWN -> poly(6f, 9f, 12f, 15f, 18f, 9f)
            NeonIconKind.CHEVRON_RIGHT -> poly(9f, 6f, 15f, 12f, 9f, 18f)
            NeonIconKind.CHEVRON_LEFT -> poly(15f, 6f, 9f, 12f, 15f, 18f)
            NeonIconKind.MINUS -> line(6f, 12f, 18f, 12f)
            NeonIconKind.DOWNLOAD -> {
                line(12f, 4f, 12f, 15f)
                poly(7f, 10.5f, 12f, 15.5f, 17f, 10.5f)
                line(5f, 20f, 19f, 20f)
            }
            NeonIconKind.SEND -> {
                // Бумажный самолётик
                poly(3f, 11f, 21f, 3f, 14f, 21f, 11f, 13f, 3f, 11f)
                line(11f, 13f, 21f, 3f)
            }
            NeonIconKind.PLUS -> {
                line(6f, 12f, 18f, 12f)
                line(12f, 6f, 12f, 18f)
            }
            NeonIconKind.FOCUS -> {
                circle(12f, 12f, 6f)
                line(12f, 2f, 12f, 6f); line(12f, 18f, 12f, 22f)
                line(2f, 12f, 6f, 12f); line(18f, 12f, 22f, 12f)
                circle(12f, 12f, 1.3f, fill = true)
            }
            NeonIconKind.XRAY -> {
                val eye = Path().apply {
                    moveTo(2f * u, 12f * u)
                    quadraticTo(12f * u, 2.5f * u, 22f * u, 12f * u)
                    quadraticTo(12f * u, 21.5f * u, 2f * u, 12f * u)
                    close()
                }
                drawPath(eye, tint, style = stroke)
                circle(12f, 12f, 3f)
            }
            NeonIconKind.THERMO -> {
                drawRoundRect(tint, p(9.5f, 2.5f), Size(5f * u, 13f * u), CornerRadius(2.5f * u), style = stroke)
                circle(12f, 18f, 3.5f, fill = true)
                line(12f, 7f, 12f, 16f)
            }
            NeonIconKind.BATTERY -> {
                drawRoundRect(tint, p(3f, 7f), Size(18f * u, 12f * u), CornerRadius(2f * u), style = stroke)
                line(6.5f, 4.5f, 9.5f, 4.5f); line(14.5f, 4.5f, 17.5f, 4.5f)
                line(6f, 13f, 9f, 13f)
                line(15f, 13f, 18f, 13f); line(16.5f, 11.5f, 16.5f, 14.5f)
            }
            NeonIconKind.FUEL -> {
                drawRoundRect(tint, p(4f, 3.5f), Size(10f * u, 17f * u), CornerRadius(1.5f * u), style = stroke)
                drawRect(tint, p(6.5f, 6f), Size(5f * u, 4f * u), style = stroke)
                poly(14f, 9f, 17f, 9f, 19f, 11f, 19f, 17f, 17.5f, 18.5f, 16f, 17f, 16f, 13f, 14f, 13f)
            }
            NeonIconKind.ENGINE -> poly(
                3f, 10f, 6f, 10f, 6f, 7.5f, 14f, 7.5f, 14f, 10f, 17f, 10f, 17f, 12f, 21f, 12f,
                21f, 16.5f, 17f, 16.5f, 17f, 18.5f, 7f, 18.5f, 3f, 15f, close = true,
            )
            NeonIconKind.CLOUD -> {
                val cloud = Path().apply {
                    moveTo(7f * u, 18f * u)
                    lineTo(17.5f * u, 18f * u)
                    cubicTo(21f * u, 18f * u, 21.5f * u, 12.5f * u, 17.5f * u, 12.2f * u)
                    cubicTo(17f * u, 8f * u, 11f * u, 6.5f * u, 9.2f * u, 10.5f * u)
                    cubicTo(5f * u, 10f * u, 3.5f * u, 13f * u, 4.2f * u, 15.3f * u)
                    cubicTo(4.8f * u, 17.2f * u, 6f * u, 18f * u, 7f * u, 18f * u)
                    close()
                }
                drawPath(cloud, tint, style = stroke)
            }
            NeonIconKind.SUN -> {
                circle(12f, 12f, 4f)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = kotlin.math.cos(a).toFloat()
                    val s = kotlin.math.sin(a).toFloat()
                    line(12f + c * 6.5f, 12f + s * 6.5f, 12f + c * 9f, 12f + s * 9f)
                }
            }
            NeonIconKind.REFRESH -> {
                drawArc(tint, 40f, 280f, false, p(4.5f, 4.5f), Size(15f * u, 15f * u), style = stroke)
                poly(19.5f, 4.5f, 19.5f, 9f, 15f, 9f)
            }
        }
    }
}
