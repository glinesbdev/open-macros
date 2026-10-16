package dev.glinesb.openmacros.ui.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.glinesb.openmacros.AppContainer
import dev.glinesb.openmacros.R
import dev.glinesb.openmacros.container
import dev.glinesb.openmacros.data.SearchOutcome
import dev.glinesb.openmacros.data.db.LogEntryEntity
import dev.glinesb.openmacros.domain.AmountUnit
import dev.glinesb.openmacros.domain.FoodItem
import dev.glinesb.openmacros.domain.Macros
import dev.glinesb.openmacros.domain.Meal
import dev.glinesb.openmacros.photo.ConfidenceLevel
import dev.glinesb.openmacros.photo.FoodPhoto
import dev.glinesb.openmacros.photo.ImagePrep
import dev.glinesb.openmacros.photo.PhotoEstimateException
import dev.glinesb.openmacros.photo.ReviewItem
import dev.glinesb.openmacros.photo.confidenceLevel
import dev.glinesb.openmacros.ui.components.NumberField
import dev.glinesb.openmacros.ui.components.kcal
import dev.glinesb.openmacros.ui.components.toNumberOrNull
import dev.glinesb.openmacros.ui.components.trimmed
import dev.glinesb.openmacros.ui.food.FoodRow
import dev.glinesb.openmacros.ui.food.MealPicker
import kotlinx.coroutines.launch
import java.io.File

sealed interface PhotoStage {
    data object Capture : PhotoStage
    data object Preparing : PhotoStage
    data object Analyzing : PhotoStage
    data class Failed(val message: String, val canRetry: Boolean) : PhotoStage
    data object Review : PhotoStage
}

class PhotoLogViewModel(private val c: AppContainer, initialMeal: Meal) : ViewModel() {
    var stage by mutableStateOf<PhotoStage>(PhotoStage.Capture); private set
    var meal by mutableStateOf(initialMeal)
    var hint by mutableStateOf("")
    var notes by mutableStateOf<String?>(null); private set
    var costUsd by mutableStateOf<Double?>(null); private set
    val items = mutableStateListOf<ReviewItem>()
    /** Kept in memory only, so "Try again" doesn't need a new photo. Never written to disk. */
    private var jpeg: ByteArray? = null

    fun fromFile(file: File) = prepare { ImagePrep.fromFile(file).also { file.delete() } }
    fun fromUri(uri: Uri, resolver: android.content.ContentResolver) = prepare { ImagePrep.fromUri(resolver, uri) }
    fun fail(message: String) { stage = PhotoStage.Failed(message, canRetry = false) }

    private fun prepare(load: suspend () -> ByteArray) {
        stage = PhotoStage.Preparing
        viewModelScope.launch {
            jpeg = try { load() } catch (e: Exception) {
                stage = PhotoStage.Failed("Couldn't read that photo (${e.javaClass.simpleName})", canRetry = false); return@launch
            }
            analyze()
        }
    }

    fun analyze() {
        val bytes = jpeg ?: return
        stage = PhotoStage.Analyzing
        viewModelScope.launch {
            stage = try {
                val est = c.photoEstimator.estimate(FoodPhoto(bytes, hint = hint))
                items.clear(); items.addAll(est.items.map(ReviewItem::from))
                notes = est.notes; costUsd = est.costUsd
                PhotoStage.Review
            } catch (e: PhotoEstimateException) {
                PhotoStage.Failed(e.message ?: "Estimate failed", canRetry = true)
            }
        }
    }

    fun retake() { jpeg = null; items.clear(); stage = PhotoStage.Capture }
    fun update(i: Int, item: ReviewItem) { if (i in items.indices) items[i] = item }
    fun remove(i: Int) { if (i in items.indices) items.removeAt(i) }

    suspend fun search(q: String): SearchOutcome = c.foods.search(q)

    fun swap(i: Int, food: FoodItem) = viewModelScope.launch {
        val id = c.foods.cache(food)
        if (i in items.indices) items[i] = items[i].swappedFor(food, id)
    }

