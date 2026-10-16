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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.glinesb.openmacros.BuildConfig
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.domain.HeightUnit
import dev.glinesb.openmacros.domain.MacroSplit
import dev.glinesb.openmacros.domain.WeightUnit
import dev.glinesb.openmacros.ui.components.ApiKeyHelp
import dev.glinesb.openmacros.ui.components.KeyLinks
import dev.glinesb.openmacros.ui.components.NumberField
import dev.glinesb.openmacros.ui.components.openLink
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onEditProfile: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val hasKey by c.keys.hasUsdaKey.collectAsStateWithLifecycle()
    val hasXaiKey by c.keys.hasXaiKey.collectAsStateWithLifecycle()
    var xaiInput by rememberSaveable { mutableStateOf("") }
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
    val exportSetsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        u?.let { report { c.backup.exportTrainingCsv(it); "Training CSV saved" } }
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
                    Text("Body weight", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = settings.weightUnit == u, onClick = { c.settings.update { it.copy(weightUnit = u) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(u.symbol) }
                        }
                    }
                    Text("Training weights (default; switch any exercise in a workout)", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(selected = settings.trainingUnit == u, onClick = { c.settings.update { it.copy(trainingUnit = u) } },
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
                    }
                    OutlinedButton(onClick = { openLink(context, KeyLinks.USDA_SIGNUP) }) { Text("Get a free key") }
                    ApiKeyHelp(
                        steps = listOf(
                            "Tap \"Get a free key\" and fill in your name and email on the api.data.gov sign-up form.",
                            "Check your email: the key arrives within a minute or so (no account or password needed).",
                            "Copy the key from the email.",
                            "Paste it in the field above and tap Save key.",
                        ),
                        costNote = "Free. A personal key allows about 1,000 searches an hour; without one the shared DEMO_KEY allows about 30.",
                        docsLabel = "FoodData Central API docs",
                        docsUrl = KeyLinks.USDA_DOCS,
                    )
                }
            }
            item {
                Section("Your data") {
                    Text("Your diary, foods, weigh-ins and workouts are stored only on this phone. There is no account and they are never uploaded. " +
                        "Export a backup to move phones or keep a copy. Backups never include your API keys.",
                        style = MaterialTheme.typography.bodySmall)
                    val stamp = LocalDate.now().toString()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { exportJson.launch("open-macros-$stamp.json") }) { Text("Export JSON") }
                        FilledTonalButton(onClick = { exportCsv.launch("open-macros-$stamp.csv") }) { Text("Export CSV") }
                    }
                    FilledTonalButton(onClick = { exportSetsCsv.launch("open-macros-training-sets-$stamp.csv") }) { Text("Export training sets (CSV)") }
                    OutlinedButton(onClick = { importDoc.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                        Text("Import JSON or CSV…")
                    }
                }
            }
            item {
                Section("Photo logging (optional, xAI)") {
                    Text(
                        "Take or pick a photo of your meal and Grok estimates the foods, grams and macros for you to review " +
                            "before logging. This uses your own xAI API key: every photo is a paid request billed to your xAI " +
                            "account (the review screen shows what each one cost). Photos are sent to xAI only when you use " +
                            "this feature, and Open Macros doesn't keep them.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(if (hasXaiKey) "Your xAI key is saved (encrypted on this device). Photo logging is on."
                        else "No xAI key saved. Photo logging is off.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = xaiInput, onValueChange = { xaiInput = it.trim() },
                        label = { Text(if (hasXaiKey) "Replace xAI key" else "xAI API key") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        Button(enabled = xaiInput.isNotBlank() && c.keys.isAvailable,
                            onClick = { c.keys.xaiKey = xaiInput; xaiInput = "" }) { Text("Save key") }
                        Spacer(Modifier.width(8.dp))
                        if (hasXaiKey) TextButton(onClick = { c.keys.xaiKey = null }) { Text("Remove") }
                    }
                    OutlinedButton(onClick = { openLink(context, KeyLinks.XAI_API_KEYS) }) { Text("Get a key") }
                    ApiKeyHelp(
                        steps = xaiKeySteps,
                        costNote = "Paid: each photo estimate is billed per request (per token) to your own xAI account, " +
                            "so you need credits loaded. Open Macros adds no fee.",
                        docsLabel = "xAI API docs (docs.x.ai)",
                        docsUrl = KeyLinks.XAI_DOCS,
                    )
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
                        TextButton(onClick = { openLink(context, "https://world.openfoodfacts.org") }) { Text("Open Food Facts") }
                        TextButton(onClick = { openLink(context, "https://opendatacommons.org/licenses/odbl/1-0/") }) { Text("ODbL") }
                        TextButton(onClick = { openLink(context, "https://github.com/glinesbdev/open-macros") }) { Text("Source") }
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
                    report { val s = c.backup.import(u); "Imported ${s.entries} entries, ${s.weights} weigh-ins, ${s.foods} foods" + if (s.workouts > 0) ", ${s.workouts} workouts" else "" }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}

private val xaiKeySteps = listOf(
    "Sign up or log in at console.x.ai and verify your email.",
    "Add credits under Billing (the API is pay-as-you-go).",
    "Open API Keys (\"Get a key\" goes straight there) and create a new key.",
    "Copy the key (it's shown only once), paste it in the field above and tap Save key.",
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
