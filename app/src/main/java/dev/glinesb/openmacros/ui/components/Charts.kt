package dev.glinesb.openmacros.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.glinesb.openmacros.domain.DatedValue
import kotlin.math.roundToInt

/** Calorie ring: eaten vs target. Turns the error colour once over target. */
@Composable
fun CalorieRing(eaten: Double, target: Int, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val over = eaten > target
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val fraction = if (target > 0) (eaten / target).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = target - eaten.roundToInt()
    Box(
        modifier.size(size).semantics { contentDescription = "${eaten.roundToInt()} of $target calories" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (remaining >= 0) "$remaining" else "${-remaining}", style = MaterialTheme.typography.headlineMedium)
            Text(if (remaining >= 0) "kcal left" else "kcal over", style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MacroBar(label: String, value: Double, target: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} / $target g", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f },
            color = if (value > target * 1.1 && target > 0) MaterialTheme.colorScheme.error else color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

/**
 * Weight chart: raw weigh-ins as dots, smoothed trend as a line. Plain Compose Canvas, no chart library.
 * [points] and [trend] are in display units.
 */
@Composable
fun WeightChart(
    points: List<DatedValue>,
    trend: List<DatedValue>,
    modifier: Modifier = Modifier,
    emptyText: String = "Log a weigh-in to see your trend",
    description: String = "Weight trend chart",
) {
    val dot = MaterialTheme.colorScheme.outline
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    if (points.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(emptyText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val all = points + trend
        var minV = all.minOf { it.value }
        var maxV = all.maxOf { it.value }
        if (maxV - minV < 1.0) { val mid = (maxV + minV) / 2; minV = mid - 0.5; maxV = mid + 0.5 }
        val pad = (maxV - minV) * 0.1
        minV -= pad; maxV += pad
        val minD = points.minOf { it.epochDay }
        val maxD = maxOf(points.maxOf { it.epochDay }, minD + 1)
        val left = 40.dp.toPx(); val bottom = size.height - 20.dp.toPx(); val top = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        fun x(d: Long) = left + (d - minD).toFloat() / (maxD - minD) * (right - left)
        fun y(v: Double) = (bottom - (v - minV) / (maxV - minV) * (bottom - top)).toFloat()

        for (i in 0..3) {
            val v = minV + (maxV - minV) * i / 3
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            drawText(measurer, v.trimmed(1), Offset(0f, yy - 8.dp.toPx()), labelStyle)
        }
        drawText(measurer, shortDate(minD), Offset(left, bottom + 4.dp.toPx()), labelStyle)
        val endLabel = measurer.measure(shortDate(maxD), labelStyle)
        drawText(endLabel, topLeft = Offset(right - endLabel.size.width, bottom + 4.dp.toPx()))

        points.forEach { drawCircle(dot, radius = 3.dp.toPx(), center = Offset(x(it.epochDay), y(it.value))) }
        if (trend.size >= 2) {
            val path = Path()
            trend.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.epochDay), y(p.value)) else path.lineTo(x(p.epochDay), y(p.value)) }
            drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
