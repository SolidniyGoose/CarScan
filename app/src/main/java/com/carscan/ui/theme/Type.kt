package com.carscan.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// bodyLarge — стиль Text по умолчанию. Высота строки относительная (em),
// иначе мелкие подписи панели получают огромные 24sp межстрочного интервала.
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.25.em,
        letterSpacing = 0.2.sp
    )
)
