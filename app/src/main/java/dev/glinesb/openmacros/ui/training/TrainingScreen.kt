package dev.glinesb.openmacros.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.TrainingPlanner
import dev.glinesb.openmacros.data.db.RoutineEntity
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.ui.components.dayLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class TrainingTab(val label: String) { WORKOUT("Workout"), HISTORY("History"), EXERCISES("Exercises") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    onOpenWorkout: (Long) -> Unit,
    onOpenWorkoutDetail: (Long) -> Unit,
    onEditRoutine: (Long?) -> Unit,
    onRecommend: () -> Unit,
    onOpenExercise: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    var tab by rememberSaveable { mutableStateOf(TrainingTab.WORKOUT) }
    LaunchedEffect(Unit) { c.training.ensureSeeded() }
    Scaffold(topBar = { TopAppBar(title = { Text("Training") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                TrainingTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }) }
            }
            when (tab) {
                TrainingTab.WORKOUT -> WorkoutTab(onOpenWorkout, onEditRoutine, onRecommend)
                TrainingTab.HISTORY -> HistoryTab(onOpenWorkoutDetail)
                TrainingTab.EXERCISES -> ExercisesTab(onOpenExercise)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutTab(onOpenWorkout: (Long) -> Unit, onEditRoutine: (Long?) -> Unit, onRecommend: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val routines by c.training.allRoutines.collectAsStateWithLifecycle(emptyList())
    val allDays by c.training.allDays.collectAsStateWithLifecycle(emptyList())
    val active by c.training.activeWorkout.collectAsStateWithLifecycle(null)
    val activeRoutine = routines.firstOrNull { it.id == settings.activeRoutineId }
    val lastForRoutine by remember(activeRoutine?.id) {
        activeRoutine?.let { c.training.observeLastForRoutine(it.id) } ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsStateWithLifecycle(null)
    var pendingStart by remember { mutableStateOf<Long?>(null) } // day id, or -1 for empty
    var confirmDelete by remember { mutableStateOf<RoutineEntity?>(null) }

    fun start(dayId: Long?) {
        if (active != null) { pendingStart = dayId ?: -1L; return }
        scope.launch { onOpenWorkout(c.training.startWorkout(dayId)) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        active?.let { w ->
            item {
                ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("In progress", style = MaterialTheme.typography.labelMedium)
                            Text(w.name, style = MaterialTheme.typography.titleLarge)
                        }
                        Button(onClick = { onOpenWorkout(w.id) }) { Text("Resume") }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeRoutine == null) {
                        Text("No active routine", style = MaterialTheme.typography.titleMedium)
                        Text("Get a split recommended for your schedule and goal, or build your own.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRecommend) { Text("Recommend a split") }
                            OutlinedButton(onClick = { onEditRoutine(null) }) { Text("Build my own") }
                        }
                    } else {
                        val days = allDays.filter { it.routineId == activeRoutine.id }
                        val next = TrainingPlanner.nextDay(days, lastForRoutine?.routineDayId)
                        Text(activeRoutine.name, style = MaterialTheme.typography.labelLarge)
                        if (next != null) {
                            Text("Next: ${next.name}", style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = { start(next.id) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start ${next.name}")
                            }
                            lastForRoutine?.let { Text("Last session: ${it.name}, ${dayLabel(it.epochDay)}", style = MaterialTheme.typography.bodySmall) }
                            val others = days.filter { it.id != next.id }
                            if (others.isNotEmpty()) {
                                Text("Or pick a day", style = MaterialTheme.typography.labelMedium)
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    others.forEach { d -> AssistChip(onClick = { start(d.id) }, label = { Text(d.name) }) }
                                }
                            }
                        } else Text("This routine has no days yet. Edit it to add some.", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = { start(null) }, modifier = Modifier.fillMaxWidth()) { Text("Start an empty workout") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Routines", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onRecommend) { Text("Recommend") }
                TextButton(onClick = { onEditRoutine(null) }) { Icon(Icons.Filled.Add, null); Text("New") }
            }
        }
        if (routines.isEmpty()) item { Text("No routines yet.", style = MaterialTheme.typography.bodyMedium) }
        items(routines, key = { it.id }) { r ->
            val dayCount = allDays.count { it.routineId == r.id }
            val isActive = r.id == settings.activeRoutineId
            var menu by remember { mutableStateOf(false) }
            ListItem(
                leadingContent = {
                    IconButton(onClick = { c.training.setActive(r.id) }) {
                        Icon(Icons.Filled.Star, if (isActive) "Active routine" else "Make active",
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    }
                },
                headlineContent = { Text(r.name) },
                supportingContent = { Text("$dayCount day${if (dayCount == 1) "" else "s"}" + if (isActive) " · Active" else "") },
                trailingContent = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Routine options") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (!isActive) DropdownMenuItem(text = { Text("Make active") }, onClick = { menu = false; c.training.setActive(r.id) })
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEditRoutine(r.id) })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; scope.launch { c.training.duplicate(r.id) } })
                            DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; scope.launch { c.training.move(r.id, -1) } })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; scope.launch { c.training.move(r.id, 1) } })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; confirmDelete = r })
                        }
                    }
                },
                modifier = Modifier.clickable { onEditRoutine(r.id) },
            )
        }
    }

    pendingStart?.let { dayId ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Workout in progress") },
            text = { Text("Finish or discard \"${active?.name}\" before starting another, or pick up where you left off.") },
            confirmButton = { TextButton(onClick = { pendingStart = null; active?.let { onOpenWorkout(it.id) } }) { Text("Resume") } },
            dismissButton = {
                TextButton(onClick = {
                    pendingStart = null
                    scope.launch {
                        active?.let { c.training.deleteWorkout(it.id) }
                        onOpenWorkout(c.training.startWorkout(dayId.takeIf { it > 0 }))
                    }
                }) { Text("Discard it and start") }
            },
        )
    }
    confirmDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${r.name}\"?") },
            text = { Text("The routine is removed. Workouts you've already logged from it stay in your history.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; scope.launch { c.training.delete(r.id) } }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryTab(onOpen: (Long) -> Unit) {
    val c = LocalContext.current.container
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val history by c.training.history.collectAsStateWithLifecycle(emptyList())
    val unit = settings.trainingUnit
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Finished workouts show up here.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(history, key = { it.id }) { w ->
            val dur = w.finishedAt?.let { durationLabel(it - w.startedAt) }
            ListItem(
                headlineContent = { Text(w.name) },
                supportingContent = {
                    Text(listOfNotNull(
                        dayLabel(w.epochDay), dur, "${w.exerciseCount} exercises", "${w.setCount} sets",
                        w.volumeKg.takeIf { it > 0 }?.let { "${Units.toDisplayWeight(it, unit).roundToInt()} ${unit.symbol} volume" },
                    ).joinToString(" · "))
                },
                modifier = Modifier.clickable { onOpen(w.id) },
            )
        }
    }
}

@Composable
private fun ExercisesTab(onOpen: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val all by c.training.allExercises.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    ExerciseList(all, onClick = { onOpen(it.id) }, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${all.size} exercises", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Custom exercise") }
        }
    }
    if (creating) CustomExerciseDialog(null, onDismiss = { creating = false }) { name, p, s, eq, type ->
        creating = false
        scope.launch { c.training.createCustomExercise(name, p, s, eq, type) }
    }
}

