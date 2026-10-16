package dev.glinesb.openmacros.ui.training

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.WorkoutExerciseEntity
import dev.glinesb.openmacros.data.db.WorkoutSetEntity
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.domain.training.ExerciseType
import dev.glinesb.openmacros.domain.training.ProgressionSuggestion
import dev.glinesb.openmacros.domain.training.SetType
import dev.glinesb.openmacros.ui.components.toNumberOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private class Loaded<T>(val value: T)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(workoutId: Long, onBack: () -> Unit, onFinished: (Long) -> Unit, onOpenExercise: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val workoutState by remember(workoutId) { c.training.observeWorkout(workoutId).map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val exercises by c.training.observeWorkoutExercises(workoutId).collectAsStateWithLifecycle(emptyList())
    val allSets by c.training.observeWorkoutSets(workoutId).collectAsStateWithLifecycle(emptyList())
    val setsByExercise = allSets.groupBy { it.workoutExerciseId }
    var restEnd by rememberSaveable { mutableLongStateOf(0L) }
    var restTotal by rememberSaveable { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var notes by rememberSaveable(workoutId) { mutableStateOf<String?>(null) }

    val workout = workoutState?.value
    LaunchedEffect(workoutState) {
        val s = workoutState ?: return@LaunchedEffect
        if (!leaving && (s.value == null || s.value.finishedAt != null)) { leaving = true; onBack() }
        if (notes == null && s.value != null) notes = s.value.notes
    }
    // Keep the screen on while logging.
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restTotal = seconds; restEnd = System.currentTimeMillis() + seconds * 1000L
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(workout?.name ?: "Workout", style = MaterialTheme.typography.titleLarge)
                        workout?.let { ElapsedText(it.startedAt) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back (the workout stays open)") } },
                actions = {
                    Button(onClick = { confirmFinish = true }, enabled = workout != null) { Text("Finish") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Discard workout") }, onClick = { menu = false; confirmDiscard = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (restEnd > 0) RestBar(restEnd, restTotal,
                onAdjust = { d -> restEnd = (restEnd + d * 1000L).coerceAtLeast(System.currentTimeMillis()); restTotal = (restTotal + d).coerceAtLeast(1) },
                onDone = { restEnd = 0L })
        },
    ) { padding ->
        if (workout == null) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (exercises.isEmpty()) item {
                Text("Add an exercise to get started.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp))
            }
            itemsIndexed(exercises, key = { _, e -> e.id }) { i, we ->
                ExerciseCard(
                    we = we, sets = setsByExercise[we.id].orEmpty(), isFirst = i == 0, isLast = i == exercises.lastIndex,
                    onRest = ::startRest, onOpenExercise = onOpenExercise,
                )
            }
            item {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
                }
            }
            item {
                OutlinedTextField(
                    value = notes.orEmpty(),
                    onValueChange = { v -> notes = v; scope.launch { c.training.updateWorkout(workout.copy(notes = v)) } },
                    label = { Text("Workout notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
            }
        }
    }

    if (picking) ExercisePickerSheet(onDismiss = { picking = false }) { ex ->
        picking = false
        scope.launch { c.training.addExercise(workoutId, ex) }
    }
    if (confirmFinish && workout != null) {
        val done = allSets.count { it.done && it.reps != null }
        val dropped = allSets.size - done
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish workout?") },
            text = {
                Text(
                    "$done set${if (done == 1) "" else "s"} marked done will be saved." +
                        if (dropped > 0) " $dropped unchecked or empty set${if (dropped == 1) "" else "s"} will be dropped." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false; leaving = true
                    scope.launch { c.training.finish(workoutId, notes.orEmpty()); onFinished(workoutId) }
                }) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
        )
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this workout?") },
        text = { Text("Nothing from it will be saved.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; scope.launch { c.training.deleteWorkout(workoutId) } }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ElapsedText(startedAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val sec = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text(if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else clockLabel(sec),
        style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun RestBar(endAt: Long, total: Int, onAdjust: (Int) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endAt) { vibrate(context); onDone(); break }
            delay(250)
        }
    }
    val left = ((endAt - now + 999) / 1000).coerceAtLeast(0)
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rest", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(12.dp))
                Text(clockLabel(left), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAdjust(-15) }) { Text("−15 s") }
                TextButton(onClick = { onAdjust(15) }) { Text("+15 s") }
                TextButton(onClick = onDone) { Text("Skip") }
            }
            LinearProgressIndicator(progress = { if (total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
    }
}

@Composable
private fun ExerciseCard(
    we: WorkoutExerciseEntity,
    sets: List<WorkoutSetEntity>,
    isFirst: Boolean,
    isLast: Boolean,
    onRest: (Int) -> Unit,
    onOpenExercise: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val cardio = we.exerciseType == ExerciseType.CARDIO
    val unit = we.weightUnit
    var menu by remember { mutableStateOf(false) }
    // Bumped when sets are changed from outside a text field (suggestion applied), so fields re-read the database.
    var version by remember { mutableIntStateOf(0) }
    var editTargets by remember { mutableStateOf(false) }
    val suggestion by produceState<ProgressionSuggestion?>(null, we.id, we.unit, we.repMin, we.repMax) { value = c.training.suggestion(we) }
    val last by produceState(emptyList<WorkoutSetEntity>(), we.id) { value = c.training.lastSets(we) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(we.name, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).clickable(enabled = we.exerciseId != null) { we.exerciseId?.let(onOpenExercise) })
                if (!cardio) TextButton(onClick = {
                    scope.launch { c.training.setUnit(we, if (unit == WeightUnit.LB) WeightUnit.KG else WeightUnit.LB) }
                }) { Text(if (unit == WeightUnit.LB) "lb → kg" else "kg → lb") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Exercise options") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Targets and rest") }, onClick = { menu = false; editTargets = true })
                        if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; scope.launch { c.training.moveExercise(we.workoutId, we.id, -1) } })
                        if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; scope.launch { c.training.moveExercise(we.workoutId, we.id, 1) } })
                        DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; scope.launch { c.training.removeExercise(we.id) } })
                    }
                }
            }
            val target = buildList {
                if (we.repMin != null || we.repMax != null) add(if (cardio) "${repRange(we.repMin, we.repMax)} min" else "${repRange(we.repMin, we.repMax)} reps")
                if (!cardio && we.restSeconds > 0) add("rest ${restLabel(we.restSeconds)}")
            }.joinToString(" · ")
            if (target.isNotEmpty()) Text("Target $target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (we.notes.isNotBlank()) Text(we.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lastWorking = last.filter { it.type == SetType.WORKING }
            if (lastWorking.isNotEmpty()) Text(
                "Last time: " + lastWorking.joinToString(", ") { s ->
                    if (cardio) "${s.reps ?: "–"} min" else "${weightText(s.weightKg, s.weightUnit).ifEmpty { "BW" }}×${s.reps ?: "–"}"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            suggestion?.let { s ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        if (s.weightKg != null || s.targetReps > 0) TextButton(onClick = {
                            scope.launch { c.training.applySuggestion(we, s); version++ }
                        }) { Text("Apply") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Set", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                if (!cardio) Text(unit.symbol, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(if (cardio) "Minutes" else "Reps", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Done", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(40.dp))
            }
            var workingNo = 0
            sets.forEach { set ->
                val label = if (set.type == SetType.WARMUP) "W" else "${++workingNo}"
                key(set.id) {
                    SetRow(
                        set = set, label = label, cardio = cardio, version = version,
                        onReps = { v -> scope.launch { c.training.setReps(set.id, v) } },
                        onWeight = { kg -> scope.launch { c.training.setWeight(set.id, kg) } },
                        onType = { t -> scope.launch { c.training.setType(set.id, t) } },
                        onDone = { done ->
                            scope.launch { c.training.setDone(set.id, done) }
                            if (done && !cardio) onRest(we.restSeconds)
                        },
                        onDelete = { scope.launch { c.training.deleteSet(set.id) } },
                    )
                }
            }
            TextButton(onClick = { scope.launch { c.training.addSet(we) } }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add set") }
        }
    }
    if (editTargets) TargetsDialog(we, onDismiss = { editTargets = false }) { updated ->
        editTargets = false
        scope.launch { c.training.updateExercise(updated) }
    }
}

@Composable
private fun SetRow(
    set: WorkoutSetEntity,
    label: String,
    cardio: Boolean,
    version: Int,
    onReps: (Int?) -> Unit,
    onWeight: (Double?) -> Unit,
    onType: (SetType) -> Unit,
    onDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    // Text is local so typing never races the database round trip; it re-reads on unit change or "Apply".
    var w by remember(set.unit, version) { mutableStateOf(weightText(set.weightKg, set.weightUnit)) }
    var r by remember(version) { mutableStateOf(set.reps?.toString().orEmpty()) }
    val rowColor = if (set.done) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    Surface(color = rowColor, shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onType(if (set.type == SetType.WARMUP) SetType.WORKING else SetType.WARMUP) },
                modifier = Modifier.width(44.dp), contentPadding = PaddingValues(0.dp),
            ) { Text(label, fontWeight = FontWeight.SemiBold) }
            if (!cardio) CompactNumberField(w, { v ->
                w = v
                val n = v.toNumberOrNull()
                onWeight(n?.let { Units.fromDisplayWeight(it, set.weightUnit) })
            }, "", Modifier.weight(1f).padding(horizontal = 4.dp))
            CompactNumberField(r, { v ->
                r = v
                onReps(v.toIntOrNull())
            }, "", Modifier.weight(1f).padding(horizontal = 4.dp), decimal = false)
            Checkbox(checked = set.done, onCheckedChange = onDone, modifier = Modifier.width(48.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Close, "Delete set") }
        }
    }
}

@Composable
private fun TargetsDialog(we: WorkoutExerciseEntity, onDismiss: () -> Unit, onSave: (WorkoutExerciseEntity) -> Unit) {
    var lo by remember { mutableStateOf(we.repMin?.toString().orEmpty()) }
    var hi by remember { mutableStateOf(we.repMax?.toString().orEmpty()) }
    var rest by remember { mutableStateOf(we.restSeconds.toString()) }
    var notes by remember { mutableStateOf(we.notes) }
    val loN = lo.toIntOrNull(); val hiN = hi.toIntOrNull()
    val valid = (loN == null && hiN == null) || (loN != null && hiN != null && loN in 1..hiN)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(we.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactNumberField(lo, { lo = it }, "Min reps", Modifier.weight(1f), decimal = false)
                    CompactNumberField(hi, { hi = it }, "Max reps", Modifier.weight(1f), decimal = false)
                }
                CompactNumberField(rest, { rest = it }, "Rest (seconds)", Modifier.fillMaxWidth(), decimal = false)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(we.copy(repMin = loN, repMax = hiN, restSeconds = (rest.toIntOrNull() ?: 0).coerceIn(0, 900), notes = notes.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

