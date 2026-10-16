package dev.glinesb.openmacros.ui.training

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.GoalType
import dev.glinesb.openmacros.domain.Tdee
import dev.glinesb.openmacros.domain.Units
import dev.glinesb.openmacros.domain.training.EquipmentAccess
import dev.glinesb.openmacros.domain.training.ExerciseLibrary
import dev.glinesb.openmacros.domain.training.Experience
import dev.glinesb.openmacros.domain.training.SplitInput
import dev.glinesb.openmacros.domain.training.SplitRecommendation
import dev.glinesb.openmacros.domain.training.SplitRecommender
import dev.glinesb.openmacros.domain.training.TrainingGoal
import dev.glinesb.openmacros.ui.components.trimmed
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecommenderScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val profile = settings.profile
    val initialGoal = when (profile?.goal) { GoalType.LOSE -> TrainingGoal.FAT_LOSS; GoalType.GAIN -> TrainingGoal.MUSCLE_GAIN; else -> TrainingGoal.RECOMP }
    var days by rememberSaveable { mutableIntStateOf(4) }
    var goal by rememberSaveable { mutableStateOf(initialGoal) }
    var experience by rememberSaveable { mutableStateOf(Experience.INTERMEDIATE) }
    var equipment by rememberSaveable { mutableStateOf(EquipmentAccess.FULL_GYM) }
    var goalPicked by rememberSaveable { mutableStateOf(false) }
    var offerDismissed by rememberSaveable { mutableStateOf<TrainingGoal?>(null) }
    var updatedNote by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val input = SplitInput(days, goal, experience, equipment)
    val recs = remember(input) { SplitRecommender.recommend(input) }
    val nutrition = profile?.let { SplitRecommender.nutritionFor(goal, experience, it.weightKg) }
    val nutritionDiffers = nutrition != null && profile != null &&
        (nutrition.goal != profile.goal || (nutrition.goal != GoalType.MAINTAIN && abs(nutrition.rateKgPerWeek - profile.rateKgPerWeek) > 0.02))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recommend a split") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Days per week", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        (2..6).forEachIndexed { i, n ->
                            SegmentedButton(selected = days == n, onClick = { days = n }, shape = SegmentedButtonDefaults.itemShape(i, 5)) { Text("$n") }
                        }
                    }
                    Text("Goal", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrainingGoal.entries.forEach { g ->
                            FilterChip(selected = goal == g, onClick = { goal = g; goalPicked = true; updatedNote = null }, label = { Text(g.label) })
                        }
                    }
                    Text("Experience", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Experience.entries.forEachIndexed { i, e ->
                            SegmentedButton(selected = experience == e, onClick = { experience = e }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(e.label) }
                        }
                    }
                    Text(experience.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Equipment", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        EquipmentAccess.entries.forEachIndexed { i, e ->
                            SegmentedButton(selected = equipment == e, onClick = { equipment = e }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(e.label) }
                        }
                    }
                }
            }
            // Offer (never force) a matching nutrition goal once the user has picked a training goal.
            if (goalPicked && nutritionDiffers && offerDismissed != goal && profile != null && nutrition != null) item {
                val unit = settings.weightUnit
                val newProfile = profile.copy(goal = nutrition.goal, rateKgPerWeek = nutrition.rateKgPerWeek)
                val rateText = if (nutrition.goal == GoalType.MAINTAIN) "maintain your weight"
                    else "${nutrition.goal.label.lowercase()} ${Units.toDisplayWeight(nutrition.rateKgPerWeek, unit).trimmed(2)} ${unit.symbol} a week"
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Update your nutrition goal too?", style = MaterialTheme.typography.titleMedium)
                        Text("Set it to $rateText (about ${Tdee.calorieTarget(newProfile)} kcal a day, now ${settings.calorieTarget}). ${nutrition.reason}",
                            style = MaterialTheme.typography.bodyMedium)
                        if (settings.calorieOverride != null) Text("You have a manual calorie target set; it stays in effect until you clear it in Settings.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                c.settings.update { it.copy(profile = it.profile?.copy(goal = nutrition.goal, rateKgPerWeek = nutrition.rateKgPerWeek)) }
                                updatedNote = "Nutrition goal updated: $rateText."
                            }) { Text("Update") }
                            TextButton(onClick = { offerDismissed = goal }) { Text("Not now") }
                        }
                    }
                }
            }
            updatedNote?.let { note -> item { Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) } }
            item {
                Text(recs.firstOrNull()?.goalNote.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            items(recs, key = { it.id }) { rec ->
                RecommendationCard(rec, creating) {
                    creating = true
                    scope.launch {
                        val id = c.training.saveRecommendation(rec)
                        c.training.setActive(id)
                        creating = false
                        onCreated(id)
                    }
                }
            }
            item {
                Text("Templates are a starting point: swap exercises, change targets and reorder days in the editor. " +
                    "Based on published research on training frequency, volume and rep ranges; see the README for references.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecommendationCard(rec: SplitRecommendation, busy: Boolean, onCreate: () -> Unit) {
    var expanded by rememberSaveable(rec.id) { mutableStateOf(rec.recommended) }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rec.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (rec.recommended) SuggestionChip(onClick = {}, label = { Text("Recommended") })
            }
            Text(rec.days.joinToString(" → ") { it.name }, style = MaterialTheme.typography.labelMedium)
            Text("Why: ${rec.why}", style = MaterialTheme.typography.bodyMedium)
            val weekly = rec.weeklySets()
            val groups = MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }.map { g ->
                g to g.muscles.maxOfOrNull { weekly[it] ?: 0.0 }.let { it ?: 0.0 }
            }
            Text("Hard sets a week (busiest muscle in each group): " + groups.joinToString(" · ") { (g, v) -> "${g.label} ${v.roundToInt()}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide exercises" else "Show exercises") }
            if (expanded) rec.days.forEach { d ->
                Text(d.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                d.exercises.forEach { p ->
                    val name = ExerciseLibrary.bySlug[p.slug]?.name ?: p.slug
                    Text("• $name — ${p.sets} × ${repRange(p.repMin, p.repMax)}, rest ${restLabel(p.restSeconds)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(enabled = !busy, onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create this routine") }
            Text("It becomes your active routine and opens in the editor.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
