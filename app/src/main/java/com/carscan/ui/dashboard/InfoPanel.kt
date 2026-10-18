package com.carscan.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carscan.ui.ChoiceGroup
import com.carscan.ui.PanelAction
import com.carscan.ui.PanelChoice
import com.carscan.ui.PanelRow
import com.carscan.ui.PanelSection
import com.carscan.ui.PanelSpec
import com.carscan.ui.PanelStepper
import com.carscan.ui.StepperId
import com.carscan.ui.theme.CarScanTheme
import com.carscan.ui.theme.Neon
import com.carscan.ui.theme.color

/**
 * Правая панель: заголовок, «чип» узла, кнопки действий и блоки с таблицей «Параметр / Значение».
 * Блок с заголовком сворачивается; блок без заголовка показывается как есть.
 */
@Composable
fun InfoPanel(
    spec: PanelSpec,
    xrayEnabled: Boolean,
    onAction: (PanelAction) -> Unit,
    onChoice: (ChoiceGroup, String) -> Unit,
    onStep: (StepperId, Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handlers = SectionHandlers(onChoice, onStep, onAction, xrayEnabled)
    val palette = CarScanTheme.palette
    Column(
        modifier
            .neonPanel(accent = palette.accent, fill = palette.panelFill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(spec.caption, color = Neon.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                Text(
                    spec.title,
                    color = Neon.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (spec.chip != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        spec.chip,
                        color = palette.accent,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (spec.closable) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    NeonIcon(NeonIconKind.CLOSE, Neon.TextMuted, size = 16.dp)
                }
            }
        }

        if (spec.actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // По две кнопки в ряд: в узкой панели три уже не помещаются
            spec.actions.chunked(2).forEach { rowActions ->
                Row(
                    Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowActions.forEach { action -> ActionButton(action, xrayEnabled, Modifier.weight(1f, fill = false), onAction) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = Neon.Divider)

        Column(Modifier.verticalScroll(rememberScrollState())) {
            spec.sections.forEachIndexed { index, section ->
                // Ключ включает заголовок панели: у каждой панели своё состояние «свёрнуто/развёрнуто»
                PanelSectionView(section, stateKey = spec.title + "/" + (section.title ?: index), handlers = handlers)
            }
        }
    }
}

/** Обработчики, которые нужны содержимому раздела. */
private class SectionHandlers(
    val onChoice: (ChoiceGroup, String) -> Unit,
    val onStep: (StepperId, Int) -> Unit,
    val onAction: (PanelAction) -> Unit,
    val xrayEnabled: Boolean,
)

@Composable
private fun ActionButton(action: PanelAction, xrayEnabled: Boolean, modifier: Modifier, onAction: (PanelAction) -> Unit) {
    val (icon, label) = when (action) {
        PanelAction.XRAY -> NeonIconKind.XRAY to if (xrayEnabled) "Обычный вид" else "Рентген"
        PanelAction.FOCUS -> NeonIconKind.FOCUS to "Навести камеру"
        PanelAction.REFRESH_WEATHER -> NeonIconKind.CLOUD to "Обновить погоду"
        PanelAction.RESET_TRIP -> NeonIconKind.REFRESH to "Сбросить поездку"
        PanelAction.OBD_LOG -> NeonIconKind.PULSE to "Журнал OBD"
        PanelAction.REFRESH_DEVICES -> NeonIconKind.REFRESH to "Адаптеры"
        PanelAction.BACK_TO_SETTINGS -> NeonIconKind.CHEVRON_LEFT to "К настройкам"
        PanelAction.PICK_VEHICLE -> NeonIconKind.CAR to "Выбрать авто"
        PanelAction.PICKER_BACK -> NeonIconKind.CHEVRON_LEFT to "Назад"
        PanelAction.RESET_VEHICLE -> NeonIconKind.REFRESH to "Сбросить"
        PanelAction.CHECK_UPDATES -> NeonIconKind.REFRESH to "Проверить"
        PanelAction.INSTALL_UPDATE -> NeonIconKind.DOWNLOAD to "Обновить"
        PanelAction.SELF_TEST -> NeonIconKind.PULSE to "Проверить датчики"
        PanelAction.SEND_REPORT -> NeonIconKind.SEND to "Отправить отчёт"
        PanelAction.SHARE_REPORT -> NeonIconKind.SEND to "Поделиться"
        PanelAction.DEEP_SCAN -> NeonIconKind.FOCUS to "Глубокий скан"
    }
    PanelButton(icon, label, modifier) { onAction(action) }
}

@Composable
private fun PanelButton(icon: NeonIconKind, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = CarScanTheme.palette.accent
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonIcon(icon, accent, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        Text(label, color = Neon.Text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelSectionView(section: PanelSection, stateKey: String, handlers: SectionHandlers) {
    if (section.title == null) {
        Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) { SectionContent(section, handlers) }
        return
    }
    var expanded by rememberSaveable(stateKey) { mutableStateOf(section.expanded) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.title,
                color = Neon.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            NeonIcon(
                if (expanded) NeonIconKind.CHEVRON_DOWN else NeonIconKind.CHEVRON_RIGHT,
                Neon.TextMuted,
                size = 14.dp,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(bottom = 8.dp)) { SectionContent(section, handlers) }
        }
        HorizontalDivider(color = Neon.Divider)
    }
}

@Composable
private fun SectionContent(section: PanelSection, handlers: SectionHandlers) {
    if (section.rows.isNotEmpty()) {
        TableHeader()
        section.rows.forEach { TableRow(it) }
    }
    if (section.actions.isNotEmpty()) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            section.actions.forEach { ActionButton(it, handlers.xrayEnabled, Modifier.weight(1f, fill = false), handlers.onAction) }
        }
    }
    if (section.steppers.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        section.steppers.forEach { StepperRow(it) { direction -> handlers.onStep(it.id, direction) } }
    }
    val group = section.choiceGroup
    if (group != null && section.choices.isNotEmpty()) {
        if (section.rows.isNotEmpty() || section.steppers.isNotEmpty()) Spacer(Modifier.height(6.dp))
        if (section.choices.any { it.swatch != null }) {
            SwatchGrid(section.choices) { handlers.onChoice(group, it) }
        } else {
            section.choices.forEach { choice -> ChoiceRow(choice) { handlers.onChoice(group, choice.id) } }
        }
    }
    if (section.text != null) {
        Text(
            section.text,
            color = Neon.TextMuted,
            fontSize = if (section.monospace) 9.sp else 11.sp,
            lineHeight = if (section.monospace) 13.sp else 15.sp,
            fontFamily = if (section.monospace) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.padding(top = if (section.rows.isEmpty() && section.choices.isEmpty()) 0.dp else 6.dp),
        )
    }
}

/** Параметр с кнопками «−/+»; изменённое вручную значение отмечено точкой цвета темы. */
@Composable
private fun StepperRow(stepper: PanelStepper, onStep: (Int) -> Unit) {
    val accent = CarScanTheme.palette.accent
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stepper.label, color = Neon.TextMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
        StepButton(NeonIconKind.MINUS) { onStep(-1) }
        Text(
            (if (stepper.modified) "• " else "") + stepper.value,
            color = if (stepper.modified) accent else Neon.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(62.dp),
        )
        StepButton(NeonIconKind.PLUS) { onStep(1) }
    }
}

@Composable
private fun StepButton(icon: NeonIconKind, onClick: () -> Unit) {
    val accent = CarScanTheme.palette.accent
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NeonIcon(icon, accent, size = 14.dp)
    }
}

/** Вариант выбора в виде строки с «радиокнопкой» (источник данных, адаптер, вид машины). */
@Composable
private fun ChoiceRow(choice: PanelChoice, onClick: () -> Unit) {
    val accent = CarScanTheme.palette.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (choice.selected) accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) {
            drawCircle(if (choice.selected) accent else Neon.TextDim, style = Stroke(1.5.dp.toPx()))
            if (choice.selected) drawCircle(accent, radius = size.minDimension / 4f)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                choice.label,
                color = if (choice.selected) Neon.Text else Neon.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (choice.hint != null) {
                Text(choice.hint, color = Neon.TextDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Образцы цветовых гамм по три в ряд. */
@Composable
private fun SwatchGrid(choices: List<PanelChoice>, onPick: (String) -> Unit) {
    choices.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { choice ->
                val colors = choice.swatch.orEmpty()
                val main = colors.firstOrNull() ?: Neon.Text
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (choice.selected) main else Neon.Divider, RoundedCornerShape(8.dp))
                        .background(if (choice.selected) main.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { onPick(choice.id) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        // Левая половина — акцент, правая — цвет активных элементов
                        drawArc(main, 90f, 180f, useCenter = true, size = Size(size.width, size.height))
                        drawArc(colors.getOrElse(1) { main }, 270f, 180f, useCenter = true, size = Size(size.width, size.height))
                        if (choice.selected) drawCircle(Color.White, radius = size.minDimension / 2f, style = Stroke(1.5.dp.toPx()))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        choice.label,
                        color = if (choice.selected) Neon.Text else Neon.TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Неполный последний ряд: пустые ячейки, чтобы образцы не растягивались
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
        Text("ПАРАМЕТР", color = Neon.TextDim, fontSize = 8.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(0.5f))
        Text("ЗНАЧЕНИЕ", color = Neon.TextDim, fontSize = 8.sp, letterSpacing = 0.8.sp, modifier = Modifier.weight(0.5f))
    }
}

@Composable
private fun TableRow(row: PanelRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.param,
            color = Neon.TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.5f).padding(end = 6.dp),
        )
        Text(
            row.value,
            color = row.level.color(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.5f),
        )
    }
}
