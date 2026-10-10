package com.carscan.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.ui.ActivePanel
import com.carscan.ui.DashboardActions
import com.carscan.ui.DashboardState
import com.carscan.ui.MainViewModel
import com.carscan.ui.PanelContent
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Neon
import kotlin.math.min

/**
 * Макет рассчитан на «эталонный» экран 900×440 dp и равномерно масштабируется:
 * одинаково выглядит на телефоне в альбомной ориентации и на магнитоле 1024×600 / 1280×720.
 */
private val REFERENCE_WIDTH = 900.dp
private val REFERENCE_HEIGHT = 440.dp

@Composable
fun CarScanDashboard(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val sceneJson = remember(state) { state.toSceneJson() }
    var safeArea by remember { mutableStateOf<SafeArea?>(null) }

    Box(Modifier.fillMaxSize().background(CarScanTheme.palette.background)) {
        // Слой 1: 3D-сцена на весь экран
        CarSceneView(
            sceneJson = sceneJson,
            safeArea = safeArea,
            commands = viewModel.sceneCommands,
            onNodeTapped = viewModel::onNodeTapped,
            modifier = Modifier.fillMaxSize(),
        )
        // Слой 2: панели поверх сцены
        DashboardOverlay(state, viewModel, onSafeAreaChanged = { safeArea = it })
    }
}

/** Панели приборной доски без 3D-сцены. Не зависит от ViewModel, поэтому подходит для @Preview и тестов. */
@Composable
fun DashboardOverlay(
    state: DashboardState,
    actions: DashboardActions,
    onSafeAreaChanged: (SafeArea) -> Unit = {},
) {
    ScaledLayout(REFERENCE_WIDTH, REFERENCE_HEIGHT) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            TopBar(state)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                LeftColumn(state, actions, Modifier.weight(0.25f).fillMaxHeight())
                // Прозрачный центр: его границы передаются в viewer.html, чтобы машина вписалась между панелями
                Spacer(
                    Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .onGloballyPositioned { coords ->
                            val root = coords.findRootCoordinates().size
                            if (root.width == 0 || root.height == 0) return@onGloballyPositioned
                            val b = coords.boundsInRoot()
                            onSafeAreaChanged(
                                SafeArea(
                                    left = b.left / root.width,
                                    top = b.top / root.height,
                                    right = (root.width - b.right) / root.width,
                                    bottom = (root.height - b.bottom) / root.height,
                                ),
                            )
                        },
                )
                InfoPanel(
                    spec = PanelContent.build(state),
                    xrayEnabled = state.xrayMode,
                    onAction = actions::onPanelAction,
                    onChoice = actions::onChoice,
                    onStep = actions::onStep,
                    onClose = actions::closeNodePanel,
                    modifier = Modifier.weight(0.29f).fillMaxHeight(),
                )
            }
            BottomBar(
                streaming = state.streaming,
                settingsOpen = state.selectedPanel == ActivePanel.SETTINGS,
                updateAvailable = state.update.available,
                onSettings = {
                    actions.selectPanel(
                        if (state.selectedPanel == ActivePanel.SETTINGS) ActivePanel.MAIN else ActivePanel.SETTINGS,
                    )
                },
                onPower = actions::togglePower,
            )
        }
    }
}

/** Подменяет плотность так, чтобы эталонный макет занимал экран целиком (по меньшей из сторон). */
@Composable
private fun ScaledLayout(referenceWidth: Dp, referenceHeight: Dp, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = min(maxWidth / referenceWidth, maxHeight / referenceHeight).coerceIn(0.6f, 3f)
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * scale, min(density.fontScale, 1.15f)),
            content = content,
        )
    }
}

@Composable
private fun LeftColumn(state: DashboardState, actions: DashboardActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalogClock(Modifier.weight(1f).fillMaxWidth())
        SpeedCard(state.telemetry, state.trip, state.fuelLiters, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val panel = state.selectedPanel
            NavTile(NeonIconKind.CAR, "Главная", panel == ActivePanel.MAIN || panel == ActivePanel.NODE, Modifier.weight(1f)) {
                actions.selectPanel(ActivePanel.MAIN)
            }
            NavTile(NeonIconKind.GAUGE, "Датчики", panel == ActivePanel.SENSORS_ALL, Modifier.weight(1f)) {
                actions.selectPanel(ActivePanel.SENSORS_ALL)
            }
            NavTile(NeonIconKind.PULSE, "Монитор", panel == ActivePanel.DIAGNOSTICS_ECU, Modifier.weight(1f)) {
                actions.selectPanel(ActivePanel.DIAGNOSTICS_ECU)
            }
            NavTile(NeonIconKind.LEAF, "Выбросы", panel == ActivePanel.EMISSIONS, Modifier.weight(1f)) {
                actions.selectPanel(ActivePanel.EMISSIONS)
            }
        }
    }
}

@Composable
private fun NavTile(icon: NeonIconKind, label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = CarScanTheme.palette
    val accent = if (active) palette.active else palette.accent
    Column(
        modifier
            .height(54.dp)
            .neonPanel(
                accent = accent,
                shape = RoundedCornerShape(10.dp),
                fill = palette.tileFill,
                borderAlpha = if (active) 0.85f else 0.25f,
                glowAlpha = if (active) 0.45f else 0f,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NeonIcon(icon, if (active) palette.active else Neon.TextMuted, size = 20.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) Neon.Text else Neon.TextMuted,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
