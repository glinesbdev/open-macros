package dev.glinesb.openmacros.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.res.painterResource
import dev.glinesb.openmacros.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glinesb.openmacros.AppContainer
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.SearchOutcome
import dev.glinesb.openmacros.data.db.FoodEntity
import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.FoodSource
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.ui.components.grams
import dev.glinesb.openmacros.ui.components.kcal
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    var outcome by mutableStateOf<SearchOutcome?>(null); private set
    var loading by mutableStateOf(false); private set
    private var job: Job? = null

    fun search(q: String) {
        if (q.isBlank()) { outcome = null; return }
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            try { outcome = c.foods.search(q) } finally { loading = false }
        }
    }

    fun clear() { job?.cancel(); outcome = null; loading = false }

    fun open(item: FoodItem, then: (Long) -> Unit) {
        viewModelScope.launch { then(c.foods.cache(item)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    meal: Meal,
    onBack: () -> Unit,
    onOpenFood: (Long) -> Unit,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickAdd: () -> Unit,
    onCreateFood: () -> Unit,
    onEditFood: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    val vm = viewModel { SearchViewModel(c) }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val recents by c.foods.recents.collectAsStateWithLifecycle(emptyList())
    val favorites by c.foods.favorites.collectAsStateWithLifecycle(emptyList())
    val custom by c.foods.customFoods.collectAsStateWithLifecycle(emptyList())
    val hasKey by c.keys.hasUsdaKey.collectAsStateWithLifecycle()
    val hasXaiKey by c.keys.hasXaiKey.collectAsStateWithLifecycle()
    var photoExplainer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add to ${meal.label}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onScan) { Icon(painterResource(R.drawable.ic_qr_code_scanner), "Scan barcode") }
                    IconButton(onClick = { if (hasXaiKey) onPhoto() else photoExplainer = true }) {
                        Icon(painterResource(R.drawable.ic_photo_camera), "Log from a photo")
                    }
                    IconButton(onClick = onQuickAdd) { Icon(painterResource(R.drawable.ic_bolt), "Quick add") }
                    IconButton(onClick = onCreateFood) { Icon(painterResource(R.drawable.ic_post_add), "Create food") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) vm.clear() },
                placeholder = { Text("Search foods") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.clear() }) { Icon(Icons.Filled.Clear, "Clear") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.search(query) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            val outcome = vm.outcome
            if (outcome != null) {
                LazyColumn(Modifier.weight(1f)) {
                    items(outcome.problems) { p ->
                        Text(p, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    if (outcome.items.isEmpty() && !vm.loading) item {
                        EmptyHint("No matches. Try fewer words, scan the barcode, or create a custom food.", onCreateFood)
                    }
                    items(outcome.items) { item ->
                        FoodRow(item.name, item.brand, item.source, item.servingLabel, item.perServing.kcal,
                            "P ${item.perServing.protein.grams()} · C ${item.perServing.carbs.grams()} · F ${item.perServing.fat.grams()}") {
                            vm.open(item, onOpenFood)
                        }
                    }
                    item { Attribution(usingDemoKey = !hasKey) }
                }
            } else {
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("Recent", "Favorites", "My foods").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
                val list = when (tab) { 0 -> recents; 1 -> favorites; else -> custom }
                LazyColumn(Modifier.weight(1f)) {
                    if (list.isEmpty()) item {
                        EmptyHint(
                            when (tab) {
                                0 -> "Foods you log show up here."
                                1 -> "Star a food to keep it here."
                                else -> "Foods you create show up here."
                            },
                            onCreateFood,
                        )
                    }
                    items(list, key = { it.id }) { f -> LocalFoodRow(f, onOpenFood, onEditFood.takeIf { f.isCustom }) }
                    item { Attribution(usingDemoKey = !hasKey) }
                }
            }
        }
    }
    if (photoExplainer) PhotoExplainer(onDismiss = { photoExplainer = false }, onOpenSettings = onOpenSettings)
}

@Composable
private fun PhotoExplainer(onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log from a photo") },
        text = {
            Text(
                "Snap your plate and Grok estimates the foods, portions and macros for you to review. " +
                    "This optional feature uses your own xAI API key: each photo is a paid request billed to your " +
                    "xAI account, and photos are sent to xAI only when you use it. Add a key in Settings to turn it on.",
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onOpenSettings() }) { Text("Open Settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun LocalFoodRow(f: FoodEntity, onOpen: (Long) -> Unit, onEdit: ((Long) -> Unit)?) {
    FoodRow(f.name, f.brand, f.foodSource, f.servingLabel, f.kcal,
        "P ${f.protein.grams()} · C ${f.carbs.grams()} · F ${f.fat.grams()}",
        trailing = onEdit?.let { { IconButton(onClick = { it(f.id) }) { Icon(Icons.Outlined.Edit, "Edit food") } } },
    ) { onOpen(f.id) }
}

@Composable
fun FoodRow(
    name: String, brand: String?, source: FoodSource, serving: String, kcal: Double, macros: String,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOfNotNull(brand, serving, macros).joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        overlineContent = { Text(source.label) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${kcal.kcal()} kcal", style = MaterialTheme.typography.labelLarge)
                trailing?.invoke()
            }
        },
    )
}

@Composable
private fun EmptyHint(text: String, onCreate: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onCreate) { Text("Create a custom food") }
    }
}

@Composable
fun Attribution(usingDemoKey: Boolean) {
    Column(Modifier.padding(16.dp)) {
        Text(
            "Food data from Open Food Facts (© Open Food Facts contributors, Open Database License) and " +
                "USDA FoodData Central (public domain). Values can be incomplete or wrong; check the label.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (usingDemoKey) Text(
            "USDA search is using the shared DEMO_KEY (about 30 searches an hour). Add your own free key in Settings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
