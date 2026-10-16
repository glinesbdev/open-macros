package dev.glinesb.openmacros.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.BuildConfig
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.HeightUnit
import dev.glinesb.openmacros.domain.MacroSplit
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.ui.components.NumberField
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onEditProfile: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val hasKey by c.keys.hasUsdaKey.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var pPct by rememberSaveable(settings.macroSplit) { mutableStateOf(settings.macroSplit.proteinPct.toString()) }
    var cPct by rememberSaveable(settings.macroSplit) { mutableStateOf(settings.macroSplit.carbsPct.toString()) }
    var fPct by rememberSaveable(settings.macroSplit) { mutableStateOf(settings.macroSplit.fatPct.toString()) }
    var override by rememberSaveable(settings.calorieOverride) { mutableStateOf(settings.calorieOverride?.toString().orEmpty()) }
    var keyInput by rememberSaveable { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }

    fun report(block: suspend () -> String) = scope.launch {
        val msg = try { block() } catch (e: Exception) { "Failed: ${e.message ?: e.javaClass.simpleName}" }
        snackbar.showSnackbar(msg)
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { report { c.backup.exportJson(it); "Backup saved" } }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        u?.let { report { c.backup.exportCsv(it); "CSV saved" } }
    }
    val importDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> pendingImport = u }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Daily targets") {
                    val m = settings.macroTargets
                    Text("${settings.calorieTarget} kcal · P ${m.protein} g · C ${m.carbs} g · F ${m.fat} g",
                        style = MaterialTheme.typography.titleMedium)
                    settings.computedCalories?.let {
                        Text(if (settings.calorieOverride != null) "Custom target (calculated: $it kcal)" else "Calculated from your profile",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = onEditProfile) { Text("Edit profile & goal") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NumberField(override, { override = it }, "Custom calorie target", Modifier.weight(1f), suffix = "kcal",
                            decimal = false, supportingText = "Leave blank to use the calculated target")
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val v = override.toIntOrNull()?.takeIf { it in 800..10000 }
                            c.settings.update { it.copy(calorieOverride = v) }
                            if (v == null) override = ""
                        }) { Text("Apply") }
                    }
                }
            }
            item {
                Section("Macro split") {
                    val split = MacroSplit(pPct.toIntOrNull() ?: -1, cPct.toIntOrNull() ?: -1, fPct.toIntOrNull() ?: -1)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(pPct, { pPct = it }, "Protein", Modifier.weight(1f), suffix = "%", decimal = false)
                        NumberField(cPct, { cPct = it }, "Carbs", Modifier.weight(1f), suffix = "%", decimal = false)
                        NumberField(fPct, { fPct = it }, "Fat", Modifier.weight(1f), suffix = "%", decimal = false)
                    }
                    val sum = (pPct.toIntOrNull() ?: 0) + (cPct.toIntOrNull() ?: 0) + (fPct.toIntOrNull() ?: 0)
                    if (split.isValid) {
                        val g = split.grams(settings.calorieTarget)
                        Text("= ${g.protein} g protein, ${g.carbs} g carbs, ${g.fat} g fat", style = MaterialTheme.typography.bodySmall)
                    } else Text("Must add up to 100% (now $sum%)", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        Button(enabled = split.isValid && split != settings.macroSplit,
                            onClick = { c.settings.update { it.copy(macroSplit = split) } }) { Text("Save split") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { c.settings.update { it.copy(macroSplit = MacroSplit.DEFAULT) } }) { Text("Reset to 30/40/30") }
                    }
                }
            }
            item {
                Section("Units") {
                    Text("Weight", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = settings.weightUnit == u, onClick = { c.settings.update { it.copy(weightUnit = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(u.symbol) }
                        }
                    }
                    Text("Height", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        HeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = settings.heightUnit == u, onClick = { c.settings.update { it.copy(heightUnit = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == HeightUnit.CM) "cm" else "ft / in") }
                        }
                    }
                }
            }
            item {
                Section("USDA FoodData Central key") {
                    Text(
                        if (hasKey) "Your key is saved (encrypted on this device)."
                        else "No key saved, so USDA search uses the shared DEMO_KEY, which allows only about 30 searches an hour " +
                            "and 50 a day. A personal key is free and allows 1,000 an hour.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!c.keys.isAvailable) Text("Encrypted storage isn't available on this device, so a key can't be saved.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = keyInput, onValueChange = { keyInput = it.trim() },
                        label = { Text(if (hasKey) "Replace key" else "API key") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Button(enabled = keyInput.isNotBlank() && c.keys.isAvailable,
                            onClick = { c.keys.usdaKey = keyInput; keyInput = "" }) { Text("Save key") }
                        Spacer(Modifier.width(8.dp))
                        if (hasKey) TextButton(onClick = { c.keys.usdaKey = null }) { Text("Remove") }
                        TextButton(onClick = { uri.openUri("https://fdc.nal.usda.gov/api-key-signup") }) { Text("Get a free key") }
                    }
                }
            }
            item {
                Section("Your data") {
                    Text("Everything is stored only on this phone. There is no account and nothing is uploaded. " +
                        "Export a backup to move phones or keep a copy. Backups never include your API key.",
                        style = MaterialTheme.typography.bodySmall)
                    val stamp = LocalDate.now().toString()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { exportJson.launch("open-macros-$stamp.json") }) { Text("Export JSON") }
                        FilledTonalButton(onClick = { exportCsv.launch("open-macros-$stamp.csv") }) { Text("Export CSV") }
                    }
                    OutlinedButton(onClick = { importDoc.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Text("Import JSON or CSV…")
                    }
                }
            }
            item {
                Section("Photo logging") {
                    Text("Coming next: snap a meal and get an editable calorie estimate using your own xAI key. Not available yet.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Section("About") {
                    Text("Open Macros ${BuildConfig.VERSION_NAME}. Free and open source (PolyForm Noncommercial 1.0.0).",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Food data: Open Food Facts, © Open Food Facts contributors, available under the Open Database License (ODbL 1.0). " +
                        "USDA FoodData Central, U.S. Department of Agriculture, public domain.",
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick = { uri.openUri("https://world.openfoodfacts.org") }) { Text("Open Food Facts") }
                        TextButton(onClick = { uri.openUri("https://opendatacommons.org/licenses/odbl/1-0/") }) { Text("ODbL") }
                        TextButton(onClick = { uri.openUri("https://github.com/glinesbdev/open-macros") }) { Text("Source") }
                    }
                    Text("Calorie targets are estimates, not medical advice.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pendingImport?.let { u ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace your data?") },
            text = { Text("Importing replaces your food diary and weigh-ins. A JSON backup also replaces your foods and settings. " +
                "Export a backup first if you're unsure.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    report { val s = c.backup.import(u); "Imported ${s.entries} entries, ${s.weights} weigh-ins, ${s.foods} foods" }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
