package dev.glinesb.openmacros.ui.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.DayDraft
import dev.glinesb.openmacros.data.ExerciseDraft
import dev.glinesb.openmacros.data.RoutineDraft
import dev.glinesb.openmacros.data.TrainingPlanner
import dev.glinesb.openmacros.domain.training.ExerciseType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val draftJson = Json { ignoreUnknownKeys = true }
private val DraftSaver = Saver<RoutineDraft?, String>(
    save = { d -> d?.let { draftJson.encodeToString(RoutineDraft.serializer(), it) } ?: "" },
    restore = { s -> if (s.isEmpty()) null else runCatching { draftJson.decodeFromString(RoutineDraft.serializer(), s) }.getOrNull() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(routineId: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<RoutineDraft?>(null) }
    var original by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<RoutineDraft?>(null) }
    var pickForDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Pair<Long, ExerciseDraft>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        if (draft == null) {
            val d = routineId?.let { c.training.loadDraft(it) }
                ?: RoutineDraft(name = "", days = listOf(DayDraft(TrainingPlanner.newKey(), "Day 1")))
            draft = d; original = d
        }
    }
    val dirty = draft != null && draft != original
    fun leave() { if (dirty) confirmLeave = true else onBack() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    fun updateDay(key: Long, f: (DayDraft) -> DayDraft) {
        draft = draft?.let { d -> d.copy(days = d.days.map { if (it.key == key) f(it) else it }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (routineId == null) "New routine" else "Edit routine") },
                navigationIcon = { IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(enabled = draft != null, onClick = {
                        val d = draft ?: return@TextButton
                        scope.launch { c.training.saveDraft(d); onSaved() }
                    }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        val d = draft ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(d.name, { draft = d.copy(name = it) }, label = { Text("Routine name") }, placeholder = { Text("e.g. Upper / Lower") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(d.notes, { draft = d.copy(notes = it) }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            }
            itemsIndexed(d.days, key = { _, day -> day.key }) { di, day ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(day.name, { v -> updateDay(day.key) { it.copy(name = v) } }, label = { Text("Day ${di + 1}") },
                                singleLine = true, modifier = Modifier.weight(1f))
                            IconButton(enabled = di > 0, onClick = { draft = d.copy(days = TrainingPlanner.move(d.days, di, -1)) }) { Icon(Icons.Filled.KeyboardArrowUp, "Move day up") }
                            IconButton(enabled = di < d.days.lastIndex, onClick = { draft = d.copy(days = TrainingPlanner.move(d.days, di, 1)) }) { Icon(Icons.Filled.KeyboardArrowDown, "Move day down") }
                            IconButton(onClick = { draft = d.copy(days = d.days.filter { it.key != day.key }) }) { Icon(Icons.Outlined.Delete, "Delete day") }
                        }
                        if (day.exercises.isEmpty()) Text("No exercises yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                        day.exercises.forEachIndexed { ei, e -> key(e.key) {
                            var menu by remember { mutableStateOf(false) }
                            ListItem(
                                headlineContent = { Text(e.name) },
                                supportingContent = {
                                    Text(
                                        if (e.type == ExerciseType.CARDIO.name) "${e.repMin}–${e.repMax} min" + (e.notes.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
                                        else "${e.sets} × ${repRange(e.repMin, e.repMax)} · rest ${restLabel(e.restSeconds)}" + (e.notes.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""),
                                    )
                                },
                                trailingContent = {
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Exercise options") }
                                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Edit targets") }, onClick = { menu = false; editing = day.key to e })
                                            if (ei > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = {
                                                menu = false; updateDay(day.key) { it.copy(exercises = TrainingPlanner.move(it.exercises, ei, -1)) }
                                            })
                                            if (ei < day.exercises.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = {
                                                menu = false; updateDay(day.key) { it.copy(exercises = TrainingPlanner.move(it.exercises, ei, 1)) }
                                            })
                                            DropdownMenuItem(text = { Text("Remove") }, onClick = {
                                                menu = false; updateDay(day.key) { it.copy(exercises = it.exercises.filter { x -> x.key != e.key }) }
                                            })
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { editing = day.key to e },
                            )
                        }
                        }
                        TextButton(onClick = { pickForDay = day.key }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add exercise") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    draft = d.copy(days = d.days + DayDraft(TrainingPlanner.newKey(), "Day ${d.days.size + 1}"))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add day") }
            }
            item {
                Text("Workouts follow the days in order: \"Start next\" picks the day after the one you trained last.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    pickForDay?.let { key ->
        ExercisePickerSheet(onDismiss = { pickForDay = null }) { ex ->
            pickForDay = null
            val cardio = ex.exerciseType == ExerciseType.CARDIO
            val iso = ex.exerciseType == ExerciseType.ISOLATION
            updateDay(key) {
                it.copy(exercises = it.exercises + ExerciseDraft(
                    TrainingPlanner.newKey(), ex.id, ex.name, ex.type,
                    sets = if (cardio) 1 else 3, repMin = if (cardio) 20 else if (iso) 10 else 6, repMax = if (cardio) 30 else if (iso) 15 else 10,
                    restSeconds = if (cardio) 0 else if (iso) 60 else 120,
                ))
            }
        }
    }
    editing?.let { (dayKey, e) ->
        ExerciseTargetsDialog(e, onDismiss = { editing = null }) { updated ->
            editing = null
            updateDay(dayKey) { it.copy(exercises = it.exercises.map { x -> if (x.key == updated.key) updated else x }) }
        }
    }
    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Discard changes?") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") } },
    )
}

@Composable
private fun ExerciseTargetsDialog(e: ExerciseDraft, onDismiss: () -> Unit, onSave: (ExerciseDraft) -> Unit) {
    val cardio = e.type == ExerciseType.CARDIO.name
    var sets by remember { mutableStateOf(e.sets.toString()) }
    var lo by remember { mutableStateOf(e.repMin.toString()) }
    var hi by remember { mutableStateOf(e.repMax.toString()) }
    var rest by remember { mutableStateOf(e.restSeconds.toString()) }
    var notes by remember { mutableStateOf(e.notes) }
    val loN = lo.toIntOrNull(); val hiN = hi.toIntOrNull(); val setsN = sets.toIntOrNull(); val restN = rest.toIntOrNull()
    val valid = loN != null && hiN != null && loN in 1..hiN && (cardio || (setsN != null && setsN in 1..20)) && (restN ?: 0) in 0..900
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!cardio) CompactNumberField(sets, { sets = it }, "Sets", Modifier.fillMaxWidth(), decimal = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactNumberField(lo, { lo = it }, if (cardio) "Min minutes" else "Min reps", Modifier.weight(1f), decimal = false)
                    CompactNumberField(hi, { hi = it }, if (cardio) "Max minutes" else "Max reps", Modifier.weight(1f), decimal = false)
                }
                if (!cardio) CompactNumberField(rest, { rest = it }, "Rest (seconds)", Modifier.fillMaxWidth(), decimal = false)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                if (!valid) Text("Min must be at least 1 and no more than max.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(e.copy(sets = if (cardio) 1 else setsN!!, repMin = loN!!, repMax = hiN!!, restSeconds = if (cardio) 0 else restN ?: 0, notes = notes.trim()))
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
