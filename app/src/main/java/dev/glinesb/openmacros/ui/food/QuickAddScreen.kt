package dev.glinesb.openmacros.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.domain.AmountUnit
import dev.glinesb.openmacros.domain.Macros
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.ui.components.NumberField
import dev.glinesb.openmacros.ui.components.kcal
import dev.glinesb.openmacros.ui.components.toNumberOrNull
import kotlinx.coroutines.launch

/** Log calories (and optionally macros) without a food. If calories are blank, they're computed from macros. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(day: Long, initialMeal: Meal, onBack: () -> Unit, onSaved: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }

    val p = protein.toNumberOrNull() ?: 0.0
    val cb = carbs.toNumberOrNull() ?: 0.0
    val ft = fat.toNumberOrNull() ?: 0.0
    val fromMacros = Macros.kcalFrom(p, cb, ft)
    val energy = kcal.toNumberOrNull() ?: fromMacros.takeIf { it > 0 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick add") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Description (optional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            NumberField(kcal, { kcal = it }, "Calories", suffix = "kcal",
                supportingText = if (kcal.isBlank() && fromMacros > 0) "From macros: ${fromMacros.kcal()} kcal" else null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            MealPicker(meal) { meal = it }
            Button(
                enabled = energy != null && energy > 0,
                onClick = {
                    scope.launch {
                        c.log.add(
                            LogEntryEntity(
                                epochDay = day, meal = meal.name, name = name.ifBlank { "Quick add" }, amount = 1.0,
                                unit = AmountUnit.SERVING.name, servings = 1.0, kcal = energy!!, protein = p, carbs = cb, fat = ft,
                                isQuickAdd = true,
                            ),
                        )
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add to ${meal.label}") }
        }
    }
}
