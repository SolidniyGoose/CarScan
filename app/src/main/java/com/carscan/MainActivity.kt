package com.carscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import java.util.Calendar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.carscan.ui.DashboardState
import com.carscan.ui.MainViewModel
import com.carscan.ui.ActivePanel
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                CarScanDashboard(activity = this)
            }
        }
    }
}

@Composable
fun CarScanDashboard(activity: ComponentActivity, viewModel: MainViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF262C36),
                        androidx.compose.ui.graphics.Color(0xFF13171D)
                    ),
                    center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    radius = 2000f
                )
            )
    ) {
        // Слой 1: 3D Сцена (WebView + model-viewer)
        WebViewLayer(activity)

        // Слой 2: UI элементы с респонсивным дизайном (weight)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ЛЕВАЯ ЧАСТЬ (30% ширины экрана)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.3f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnalogClockWidget()
                Spacer(modifier = Modifier.weight(1f))
                ActionButtonsGrid(onPanelSelected = { viewModel.selectPanel(it) })
            }
            
            // ЦЕНТР: пустое пространство под машину (40% ширины экрана)
            Spacer(modifier = Modifier.weight(0.4f))

            // ПРАВАЯ ЧАСТЬ (30% ширины экрана)
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.3f),
                verticalArrangement = Arrangement.Center
            ) {
                DynamicRightPanel(state)
            }
        }
        
        // НИЖНЯЯ ЦЕНТРАЛЬНАЯ КНОПКА ON
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 12.dp), 
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "ON", 
                color = Color(0xFF00E5FF), 
                fontSize = 24.sp, 
                fontWeight = FontWeight.Light,
                letterSpacing = 4.sp
            )
        }
    }
}

@Composable
fun WebViewLayer(activity: ComponentActivity) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.domStorageEnabled = true
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE // Отключаем кэш для разработки
                
                val assetLoader = androidx.webkit.WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", androidx.webkit.WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                    
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView,
                        request: android.webkit.WebResourceRequest
                    ): android.webkit.WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }
                
                webChromeClient = android.webkit.WebChromeClient()
                
                // Загружаем наш viewer.html локально, он подтянет car.glb и CSS
                loadUrl("https://appassets.androidplatform.net/assets/viewer.html")
            }
        }
    )
}

@Composable
fun AnalogClockWidget() {
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    
    val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.width / 2
            
            // Циферблат
            drawCircle(
                color = Color(0xFF15181E),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0xFF333333),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Метки
            for (i in 0..59) {
                val angle = Math.toRadians((i * 6 - 90).toDouble())
                val isHour = i % 5 == 0
                val innerRadius = if (isHour) radius * 0.8f else radius * 0.9f
                val outerRadius = radius * 0.95f
                val strokeWidth = if (isHour) 3.dp.toPx() else 1.dp.toPx()
                
                val startX = center.x + (innerRadius * cos(angle)).toFloat()
                val startY = center.y + (innerRadius * sin(angle)).toFloat()
                val endX = center.x + (outerRadius * cos(angle)).toFloat()
                val endY = center.y + (outerRadius * sin(angle)).toFloat()
                
                drawLine(color = Color.White, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = strokeWidth)
            }
            
            // Часовая стрелка
            val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30 - 90).toDouble())
            drawLine(
                color = Color.White,
                start = center,
                end = Offset(center.x + (radius * 0.5f * cos(hourAngle)).toFloat(), center.y + (radius * 0.5f * sin(hourAngle)).toFloat()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Минутная стрелка
            val minAngle = Math.toRadians(((minutes + seconds / 60f) * 6 - 90).toDouble())
            drawLine(
                color = Color.White,
                start = center,
                end = Offset(center.x + (radius * 0.7f * cos(minAngle)).toFloat(), center.y + (radius * 0.7f * sin(minAngle)).toFloat()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Секундная стрелка
            val secAngle = Math.toRadians((seconds * 6 - 90).toDouble())
            drawLine(
                color = Color(0xFFFF3333),
                start = center,
                end = Offset(center.x + (radius * 0.8f * cos(secAngle)).toFloat(), center.y + (radius * 0.8f * sin(secAngle)).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            
            // Центр
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = center)
            drawCircle(color = Color(0xFFFF3333), radius = 3.dp.toPx(), center = center)
        }
    }
}

@Composable
fun ActionButtonsGrid(onPanelSelected: (ActivePanel) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ActionButton(Modifier.weight(1f), "Главная", Color(0xFF00E5FF)) { onPanelSelected(ActivePanel.MAIN) }
            Spacer(Modifier.width(8.dp))
            ActionButton(Modifier.weight(1f), "Датчики", Color(0xFF00FF88)) { onPanelSelected(ActivePanel.SENSORS_ALL) }
            Spacer(Modifier.width(8.dp))
            ActionButton(Modifier.weight(1f), "Мониторинг", Color(0xFF00E5FF)) { onPanelSelected(ActivePanel.DIAGNOSTICS_ECU) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ActionButton(Modifier.weight(1f), "Выбросы", Color(0xFF00E5FF)) { onPanelSelected(ActivePanel.EMISSIONS) }
            Spacer(Modifier.width(8.dp))
            ActionButton(Modifier.weight(1f), "Профиль", Color(0xFF555555)) { }
            Spacer(Modifier.width(8.dp))
            ActionButton(Modifier.weight(1f), "Настройки", Color(0xFF555555)) { }
        }
    }
}

@Composable
fun ActionButton(modifier: Modifier = Modifier, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(1f) // Квадратная кнопка
            .background(Color(0xFF2A2E35), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF3A3E45), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(20.dp).border(2.dp, tint, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, color = Color.LightGray, fontSize = 10.sp)
        }
    }
}

