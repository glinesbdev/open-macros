package dev.glinesb.openmacros.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.res.painterResource
import dev.glinesb.openmacros.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.domain.AmountUnit
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.domain.Portions
import dev.glinesb.openmacros.ui.components.*
import dev.glinesb.openmacros.ui.theme.LocalMacroColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDetailScreen(
    foodId: Long,
    day: Long,
    initialMeal: Meal,
    entryId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditFood: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val food by remember(foodId) { c.foods.observe(foodId) }.collectAsStateWithLifecycle(null)
    var amount by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(AmountUnit.SERVING) }
    var meal by rememberSaveable { mutableStateOf(initialMeal) }
    var existing by remember { mutableStateOf<LogEntryEntity?>(null) }
    var prefilled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (entryId != null) {
            existing = c.log.byId(entryId)
            if (!prefilled) existing?.let { e ->
                amount = e.amount.trimmed(); unit = runCatching { AmountUnit.valueOf(e.unit) }.getOrDefault(AmountUnit.SERVING)
                meal = Meal.valueOf(e.meal); prefilled = true
            }
        }
    }

    val f = food
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId != null) "Edit entry" else "Add food") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (f != null) {
                        if (f.isCustom) IconButton(onClick = { onEditFood(f.id) }) { Icon(Icons.Outlined.Edit, "Edit food") }
                        IconButton(onClick = { scope.launch { c.foods.setFavorite(f.id, !f.isFavorite) } }) {
                            if (f.isFavorite) Icon(Icons.Filled.Star, "Remove from favorites")
                            else Icon(painterResource(R.drawable.ic_star_outline), "Add to favorites")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (f == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val units = Portions.unitsFor(f.servingGrams)
        if (unit !in units) unit = AmountUnit.SERVING
        val amt = amount.toNumberOrNull()
        val servings = amt?.let { Portions.servings(it, unit, f.servingGrams) }
        val totals = servings?.let { f.perServing * it }
        val colors = LocalMacroColors.current

        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(f.name, style = MaterialTheme.typography.headlineSmall)
                f.brand?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("1 serving = ${f.servingLabel}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(amount, { amount = it }, "Amount", Modifier.weight(1f), isError = amt == null || amt <= 0)
                Spacer(Modifier.width(12.dp))
                SingleChoiceSegmentedButtonRow {
                    units.forEachIndexed { i, u ->
                        SegmentedButton(
                            selected = unit == u,
                            onClick = {
                                // Keep the same quantity when switching units.
                                val s = amt?.let { Portions.servings(it, unit, f.servingGrams) }
                                if (s != null && f.servingGrams != null) amount = when (u) {
                                    AmountUnit.SERVING -> s.trimmed()
                                    AmountUnit.GRAM -> (s * f.servingGrams).trimmed(0)
                                    AmountUnit.OUNCE -> (s * f.servingGrams / dev.glinesb.openmacros.domain.Units.G_PER_OZ).trimmed(1)
                                }
                                unit = u
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, units.size),
                        ) { Text(u.label) }
                    }
                }
            }
            MealPicker(meal) { meal = it }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${totals?.kcal?.kcal() ?: "–"} kcal", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        MacroStat("Protein", totals?.protein, colors.protein)
                        MacroStat("Carbs", totals?.carbs, colors.carbs)
                        MacroStat("Fat", totals?.fat, colors.fat)
                    }
                }
            }
            Text(
                when (f.foodSource) {
                    FoodSource.OPEN_FOOD_FACTS -> "Data from Open Food Facts (ODbL). Barcode ${f.barcode.orEmpty()}"
                    FoodSource.USDA -> "Data from USDA FoodData Central (public domain), FDC ID ${f.sourceId}"
                    FoodSource.CUSTOM -> "Your custom food"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = totals != null && amt > 0 && day >= 0,
                onClick = {
                    val s = servings ?: return@Button
                    val t = totals ?: return@Button
                    scope.launch {
                        val base = existing
                        val entry = LogEntryEntity(
                            id = base?.id ?: 0, epochDay = base?.epochDay ?: day, meal = meal.name, foodId = f.id,
                            name = f.name, brand = f.brand, amount = amt!!, unit = unit.name, servings = s,
                            kcal = t.kcal, protein = t.protein, carbs = t.carbs, fat = t.fat,
                            createdAt = base?.createdAt ?: System.currentTimeMillis(),
                        )
                        if (base != null) c.log.update(entry) else c.log.add(entry)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (existing != null) "Save changes" else "Add to ${meal.label}") }
        }
    }
}

@Composable
private fun MacroStat(label: String, grams: Double?, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams?.grams() ?: "–"} g", style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealPicker(selected: Meal, onSelect: (Meal) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Meal.entries.forEach { m ->
            FilterChip(selected = m == selected, onClick = { onSelect(m) }, label = { Text(m.label) })
        }
    }
}
