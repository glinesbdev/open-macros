package dev.glinesb.openmacros.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.ExerciseEntity
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.Equipment
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.Muscle
import dev.glinesb.openmacros.ui.components.trimmed
import kotlinx.coroutines.launch

/** Coarse groups for filtering the library. */
enum class MuscleGroup(val label: String, val muscles: Set<Muscle>) {
    CHEST("Chest", setOf(Muscle.CHEST)),
    BACK("Back", setOf(Muscle.LATS, Muscle.UPPER_BACK, Muscle.LOWER_BACK, Muscle.TRAPS)),
    SHOULDERS("Shoulders", setOf(Muscle.FRONT_DELTS, Muscle.SIDE_DELTS, Muscle.REAR_DELTS)),
    ARMS("Arms", setOf(Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS)),
    LEGS("Legs", setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ADDUCTORS, Muscle.ABDUCTORS)),
    CORE("Core", setOf(Muscle.ABS, Muscle.OBLIQUES)),
    CARDIO("Cardio", setOf(Muscle.CARDIOVASCULAR)),
}

fun weightText(kg: Double?, unit: WeightUnit): String = kg?.let { Units.toDisplayWeight(it, unit).trimmed(2) }.orEmpty()

/** "BW", "BW + 25 lb" or "BW − 20 lb" for a bodyweight set's added (+) / assisted (−) load in kg. */
fun bwText(addedKg: Double?, unit: WeightUnit): String =
    dev.glinesb.openmacros.domain.training.Progression.bw(addedKg?.let { Units.toDisplayWeight(it, unit) } ?: 0.0, unit)

/** One set as text: "135 lb × 10", "BW × 10", "BW + 25 lb × 8", "30 min". */
fun setLabel(reps: Int?, kg: Double?, unit: WeightUnit, bodyweight: Boolean, cardio: Boolean): String = when {
    cardio -> "${reps ?: "–"} min"
    bodyweight -> "${bwText(kg, unit)} × ${reps ?: "–"}"
    kg == null -> "– × ${reps ?: "–"}"
    else -> "${weightText(kg, unit)} ${unit.symbol} × ${reps ?: "–"}"
}

fun repRange(min: Int?, max: Int?): String = when {
    min == null && max == null -> ""
    min == null || max == null || min == max -> "${min ?: max}"
    else -> "$min–$max"
}

fun restLabel(sec: Int): String = when {
    sec <= 0 -> "no rest"
    sec < 120 && sec % 60 != 0 -> "$sec s"
    sec % 60 == 0 -> "${sec / 60} min"
    else -> "%d:%02d".format(sec / 60, sec % 60)
}

fun clockLabel(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

fun durationLabel(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)} min" else "$min min"
}

fun ExerciseEntity.subtitle(): String = buildList {
    add(primary.joinToString(", ") { it.label })
    add(equipmentType.label)
    add(exerciseType.label)
}.filter { it.isNotEmpty() }.joinToString(" · ")

/** Searchable, filterable exercise list (used by the Exercises tab and the picker). */
@Composable
fun ExerciseList(
    all: List<ExerciseEntity>,
    onClick: (ExerciseEntity) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit = {},
) {
    var q by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    val shown = all.filter { e ->
        (q.isBlank() || e.name.contains(q.trim(), ignoreCase = true)) &&
            (group == null || e.primary.any { it in group!!.muscles })
    }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text("Search exercises") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") }) }
                items(MuscleGroup.entries) { g -> FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g.label) }) }
            }
        }
        item { header() }
        items(shown, key = { it.id }) { e ->
            ListItem(
                headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(e.subtitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = if (e.isCustom) ({ Text("Custom", style = MaterialTheme.typography.labelSmall) }) else null,
                modifier = Modifier.clickable { onClick(e) },
            )
        }
        if (shown.isEmpty()) item {
            Text("No exercises match. You can create your own.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Bottom sheet for choosing an exercise, with "create custom" built in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(onDismiss: () -> Unit, onPick: (ExerciseEntity) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val all by c.training.allExercises.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { c.training.ensureSeeded() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Add exercise", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        ExerciseList(all, onClick = onPick, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Create custom exercise")
            }
        }
    }
    if (creating) CustomExerciseDialog(null, onDismiss = { creating = false }) { name, primary, secondary, eq, type ->
        scope.launch {
            val id = c.training.createCustomExercise(name, primary, secondary, eq, type)
            creating = false
            c.db.exercises().byId(id)?.let(onPick)
        }
    }
}

/** Create or edit a custom exercise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomExerciseDialog(
    initial: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (String, List<Muscle>, List<Muscle>, Equipment, ExerciseType) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var primary by remember { mutableStateOf(initial?.primary?.toSet() ?: emptySet()) }
    var secondary by remember { mutableStateOf(initial?.secondary?.toSet() ?: emptySet()) }
    var eq by rememberSaveable { mutableStateOf(initial?.equipmentType ?: Equipment.BARBELL) }
    var type by rememberSaveable { mutableStateOf(initial?.exerciseType ?: ExerciseType.COMPOUND) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New exercise" else "Edit exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Type", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ExerciseType.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, ExerciseType.entries.size)) { Text(t.label) }
                    }
                }
                Text("Primary muscles", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.forEach { m ->
                        FilterChip(selected = m in primary, onClick = { primary = if (m in primary) primary - m else primary + m; secondary = secondary - m }, label = { Text(m.label) })
                    }
                }
                Text("Secondary muscles (optional)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muscle.entries.filter { it !in primary }.forEach { m ->
                        FilterChip(selected = m in secondary, onClick = { secondary = if (m in secondary) secondary - m else secondary + m }, label = { Text(m.label) })
                    }
                }
                Text("Equipment", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.forEach { e -> FilterChip(selected = eq == e, onClick = { eq = e }, label = { Text(e.label) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && primary.isNotEmpty(), onClick = {
                onSave(name.trim(), Muscle.entries.filter { it in primary }, Muscle.entries.filter { it in secondary }, eq, type)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small numeric field for set rows and routine targets. */
@Composable
fun CompactNumberField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = true,
    allowNegative: Boolean = false, placeholder: String? = null,
) {
    dev.glinesb.openmacros.ui.components.NumberField(value, onValueChange, label, modifier, decimal = decimal,
        allowNegative = allowNegative, placeholder = placeholder)
}