    fun log(day: Long, done: () -> Unit) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        items.filter { it.include }.forEachIndexed { n, it ->
            val food = it.foodId?.let { id -> c.foods.byId(id) }
            val g = it.grams
            val sg = food?.servingGrams
            val (amount, unit, servings) = when {
                food != null && g != null && sg != null && sg > 0 -> Triple(g, AmountUnit.GRAM, g / sg)
                food != null -> Triple(1.0, AmountUnit.SERVING, 1.0)
                g != null -> Triple(g, AmountUnit.GRAM, 1.0)
                else -> Triple(1.0, AmountUnit.SERVING, 1.0)
            }
            c.log.add(
                LogEntryEntity(
                    epochDay = day, meal = meal.name, foodId = food?.id, name = it.name,
                    brand = if (food != null) food.brand else "Photo estimate",
                    amount = amount, unit = unit.name, servings = servings,
                    kcal = it.macros.kcal, protein = it.macros.protein, carbs = it.macros.carbs, fat = it.macros.fat,
                    createdAt = now + n,
                ),
            )
        }
        done()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoLogScreen(day: Long, initialMeal: Meal, onBack: () -> Unit, onLogged: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val vm = viewModel { PhotoLogViewModel(c, initialMeal) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.fromUri(it, context.contentResolver) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.stage == PhotoStage.Review) "Review estimate" else "Photo log") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (val s = vm.stage) {
                PhotoStage.Capture -> CaptureContent(vm, onGallery = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                PhotoStage.Preparing, PhotoStage.Analyzing -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(if (s == PhotoStage.Preparing) "Preparing photo…" else "Asking Grok what's on the plate…")
                    if (s == PhotoStage.Analyzing) Text("This can take 10–30 seconds.", style = MaterialTheme.typography.bodySmall)
                }
                is PhotoStage.Failed -> Column(
                    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::retake) { Text("New photo") }
                        if (s.canRetry) Button(onClick = vm::analyze) { Text("Try again") }
                    }
                }
                PhotoStage.Review -> ReviewContent(vm, onLog = { vm.log(day, onLogged) })
            }
        }
    }
}

