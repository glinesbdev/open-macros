package dev.glinesb.openmacros.ui.goals

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.*
import dev.glinesb.openmacros.ui.components.NumberField
import dev.glinesb.openmacros.ui.components.kcal
import dev.glinesb.openmacros.ui.components.today
import dev.glinesb.openmacros.ui.components.toNumberOrNull
import dev.glinesb.openmacros.ui.components.trimmed
import kotlinx.coroutines.launch

private val LB_RATES = listOf(0.25, 0.5, 1.0, 1.5, 2.0)
private val KG_RATES = listOf(0.1, 0.25, 0.5, 0.75, 1.0)

/** Onboarding (first launch) and "Edit profile & goal" share this form. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(isOnboarding: Boolean, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val initial = remember { c.settings.state.value }
    val p0 = initial.profile

    var metric by rememberSaveable { mutableStateOf(initial.weightUnit == WeightUnit.KG) }
    var sex by rememberSaveable { mutableStateOf(p0?.sex ?: Sex.MALE) }
    var age by rememberSaveable { mutableStateOf(p0?.ageYears?.toString().orEmpty()) }
    val (ft0, in0) = p0?.let { Units.cmToFeetInches(it.heightCm) } ?: (0 to 0.0)
    var heightCm by rememberSaveable { mutableStateOf(p0?.heightCm?.trimmed(0).orEmpty()) }
    var feet by rememberSaveable { mutableStateOf(if (p0 != null) ft0.toString() else "") }
    var inches by rememberSaveable { mutableStateOf(if (p0 != null) in0.trimmed(0) else "") }
    var weight by rememberSaveable {
        mutableStateOf(p0?.let { Units.toDisplayWeight(it.weightKg, initial.weightUnit).trimmed(1) }.orEmpty())
    }
    var activity by rememberSaveable { mutableStateOf(p0?.activity ?: ActivityLevel.LIGHT) }
    var goal by rememberSaveable { mutableStateOf(p0?.goal ?: GoalType.LOSE) }
    var rateKg by rememberSaveable { mutableDoubleStateOf(p0?.rateKgPerWeek?.takeIf { it > 0 } ?: Units.lbToKg(1.0)) }

    if (isOnboarding) {
        val activity2 = context as? Activity
        BackHandler { activity2?.finish() }
    }

    val wUnit = if (metric) WeightUnit.KG else WeightUnit.LB
    val ageN = age.toIntOrNull()?.takeIf { it in 13..110 }
    val hCm = if (metric) heightCm.toNumberOrNull() else {
        val f = feet.toIntOrNull(); val i = inches.toNumberOrNull() ?: 0.0
        f?.let { Units.feetInchesToCm(it, i) }
    }?.takeIf { it in 100.0..250.0 }
    val wKg = weight.toNumberOrNull()?.let { Units.fromDisplayWeight(it, wUnit) }?.takeIf { it in 25.0..400.0 }
    val profile = if (ageN != null && hCm != null && wKg != null) Profile(
        sex, ageN, hCm, wKg, activity, goal, if (goal == GoalType.MAINTAIN) 0.0 else rateKg,
    ) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOnboarding) "Welcome to Open Macros" else "Profile & goal") },
                navigationIcon = {
                    if (!isOnboarding) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (isOnboarding) Text(
                "A few details to estimate your daily calorie target. Everything stays on this phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Segmented("Units", listOf("Imperial (lb, ft)", "Metric (kg, cm)"), if (metric) 1 else 0) { i ->
                val toMetric = i == 1
                if (toMetric != metric) {
                    // Convert what's already typed.
                    weight.toNumberOrNull()?.let { w ->
                        weight = (if (toMetric) Units.lbToKg(w) else Units.kgToLb(w)).trimmed(1)
                    }
                    if (toMetric) hCm?.let { heightCm = it.trimmed(0) }
                    else hCm?.let { Units.cmToFeetInches(it) }?.let { (f, inch) -> feet = f.toString(); inches = inch.trimmed(0) }
                    metric = toMetric
                }
            }
            Segmented("Sex (used by the BMR formula)", listOf("Male", "Female"), sex.ordinal) { sex = Sex.entries[it] }
            NumberField(age, { age = it }, "Age", suffix = "years", decimal = false)
            if (metric) {
                NumberField(heightCm, { heightCm = it }, "Height", suffix = "cm")
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(feet, { feet = it }, "Height", Modifier.weight(1f), suffix = "ft", decimal = false)
                NumberField(inches, { inches = it }, "", Modifier.weight(1f), suffix = "in")
            }
            NumberField(weight, { weight = it }, "Current weight", suffix = wUnit.symbol)

            Text("Activity level", style = MaterialTheme.typography.titleSmall)
            Column {
                ActivityLevel.entries.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = activity == a, onClick = { activity = a }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = activity == a, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(a.label, style = MaterialTheme.typography.bodyLarge)
                            Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Segmented("Goal", GoalType.entries.map { it.label }, goal.ordinal) { goal = GoalType.entries[it] }
            if (goal != GoalType.MAINTAIN) {
                Text("Rate per week", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rates = if (metric) KG_RATES else LB_RATES
                    rates.forEach { r ->
                        val kg = if (metric) r else Units.lbToKg(r)
                        FilterChip(
                            selected = kotlin.math.abs(kg - rateKg) < 0.01,
                            onClick = { rateKg = kg },
                            label = { Text("${r.trimmed()} ${wUnit.symbol}") },
                        )
                    }
                }
            }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (profile == null) {
                        Text("Fill in age, height and weight to see your target.")
                    } else {
                        Text("${Tdee.calorieTarget(profile)} kcal/day", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "BMR ${Tdee.bmr(profile).kcal()} × ${activity.multiplier} activity = ${Tdee.tdee(profile).kcal()} kcal maintenance" +
                                when (goal) {
                                    GoalType.MAINTAIN -> ""
                                    GoalType.LOSE -> ", minus ${(-Tdee.dailyAdjustment(goal, rateKg)).kcal()} for your rate"
                                    GoalType.GAIN -> ", plus ${Tdee.dailyAdjustment(goal, rateKg).kcal()} for your rate"
                                },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (Tdee.isFloored(profile)) Text(
                            "That rate would put you under ${Tdee.MIN_CALORIES} kcal, so the target is held at ${Tdee.MIN_CALORIES}. " +
                                "Consider a slower rate.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        )
                        Text("An estimate (Mifflin-St Jeor). Adjust once you see how your weight trend moves.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Button(
                enabled = profile != null,
                onClick = {
                    val p = profile ?: return@Button
                    c.settings.update {
                        it.copy(
                            profile = p,
                            weightUnit = wUnit,
                            heightUnit = if (metric) HeightUnit.CM else HeightUnit.FT_IN,
                        )
                    }
                    scope.launch {
                        // Seed the weight chart with today's weight on first run (or if today has none).
                        if (c.weights.forDay(today()) == null) c.weights.log(today(), p.weightKg)
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isOnboarding) "Start tracking" else "Save") }
        }
    }
}

@Composable
private fun Segmented(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(selected = selected == i, onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(o, maxLines = 1) }
            }
        }
    }
}
