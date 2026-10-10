package com.carscan.ui

import java.util.Locale
import kotlin.math.roundToInt

/** Форматирование чисел для приборной панели: всегда с точкой, «—» если значения нет. */
internal const val NO_VALUE = "—"

internal fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

internal fun Number?.orDash(suffix: String = "", decimals: Int = 0): String =
    if (this == null) NO_VALUE else this.toDouble().fmt(decimals) + suffix

/** Наружная температура со знаком: «+9 °C», «-3 °C», «0 °C». */
internal fun signedTemp(celsius: Double?): String {
    if (celsius == null) return NO_VALUE
    val rounded = celsius.roundToInt()
    return (if (rounded > 0) "+$rounded" else "$rounded") + " °C"
}

/** Длительность поездки: «07:32» до часа, «1:07:32» после. */
internal fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
