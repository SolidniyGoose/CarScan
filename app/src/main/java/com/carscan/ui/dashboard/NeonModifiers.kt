package com.carscan.ui.dashboard

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Неоновое свечение по контуру формы.
 *
 * Размытие считается один раз в растровую картинку (при смене размера) и дальше рисуется как текстура.
 * Иначе пока WebView крутит 3D-сцену (60 кадров/с), каждый кадр заново размывались бы все рамки панелей,
 * и вращение машины на планшете заметно тормозило.
 */
fun Modifier.neonGlow(
    color: Color,
    shape: Shape,
    radius: Dp = 10.dp,
    alpha: Float = 0.45f,
): Modifier = if (alpha <= 0f) this else drawWithCache {
    val blur = radius.toPx()
    val pad = (blur * 2).toInt() + 2
    val width = size.width.toInt() + pad * 2
    val height = size.height.toInt() + pad * 2
    if (width <= pad * 2 || height <= pad * 2) return@drawWithCache onDrawBehind {}
    val path = Path().apply {
        when (val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }.asAndroidPath()
    // Программный Canvas: BlurMaskFilter работает на всех версиях Android
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(bitmap).apply {
        translate(pad.toFloat(), pad.toFloat())
        drawPath(
            path,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2.dp.toPx()
                this.color = color.copy(alpha = alpha).toArgb()
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            },
        )
    }
    val image = bitmap.asImageBitmap()
    onDrawBehind {
        drawImage(image, topLeft = Offset(-pad.toFloat(), -pad.toFloat()))
    }
}

/** Полупрозрачная панель со светящейся рамкой — базовый «кирпичик» интерфейса как на референсе. */
fun Modifier.neonPanel(
    accent: Color,
    fill: Color,
    shape: Shape = RoundedCornerShape(14.dp),
    borderAlpha: Float = 0.5f,
    glowAlpha: Float = 0.35f,
): Modifier = this
    .neonGlow(accent, shape, alpha = glowAlpha)
    .clip(shape)
    .background(fill)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                accent.copy(alpha = borderAlpha),
                accent.copy(alpha = borderAlpha * 0.2f),
                accent.copy(alpha = borderAlpha * 0.8f),
            ),
        ),
        shape = shape,
    )