@Composable
fun DynamicRightPanel(state: DashboardState) {
    when (state.selectedPanel) {
        ActivePanel.MAIN -> MainInfoPanel(state)
        ActivePanel.SENSORS_ALL -> AllSensorsPanel(state)
        ActivePanel.DIAGNOSTICS_ECU -> EcuMonitoringPanel(state)
        ActivePanel.EMISSIONS -> EmissionsPanel(state)
    }
}

@Composable
fun MainInfoPanel(state: DashboardState) {
    BasePanelLayout(title = "ГЛАВНАЯ ПАНЕЛЬ", subtitle = "Информация о ТС") {
        InfoGridRow("Температура ОЖ", "${state.coolantTemp} °C")
        InfoGridRow("Нагрузка двигателя", "${state.engineLoad} %")
        InfoGridRow("Передача (AT)", state.currentGear)
        InfoGridRow("Температура ATF", "${state.atfTemp} °C")
        InfoGridRow("Напряжение сети", String.format("%.2f V", state.voltage).replace(",", "."))
        InfoGridRow("Топливо в баке", String.format("%.1f л", state.fuelLevel).replace(",", "."))
        InfoGridRow("Средний расход", String.format("%.1f л/100км", state.avgFuelConsumption).replace(",", "."))
    }
}

@Composable
fun AllSensorsPanel(state: DashboardState) {
    BasePanelLayout(title = "ВСЕ ДАТЧИКИ", subtitle = "Текущие параметры") {
        InfoGridRow("Напряжение (ELM327)", String.format("%.2f V", state.voltage).replace(",", "."))
        InfoGridRow("Температура ОЖ", "${state.coolantTemp} °C")
        InfoGridRow("Нагрузка", "${state.engineLoad} %")
        InfoGridRow("Кратко. коррекция", "${state.shortTermFuelTrim} %")
        InfoGridRow("Долг. коррекция", "${state.longTermFuelTrim} %")
        InfoGridRow("Давл. в коллекторе", "${state.intakePressure} kPa")
        InfoGridRow("Топливная система", "Closed Loop")
    }
}

@Composable
fun EcuMonitoringPanel(state: DashboardState) {
    BasePanelLayout(title = "МОНИТОРИНГ ЭБУ", subtitle = "Пропуски зажигания (MID\$A1)") {
        InfoGridRow("Цилиндр 1", "${state.misfireCylinder1} (Мин:0 Макс:65535)", valueColor = Color(0xFF00FF88))
        InfoGridRow("Цилиндр 2", "${state.misfireCylinder2} (Мин:0 Макс:65535)", valueColor = Color(0xFF00FF88))
        InfoGridRow("Цилиндр 3", "${state.misfireCylinder3} (Мин:0 Макс:65535)", valueColor = Color(0xFF00FF88))
        InfoGridRow("Цилиндр 4", "${state.misfireCylinder4} (Мин:0 Макс:65535)", valueColor = Color(0xFF00FF88))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {}, 
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2E35)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ОБНОВИТЬ", color = Color.White)
        }
    }
}

@Composable
fun EmissionsPanel(state: DashboardState) {
    BasePanelLayout(title = "ТЕСТЫ НА ВЫБРОСЫ", subtitle = "С момента сброса DTC") {
        InfoGridRow("Пропуски зажигания", if(state.testMisfire) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testMisfire) Color(0xFF00FF88) else Color.Red)
        InfoGridRow("Топливная система", if(state.testFuelSystem) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testFuelSystem) Color(0xFF00FF88) else Color.Red)
        InfoGridRow("Components", if(state.testComponents) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testComponents) Color(0xFF00FF88) else Color.Red)
        InfoGridRow("Катализатор", if(state.testCatalyst) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testCatalyst) Color(0xFF00FF88) else Color.Red)
        InfoGridRow("Датчик кислорода", if(state.testOxygenSensor) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testOxygenSensor) Color(0xFF00FF88) else Color.Red)
        InfoGridRow("Система EGR", if(state.testEgr) "ПРОЙДЕН" else "ФЕЙЛ", if(state.testEgr) Color(0xFF00FF88) else Color.Red)
    }
}

@Composable
fun BasePanelLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC15181E), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(bottom = 8.dp))
        
        content()
    }
}

@Composable
fun InfoGridRow(param: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp), 
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = param, color = Color.Gray, fontSize = 14.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}