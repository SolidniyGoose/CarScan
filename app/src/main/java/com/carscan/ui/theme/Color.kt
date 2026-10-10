package com.carscan.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.carscan.ui.StatusLevel

/**
 * Смысловые цвета, одинаковые во всех темах: зелёный — норма, янтарный — внимание, красный — авария.
 * Цвета оформления (акцент, свечение, фон) задаёт [CarScanPalette].
 */
object Neon {
    val Green = Color(0xFF3DFF9A)
    val Amber = Color(0xFFFFB02E)
    val Orange = Color(0xFFFF7A1A)
    val Red = Color(0xFFFF4D4D)
    val Info = Color(0xFF6CC8FF)

    val Text = Color(0xFFE8F1F8)
    val TextMuted = Color(0xFF8EA3B5)
    val TextDim = Color(0xFF5B6B78)
    val Divider = Color(0x1AFFFFFF)
}

/** Цветовая гамма интерфейса. Те же цвета передаются в viewer.html для 3D-сцены. */
@Immutable
data class CarScanPalette(
    val id: String,
    val title: String,
    /** Рамки, свечение, линии, выделение. */
    val accent: Color,
    /** Активная плитка, кнопка питания, карточка скорости. */
    val active: Color,
    val background: Color,
    val panelFill: Color,
    val tileFill: Color,
    /** Подсветка «гаража» за машиной. */
    val sceneGlow: Color,
    /** Цвет «стекла» кузова в рентген-режиме. */
    val xrayTint: Color,
)

object Palettes {
    val Cyan = CarScanPalette(
        id = "cyan", title = "Бирюза",
        accent = Color(0xFF19E3FF), active = Color(0xFF3DFF9A),
        background = Color(0xFF06090D), panelFill = Color(0xD90A1118), tileFill = Color(0xCC111A23),
        sceneGlow = Color(0xFF2C5276), xrayTint = Color(0xFF6B9ED1),
    )
    val Amber = CarScanPalette(
        id = "amber", title = "Янтарь",
        accent = Color(0xFFFFB02E), active = Color(0xFFFFE07A),
        background = Color(0xFF0B0805), panelFill = Color(0xD9150F08), tileFill = Color(0xCC1E160C),
        sceneGlow = Color(0xFF6B4A1E), xrayTint = Color(0xFFD9A75E),
    )
    val Red = CarScanPalette(
        id = "red", title = "Красный спорт",
        accent = Color(0xFFFF3B4A), active = Color(0xFFFF9A3D),
        background = Color(0xFF0C0607), panelFill = Color(0xD9170A0C), tileFill = Color(0xCC221012),
        sceneGlow = Color(0xFF6E1E26), xrayTint = Color(0xFFD9727C),
    )
    val Emerald = CarScanPalette(
        id = "emerald", title = "Изумруд",
        accent = Color(0xFF2EF2A0), active = Color(0xFFC6FF5C),
        background = Color(0xFF050B09), panelFill = Color(0xD9081410), tileFill = Color(0xCC0E1D17),
        sceneGlow = Color(0xFF1E5E45), xrayTint = Color(0xFF6BD1A8),
    )
    val Violet = CarScanPalette(
        id = "violet", title = "Лиловый",
        accent = Color(0xFFB07CFF), active = Color(0xFFFF6AD5),
        background = Color(0xFF08060D), panelFill = Color(0xD9100B19), tileFill = Color(0xCC181126),
        sceneGlow = Color(0xFF45307A), xrayTint = Color(0xFFA58AD9),
    )
    val Ice = CarScanPalette(
        id = "ice", title = "Лёд",
        accent = Color(0xFFCFE8FF), active = Color(0xFF7FC8FF),
        background = Color(0xFF07090C), panelFill = Color(0xD90D1116), tileFill = Color(0xCC151B22),
        sceneGlow = Color(0xFF3C4A5A), xrayTint = Color(0xFFB4C8DC),
    )

    val all = listOf(Cyan, Amber, Red, Emerald, Violet, Ice)

    fun byId(id: String?): CarScanPalette = all.firstOrNull { it.id == id } ?: Cyan
}

fun StatusLevel?.color(default: Color = Neon.Text): Color = when (this) {
    StatusLevel.OK -> Neon.Green
    StatusLevel.INFO -> Neon.Info
    StatusLevel.WARN -> Neon.Amber
    StatusLevel.CRIT -> Neon.Red
    StatusLevel.OFF -> Neon.TextDim
    null -> default
}