@Composable
private fun CaptureContent(vm: PhotoLogViewModel, onGallery: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(android.Manifest.permission.CAMERA) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            if (granted) CameraPreview(capture, Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Allow the camera to take a photo, or pick one from your gallery.")
                TextButton(onClick = { permission.launch(android.Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }
        OutlinedTextField(
            value = vm.hint, onValueChange = { vm.hint = it.take(300) },
            label = { Text("Details (optional)") }, placeholder = { Text("e.g. cooked in 1 tbsp butter, large plate") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f)) { Text("Gallery") }
            Button(
                enabled = granted,
                onClick = {
                    val file = File(context.cacheDir, "photo-log-${System.currentTimeMillis()}.jpg")
                    capture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) = vm.fromFile(file)
                            override fun onError(e: ImageCaptureException) { file.delete(); vm.fail("Couldn't take the photo") }
                        },
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(painterResource(R.drawable.ic_photo_camera), null)
                Spacer(Modifier.width(8.dp))
                Text("Take photo")
            }
        }
        Text(
            "The photo is sent to xAI with your API key (billed to your xAI account) and isn't saved by Open Macros.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CameraPreview(capture: ImageCapture, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
private fun ReviewContent(vm: PhotoLogViewModel, onLog: () -> Unit) {
    var swapIndex by remember { mutableStateOf<Int?>(null) }
    val included = vm.items.filter { it.include }
    val total = included.fold(Macros.ZERO) { a, i -> a + i.macros }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AI estimates are approximate", style = MaterialTheme.typography.titleSmall)
                        Text("Check each portion, fix anything that looks off, or swap in a database match.",
                            style = MaterialTheme.typography.bodySmall)
                        vm.notes?.let { Text("Note: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                        vm.costUsd?.let {
                            Text("This estimate cost about $" + "%.3f".format(it) + " on your xAI account.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            itemsIndexed(vm.items, key = { _, it -> it.id }) { i, item ->
                ReviewItemCard(
                    item = item,
                    onChange = { vm.update(i, it) },
                    onRemove = { vm.remove(i) },
                    onSwap = { swapIndex = i },
                )
            }
            item { MealPicker(vm.meal) { vm.meal = it } }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("${total.kcal.kcal()} kcal · P ${total.protein.trimmed(0)} g · C ${total.carbs.trimmed(0)} g · F ${total.fat.trimmed(0)} g",
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::retake) { Text("Retake") }
                    Button(enabled = included.isNotEmpty(), onClick = onLog, modifier = Modifier.weight(1f)) {
                        Text("Log ${included.size} to ${vm.meal.label}")
                    }
                }
            }
        }
    }
    swapIndex?.let { i ->
        val item = vm.items.getOrNull(i)
        if (item == null) swapIndex = null
        else SwapDialog(item.name, vm, onDismiss = { swapIndex = null }, onPick = { food -> vm.swap(i, food); swapIndex = null })
    }
}

@Composable
private fun ReviewItemCard(item: ReviewItem, onChange: (ReviewItem) -> Unit, onRemove: () -> Unit, onSwap: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.include, onCheckedChange = { onChange(item.copy(include = it)) })
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                if (item.foodId != null) AssistChip(onClick = {}, label = { Text("Database") })
                else item.confidence?.let { ConfidenceBadge(it) }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove item") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncedNumberField(item.grams, "Weight", "g", Modifier.weight(1f)) { g -> onChange(item.withGrams(g)) }
                SyncedNumberField(item.macros.kcal, "Calories", "kcal", Modifier.weight(1f)) { v -> onChange(item.withMacros(item.macros.copy(kcal = v))) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncedNumberField(item.macros.protein, "Protein", "g", Modifier.weight(1f)) { v -> onChange(item.withMacros(item.macros.copy(protein = v))) }
                SyncedNumberField(item.macros.carbs, "Carbs", "g", Modifier.weight(1f)) { v -> onChange(item.withMacros(item.macros.copy(carbs = v))) }
                SyncedNumberField(item.macros.fat, "Fat", "g", Modifier.weight(1f)) { v -> onChange(item.withMacros(item.macros.copy(fat = v))) }
            }
            TextButton(onClick = onSwap) {
                Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text("Find in food database")
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(c: Float) {
    val level = confidenceLevel(c)
    val (bg, fg) = when (level) {
        ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ConfidenceLevel.LOW -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(8.dp)) {
        Text("${level.label} · ${(c * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

/** Number field that keeps the user's in-progress text but follows external changes (e.g. gram scaling). */
@Composable
private fun SyncedNumberField(value: Double?, label: String, suffix: String, modifier: Modifier, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(value?.trimmed(1).orEmpty()) }
    LaunchedEffect(value) {
        val shown = text.toNumberOrNull()
        if (value == null) { if (shown != null) text = "" }
        else if (shown == null || kotlin.math.abs(shown - value) > 0.05) text = value.trimmed(1)
    }
    NumberField(text, { t -> text = t; t.toNumberOrNull()?.let(onValue) }, label, modifier, suffix = suffix)
}

@Composable
private fun SwapDialog(initialQuery: String, vm: PhotoLogViewModel, onDismiss: () -> Unit, onPick: (FoodItem) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var outcome by remember { mutableStateOf<SearchOutcome?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun run() {
        loading = true
        scope.launch { try { outcome = vm.search(query) } finally { loading = false } }
    }
    LaunchedEffect(Unit) { run() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Find a database match") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = ::run) { Icon(Icons.Filled.Search, "Search") } })
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                outcome?.problems?.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    val list = outcome?.items.orEmpty()
                    itemsIndexed(list) { _, f ->
                        FoodRow(f.name, f.brand, f.source, f.servingLabel, f.perServing.kcal, "per serving") { onPick(f) }
                    }
                    if (!loading && outcome != null && list.isEmpty()) item { Text("No matches", modifier = Modifier.padding(8.dp)) }
                }
                Text("Keeps your estimated grams and uses the database's nutrition.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

