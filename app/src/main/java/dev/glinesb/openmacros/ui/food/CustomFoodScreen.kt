package dev.glinesb.openmacros.ui.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.db.FoodEntity
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Macros
import dev.glinesb.openmacros.ui.components.NumberField
import dev.glinesb.openmacros.ui.components.kcal
import dev.glinesb.openmacros.ui.components.toNumberOrNull
import dev.glinesb.openmacros.ui.components.trimmed
import kotlinx.coroutines.launch

/** Create or edit a custom food (also the fallback when a scanned barcode isn't in Open Food Facts). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFoodScreen(
    foodId: Long?,
    initialBarcode: String,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf(initialBarcode) }
    var serving by rememberSaveable { mutableStateOf("1 serving") }
    var grams by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<FoodEntity?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(foodId) {
        if (foodId != null) {
            val f = c.foods.byId(foodId)
            original = f
            if (f != null && !loaded) {
                name = f.name; brand = f.brand.orEmpty(); barcode = f.barcode.orEmpty(); serving = f.servingLabel
                grams = f.servingGrams?.trimmed(1).orEmpty(); kcal = f.kcal.trimmed(1); protein = f.protein.trimmed(1)
                carbs = f.carbs.trimmed(1); fat = f.fat.trimmed(1); loaded = true
            }
        }
    }

    val p = protein.toNumberOrNull() ?: 0.0
    val cb = carbs.toNumberOrNull() ?: 0.0
    val ft = fat.toNumberOrNull() ?: 0.0
    val fromMacros = Macros.kcalFrom(p, cb, ft)
    val energy = kcal.toNumberOrNull() ?: fromMacros.takeIf { it > 0 }
    val valid = name.isNotBlank() && energy != null && serving.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (foodId != null) "Edit food" else "New food") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete food") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(brand, { brand = it }, label = { Text("Brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField(barcode, { barcode = it }, "Barcode (optional)", decimal = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(serving, { serving = it }, label = { Text("Serving") }, singleLine = true, modifier = Modifier.weight(1.4f))
                NumberField(grams, { grams = it }, "Weight", Modifier.weight(1f), suffix = "g",
                    supportingText = "Optional; enables g/oz")
            }
            Text("Nutrition per serving", style = MaterialTheme.typography.titleSmall)
            NumberField(kcal, { kcal = it }, "Calories", suffix = "kcal",
                supportingText = if (kcal.isBlank() && fromMacros > 0) "From macros: ${fromMacros.kcal()} kcal" else null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(carbs, { carbs = it }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(fat, { fat = it }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            Button(
                enabled = valid,
                onClick = {
                    scope.launch {
                        val base = original
                        val entity = FoodEntity(
                            id = base?.id ?: 0, source = FoodSource.CUSTOM.name, sourceId = base?.sourceId ?: "",
                            name = name.trim(), brand = brand.trim().ifEmpty { null },
                            barcode = barcode.filter { it.isDigit() }.ifEmpty { null },
                            servingLabel = serving.trim(), servingGrams = grams.toNumberOrNull()?.takeIf { it > 0 },
                            kcal = energy!!, protein = p, carbs = cb, fat = ft,
                            isFavorite = base?.isFavorite ?: false, lastUsedAt = base?.lastUsedAt ?: 0, useCount = base?.useCount ?: 0,
                        )
                        onSaved(c.foods.saveCustom(entity))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save food") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this food?") },
        text = { Text("Entries you already logged stay in your diary.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                original?.let { f -> scope.launch { c.foods.delete(f); onDeleted() } }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
