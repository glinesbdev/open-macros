package dev.glinesb.openmacros.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.SetRow
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.domain.DatedValue
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.LoggedSet
import dev.glinesb.openmacros.domain.training.Session
import dev.glinesb.openmacros.domain.training.SetType
import dev.glinesb.openmacros.domain.training.Strength
import dev.glinesb.openmacros.ui.components.WeightChart
import dev.glinesb.openmacros.ui.components.dayLabel
import dev.glinesb.openmacros.ui.components.trimmed
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun setText(reps: Int?, kg: Double?, unit: WeightUnit, type: SetType, cardio: Boolean): String {
    val prefix = if (type == SetType.WARMUP) "W " else ""
    return prefix + if (cardio) "${reps ?: "–"} min" else "${weightText(kg, unit).ifEmpty { "BW" }}${if (kg != null) " ${unit.symbol}" else ""} × ${reps ?: "–"}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit, onOpenExercise: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val workout by c.training.observeWorkout(workoutId).collectAsStateWithLifecycle(null)
    val exercises by c.training.observeWorkoutExercises(workoutId).collectAsStateWithLifecycle(emptyList())
    val sets by c.training.observeWorkoutSets(workoutId).collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val byEx = sets.groupBy { it.workoutExerciseId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "Workout") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete workout") } },
            )
        },
    ) { padding ->
        val w = workout ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val vol = Strength.volumeKg(exercises.filter { it.exerciseType != ExerciseType.CARDIO }.flatMap { byEx[it.id].orEmpty() }.map(WorkoutSetEntity::toLogged))
                val unit = settings.trainingUnit
                Text(listOfNotNull(
                    dayLabel(w.epochDay), w.finishedAt?.let { durationLabel(it - w.startedAt) },
                    vol.takeIf { it > 0 }?.let { "${Units.toDisplayWeight(it, unit).roundToInt()} ${unit.symbol} volume" },
                ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(exercises, key = { it.id }) { we ->
                val cardio = we.exerciseType == ExerciseType.CARDIO
                val list = byEx[we.id].orEmpty()
                OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = we.exerciseId != null) { we.exerciseId?.let(onOpenExercise) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(we.name, style = MaterialTheme.typography.titleMedium)
                        list.forEach { s -> Text(setText(s.reps, s.weightKg, s.weightUnit, s.type, cardio), style = MaterialTheme.typography.bodyMedium) }
                        if (!cardio) list.filter { it.type == SetType.WORKING }.maxOfOrNull { Strength.e1rm(it.weightKg ?: 0.0, it.reps ?: 0) }
                            ?.takeIf { it > 0 }?.let { Text("Best e1RM ${Units.toDisplayWeight(it, we.weightUnit).trimmed(1)} ${we.weightUnit.symbol}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this workout?") },
        text = { Text("Its sets are removed from your history, charts and PRs.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.training.deleteWorkout(workoutId); onBack() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(exerciseId: Long, onBack: () -> Unit, onOpenWorkout: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val exercise by c.training.observeExercise(exerciseId).collectAsStateWithLifecycle(null)
    val rows by c.training.observeExerciseSets(exerciseId).collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ex = exercise
    val cardio = ex?.exerciseType == ExerciseType.CARDIO
    // Show progress in the unit last used for this exercise, else the training default.
    val unit = rows.lastOrNull()?.let { runCatching { WeightUnit.valueOf(it.unit) }.getOrNull() } ?: settings.trainingUnit
    val sessions = rows.groupBy { it.workoutId }.map { (wid, r) ->
        Session(wid, r.first().epochDay, r.map { LoggedSet(it.reps, it.weightKg, runCatching { SetType.valueOf(it.setType) }.getOrDefault(SetType.WORKING), it.done) })
    }
    val loaded = rows.any { (it.weightKg ?: 0.0) > 0 }
    val series: List<DatedValue> = when {
        cardio -> sessions.map { s -> DatedValue(s.epochDay, s.working.sumOf { it.reps ?: 0 }.toDouble()) }
        loaded -> Strength.e1rmSeries(sessions).map { DatedValue(it.epochDay, Units.toDisplayWeight(it.value, unit)) }
        else -> Strength.bestRepsSeries(sessions)
    }
    val chartLabel = when { cardio -> "Minutes per session"; loaded -> "Estimated 1RM (${unit.symbol}, Epley)"; else -> "Best reps per session" }
    val pr = Strength.records(sessions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ex?.name ?: "Exercise") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (ex?.isCustom == true) {
                        IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Edit exercise") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete exercise") }
                    }
                },
            )
        },
    ) { padding ->
        if (ex == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(ex.subtitle(), style = MaterialTheme.typography.bodyMedium)
                if (ex.secondary.isNotEmpty()) Text("Also works: " + ex.secondary.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(chartLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        WeightChart(series, series, Modifier.fillMaxWidth().height(200.dp),
                            emptyText = "Finish a workout with this exercise to see progress", description = chartLabel)
                    }
                }
            }
            if (!cardio && sessions.isNotEmpty()) item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Personal records", style = MaterialTheme.typography.titleMedium)
                        fun w(kg: Double) = "${Units.toDisplayWeight(kg, unit).trimmed(1)} ${unit.symbol}"
                        pr.bestE1rm?.let { PrLine("Best estimated 1RM", "${w(it.value)} (${it.detail})", it.epochDay) }
                        pr.heaviest?.let { PrLine("Heaviest set", "${w(it.value)} ${it.detail}", it.epochDay) }
                        pr.mostReps?.let { PrLine("Most reps in a set", it.value.roundToInt().toString(), it.epochDay) }
                        pr.bestVolume?.let { PrLine("Best session volume", w(it.value), it.epochDay) }
                    }
                }
            }
            if (sessions.isNotEmpty()) item { Text("History", style = MaterialTheme.typography.titleMedium) }
            items(rows.groupBy { it.workoutId }.entries.reversed().toList(), key = { it.key }) { (wid, r) ->
                ListItem(
                    headlineContent = { Text("${dayLabel(r.first().epochDay)} · ${r.first().workoutName}") },
                    supportingContent = { Text(r.joinToString("\n") { it.line(cardio) }) },
                    modifier = Modifier.clickable { onOpenWorkout(wid) },
                )
            }
        }
    }
    if (editing && ex != null) CustomExerciseDialog(ex, onDismiss = { editing = false }) { name, p, s, eq, type ->
        editing = false
        scope.launch {
            c.training.updateCustomExercise(ex.copy(name = name, primaryMuscles = dev.glinesb.openmacros.domain.training.Muscle.toCsv(p),
                secondaryMuscles = dev.glinesb.openmacros.domain.training.Muscle.toCsv(s), equipment = eq.name, type = type.name))
        }
    }
    if (confirmDelete && ex != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete \"${ex.name}\"?") },
        text = { Text("It's removed from your library and routines. Logged workouts keep their sets under this name, but it won't have a progress page.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { c.training.deleteCustomExercise(ex); onBack() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PrLine(label: String, value: String, day: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
            Text(dayLabel(day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun SetRow.line(cardio: Boolean): String {
    val u = runCatching { WeightUnit.valueOf(unit) }.getOrDefault(WeightUnit.KG)
    val t = runCatching { SetType.valueOf(setType) }.getOrDefault(SetType.WORKING)
    return setText(reps, weightKg, u, t, cardio)
}
