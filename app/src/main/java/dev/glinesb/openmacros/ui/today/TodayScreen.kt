package dev.glinesb.openmacros.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.domain.AmountUnit
import dev.glinesb.openmacros.domain.Macros
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.ui.components.*
import dev.glinesb.openmacros.ui.theme.LocalMacroColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onAddFood: (Long, Meal) -> Unit, onEditEntry: (LogEntryEntity) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableLongStateOf(today()) }
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val entries by remember(day) { c.log.forDay(day) }.collectAsStateWithLifecycle(emptyList())
    var selected by remember { mutableStateOf<LogEntryEntity?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    val total = entries.fold(Macros.ZERO) { acc, e -> acc + e.macros }
    val targets = settings.macroTargets
    val colors = LocalMacroColors.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { day-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
                },
                title = { TextButton(onClick = { pickDate = true }) { Text(dayLabel(day), style = MaterialTheme.typography.titleLarge) } },
                actions = {
                    IconButton(onClick = { day++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CalorieRing(total.kcal, settings.calorieTarget)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${total.kcal.kcal()} / ${settings.calorieTarget} kcal", style = MaterialTheme.typography.titleSmall)
                            MacroBar("Protein", total.protein, targets.protein, colors.protein)
                            MacroBar("Carbs", total.carbs, targets.carbs, colors.carbs)
                            MacroBar("Fat", total.fat, targets.fat, colors.fat)
                        }
                    }
                }
            }
            Meal.entries.forEach { meal ->
                val mealEntries = entries.filter { it.meal == meal.name }
                item(key = meal.name) {
                    MealCard(
                        meal = meal,
                        entries = mealEntries,
                        onAdd = { onAddFood(day, meal) },
                        onEntryClick = { selected = it },
                    )
                }
            }
        }
    }

    selected?.let { e ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.name) },
            text = { Text("${e.kcal.kcal()} kcal · P ${e.protein.grams()} g · C ${e.carbs.grams()} g · F ${e.fat.grams()} g") },
            confirmButton = {
                if (e.foodId != null) TextButton(onClick = { selected = null; onEditEntry(e) }) { Text("Edit") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null; scope.launch { c.log.delete(e) } }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = it / 86_400_000L }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { day = today(); pickDate = false }) { Text("Today") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun MealCard(meal: Meal, entries: List<LogEntryEntity>, onAdd: () -> Unit, onEntryClick: (LogEntryEntity) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${entries.sumOf { it.kcal }.kcal()} kcal", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add to ${meal.label}") }
        }
        entries.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clickable { onEntryClick(e) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(entrySubtitle(e), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(e.kcal.kcal(), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (entries.isEmpty()) {
            Text("Nothing logged", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
        } else Spacer(Modifier.height(8.dp))
    }
}

private fun entrySubtitle(e: LogEntryEntity): String {
    if (e.isQuickAdd) return "Quick add · P ${e.protein.grams()} · C ${e.carbs.grams()} · F ${e.fat.grams()}"
    val unit = runCatching { AmountUnit.valueOf(e.unit) }.getOrDefault(AmountUnit.SERVING)
    val amount = when (unit) {
        AmountUnit.SERVING -> "${e.amount.trimmed()} serving" + if (e.amount != 1.0) "s" else ""
        else -> "${e.amount.trimmed()} ${unit.label}"
    }
    return listOfNotNull(e.brand, amount).joinToString(" · ")
}
