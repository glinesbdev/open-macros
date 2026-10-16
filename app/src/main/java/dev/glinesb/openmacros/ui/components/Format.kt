package dev.glinesb.openmacros.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

fun Double.kcal(): String = roundToInt().toString()
fun Double.grams(): String = if (this < 10 && this % 1.0 != 0.0) "%.1f".format(this) else roundToInt().toString()
fun Double.trimmed(maxDecimals: Int = 2): String {
    val s = "%.${maxDecimals}f".format(Locale.ROOT, this)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}

fun today(): Long = LocalDate.now().toEpochDay()

fun dayLabel(epochDay: Long): String = when (epochDay - today()) {
    0L -> "Today"
    -1L -> "Yesterday"
    1L -> "Tomorrow"
    else -> LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

fun shortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

/** Parses user number input, accepting either decimal separator. */
fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
