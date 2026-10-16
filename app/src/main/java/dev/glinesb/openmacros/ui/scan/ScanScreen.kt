package dev.glinesb.openmacros.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.glinesb.openmacros.container
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private sealed interface ScanState {
    data object Scanning : ScanState
    data class LookingUp(val code: String) : ScanState
    data class NotFound(val code: String) : ScanState
    data class Failed(val code: String, val message: String) : ScanState
}

/** Barcode → Open Food Facts lookup. Not found → offer a custom food with the barcode pre-filled. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onFound: (Long) -> Unit, onCreate: (String) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScanState>(ScanState.Scanning) }
    var manual by remember { mutableStateOf("") }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    fun lookup(code: String) {
        if (state is ScanState.LookingUp) return
        state = ScanState.LookingUp(code)
        scope.launch {
            state = try {
                val food = c.foods.lookupBarcode(code)
                if (food != null) { onFound(food.id); return@launch }
                ScanState.NotFound(code)
            } catch (e: Exception) {
                ScanState.Failed(code, "Lookup failed (${e.message ?: e.javaClass.simpleName}). Check your connection.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan barcode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    BarcodeCamera(onBarcode = { if (state is ScanState.Scanning) lookup(it) }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission is needed to scan. You can still type the barcode below.")
                        TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                    }
                }
            }
            when (val s = state) {
                ScanState.Scanning -> Text("Point the camera at a product barcode.", style = MaterialTheme.typography.bodyMedium)
                is ScanState.LookingUp -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text("Looking up ${s.code}…")
                }
                is ScanState.NotFound -> ResultCard(
                    "Barcode ${s.code} isn't in Open Food Facts yet. Add it as a custom food.",
                    "Create food", { onCreate(s.code) }, { state = ScanState.Scanning },
                )
                is ScanState.Failed -> ResultCard(s.message, "Retry", { state = ScanState.Scanning; lookup(s.code) }, { state = ScanState.Scanning })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = manual, onValueChange = { v -> manual = v.filter { it.isDigit() } },
                    label = { Text("Or type the barcode") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (manual.length >= 6) lookup(manual) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(enabled = manual.length >= 6, onClick = { lookup(manual) }) { Text("Look up") }
            }
            Text("Product data from Open Food Facts (ODbL).", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultCard(text: String, action: String, onAction: () -> Unit, onScanAgain: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onScanAgain) { Text("Scan again") }
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}

/** CameraX preview + ML Kit (bundled on-device model) barcode analysis. */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun BarcodeCamera(onBarcode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) { proxy.close(); return@setAnalyzer }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let { currentOnBarcode(it) } }
                    .addOnCompleteListener { proxy.close() }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }
    AndroidView(factory = { previewView }, modifier = modifier)
}
