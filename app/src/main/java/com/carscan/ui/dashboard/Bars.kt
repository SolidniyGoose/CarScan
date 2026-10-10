package com.carscan.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.ui.DashboardState
import com.carscan.ui.signedTemp
import com.carscan.data.ConnectionState
import com.carscan.data.DataSourceType
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Neon
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Неоновая линия во всю ширину с трапециевидной «вкладкой» по центру —
 * сверху под часы, снизу под индикатор ON (как на референсе).
 */
@Composable
private fun NeonTabFrame(top: Boolean, tabWidthFraction: Float, modifier: Modifier = Modifier) {
    val palette = CarScanTheme.palette
    Canvas(modifier) {
        val path = tabPath(top, tabWidthFraction)
        drawPath(path, palette.panelFill)
        val lineBrush = Brush.horizontalGradient(
            0f to Color.Transparent,
            0.25f to palette.accent.copy(alpha = 0.55f),
            0.5f to palette.accent,
            0.75f to palette.accent.copy(alpha = 0.55f),
            1f to Color.Transparent,
        )
        val outline = tabPath(top, tabWidthFraction, closed = false)
        drawPath(outline, lineBrush, style = Stroke(width = 6.dp.toPx()), alpha = 0.15f)
        drawPath(outline, lineBrush, style = Stroke(width = 1.5.dp.toPx()))
    }
}

private fun DrawScope.tabPath(top: Boolean, tabWidthFraction: Float, closed: Boolean = true): Path {
    val w = size.width
    val h = size.height
    val tabHalf = w * tabWidthFraction / 2f
    val slope = h * 0.8f
    val cx = w / 2f
    // Для верхней рамки линия идёт у верхнего края, вкладка опускается вниз; для нижней — зеркально
    val edge = if (top) 1.dp.toPx() else h - 1.dp.toPx()
    val tab = if (top) h - 2.dp.toPx() else 2.dp.toPx()
    return Path().apply {
        moveTo(0f, edge)
        lineTo(cx - tabHalf - slope, edge)
        lineTo(cx - tabHalf, tab)
        lineTo(cx + tabHalf, tab)
        lineTo(cx + tabHalf + slope, edge)
        lineTo(w, edge)
        if (closed) {
            lineTo(w, if (top) 0f else h)
            lineTo(0f, if (top) 0f else h)
            close()
        }
    }
}

@Composable
fun TopBar(state: DashboardState, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(modifier.fillMaxWidth().height(40.dp)) {
        NeonTabFrame(top = true, tabWidthFraction = 0.14f, modifier = Modifier.fillMaxSize())

        // Слева: источник данных
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = state.connection
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !state.streaming -> Neon.TextDim
                            c.state == ConnectionState.CONNECTED -> Neon.Green
                            c.state == ConnectionState.ERROR -> Neon.Red
                            else -> Neon.Amber
                        },
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = when {
                    !state.streaming -> "ПОТОК ВЫКЛЮЧЕН"
                    state.settings.dataSource == DataSourceType.SIMULATOR -> "СИМУЛЯТОР"
                    c.state == ConnectionState.CONNECTED -> "OBD2" + (c.protocol?.let { " · " + it.substringBefore(" (") } ?: "")
                    c.state == ConnectionState.ERROR -> "НЕТ СВЯЗИ С OBD2"
                    else -> "ПОДКЛЮЧЕНИЕ OBD2…"
                },
                color = Neon.TextMuted,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
        }

        Text(
            timeFormat.format(Date(now)),
            color = Neon.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )

        // Справа: улица (интернет) и салон
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val weather = state.weather.current
            val code = weather?.weatherCode ?: -1
            NeonIcon(if (code in 0..1) NeonIconKind.SUN else NeonIconKind.CLOUD, Neon.Info, size = 16.dp)
            Spacer(Modifier.width(4.dp))
            Text(signedTemp(state.outsideTempC), color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            weather?.place?.let {
                Spacer(Modifier.width(4.dp))
                Text(it, color = Neon.TextMuted, fontSize = 10.sp, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            NeonIcon(NeonIconKind.THERMO, Neon.TextMuted, size = 14.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                state.telemetry.cabinTemp?.let { "салон ${it.roundToInt()} °C" } ?: "салон —",
                color = Neon.TextMuted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun BottomBar(
    streaming: Boolean,
    settingsOpen: Boolean,
    updateAvailable: Boolean,
    onSettings: () -> Unit,
    onPower: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = CarScanTheme.palette
    Box(modifier.fillMaxWidth().height(52.dp)) {
        NeonTabFrame(top = false, tabWidthFraction = 0.16f, modifier = Modifier.fillMaxSize())

        Row(
            Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(start = 18.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RoundButton(NeonIconKind.DOTS, active = settingsOpen, accent = palette.accent, onClick = onSettings)
                if (updateAvailable) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Neon.Amber),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            RoundButton(NeonIconKind.POWER, active = streaming, accent = palette.active, onClick = onPower)
        }

        Text(
            if (streaming) "ON" else "OFF",
            color = if (streaming) palette.accent else Neon.TextDim,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp,
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
        )

        Text(
            "CARSCAN 3D",
            color = Neon.TextMuted,
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp, top = 8.dp),
        )
    }
}

@Composable
private fun RoundButton(icon: NeonIconKind, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .neonPanel(
                accent = accent,
                shape = CircleShape,
                fill = CarScanTheme.palette.tileFill,
                borderAlpha = if (active) 0.8f else 0.3f,
                glowAlpha = if (active) 0.45f else 0f,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NeonIcon(icon, if (active) accent else Neon.TextMuted, size = 18.dp)
    }
}
