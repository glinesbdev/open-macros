package dev.glinesb.openmacros.ui.weight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.DatedValue
import dev.glinesb.openmacros.domain.Trend
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.ui.components.*
import kotlinx.coroutines.launch

private enum class Range(val label: String, val days: Long?) { M1("1M", 30), M3("3M", 90), Y1("1Y", 365), ALL("All", null) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val entries by c.weights.all.collectAsStateWithLifecycle(emptyList())
    val unit = settings.weightUnit
    var input by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(today()) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    var pickDate by remember { mutableStateOf(false) }

    // Trend is computed over all history (so the start of a range isn't biased), then clipped.
    val raw = entries.map { DatedValue(it.epochDay, Units.toDisplayWeight(it.weightKg, unit)) }
    val trend = Trend.exponentialTrend(raw)
    val avg7 = Trend.trailingMovingAverage(raw, 7)
    val from = range.days?.let { today() - it } ?: Long.MIN_VALUE
    val shownRaw = raw.filter { it.epochDay >= from }
    val shownTrend = trend.filter { it.epochDay >= from }

    Scaffold(topBar = { TopAppBar(title = { Text("Weight") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Trend", style = MaterialTheme.typography.labelMedium)
                                Text(trend.lastOrNull()?.let { "${it.value.trimmed(1)} ${unit.symbol}" } ?: "–",
                                    style = MaterialTheme.typography.headlineMedium)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("7-day avg ${avg7.lastOrNull()?.value?.trimmed(1) ?: "–"}", style = MaterialTheme.typography.bodySmall)
                                Text("Week change ${change(trend, 7)?.let { signed(it) } ?: "–"}", style = MaterialTheme.typography.bodySmall)
                                Text("Month change ${change(trend, 30)?.let { signed(it) } ?: "–"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        WeightChart(shownRaw, shownTrend, Modifier.fillMaxWidth().height(220.dp))
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            Range.entries.forEachIndexed { i, r ->
                                SegmentedButton(selected = range == r, onClick = { range = r },
                                    shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size)) { Text(r.label) }
                            }
                        }
                        Text("Dots are weigh-ins; the line is a smoothed trend that evens out daily water swings.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Log weight", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberField(input, { input = it }, "Weight", Modifier.weight(1f), suffix = unit.symbol)
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { pickDate = true }) { Text(dayLabel(day)) }
                        }
                        val v = input.toNumberOrNull()
                        Button(
                            enabled = v != null && v > 0,
                            onClick = {
                                val kg = Units.fromDisplayWeight(v!!, unit)
                                scope.launch { c.weights.log(day, kg) }
                                // The newest weigh-in feeds the calorie target (BMR depends on weight).
                                if (entries.none { it.epochDay > day }) c.settings.update { s -> s.copy(profile = s.profile?.copy(weightKg = kg)) }
                                input = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save") }
                    }
                }
            }
            if (entries.isNotEmpty()) item { Text("History", style = MaterialTheme.typography.titleMedium) }
            items(entries.reversed(), key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text("${Units.toDisplayWeight(e.weightKg, unit).trimmed(1)} ${unit.symbol}") },
                    supportingContent = { Text(dayLabel(e.epochDay)) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { c.weights.delete(e) } }) { Icon(Icons.Outlined.Delete, "Delete") }
                    },
                )
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { day = it / 86_400_000L }; pickDate = false }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}

/** Trend now minus trend [days] ago (nearest point at or before that day). */
private fun change(trend: List<DatedValue>, days: Long): Double? {
    val last = trend.lastOrNull() ?: return null
    val past = trend.lastOrNull { it.epochDay <= last.epochDay - days } ?: return null
    return last.value - past.value
}

private fun signed(d: Double) = (if (d > 0) "+" else "") + d.trimmed(1)
