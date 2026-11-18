package fit.sigap.app.core.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.device.appSettingsIntent
import fit.sigap.app.core.device.openSettings
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QrScanner(
    description: String,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = SigapTheme.colors
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        denied = !result
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(colors.ink950)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (LocalInspectionMode.current) {
            FinderCorners()
        } else if (granted) {
            CameraPreview(onResult)
            FinderCorners()
        } else if (denied) {
            Column(
                modifier = Modifier.padding(Space.x5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                Text(
                    text = "Kamera tidak diizinkan. Izinkan kamera untuk SIGAP di pengaturan HP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.paper0,
                    textAlign = TextAlign.Center,
                )
                SigapButton(
                    text = "Buka pengaturan",
                    onClick = { context.openSettings(context.appSettingsIntent()) },
                    variant = ButtonVariant.OnSignal,
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val delivered = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor) { image ->
                    analyze(image, scanner) { raw ->
                        if (delivered.compareAndSet(false, true)) scope.launch { currentOnResult(raw) }
                    }
                }
            }
        val job = scope.launch {
            val camera = ProcessCameraProvider.awaitInstance(context)
            provider = camera
            val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
            camera.unbindAll()
            camera.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
        onDispose {
            job.cancel()
            provider?.unbindAll()
            analysis.clearAnalyzer()
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyze(
    image: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onFound: (String) -> Unit,
) {
    val media = image.image
    if (media == null) {
        image.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
        .addOnSuccessListener { barcodes -> barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onFound) }
        .addOnCompleteListener { image.close() }
}

@Composable
private fun FinderCorners() {
    val paper = SigapTheme.colors.paper0
    Canvas(modifier = Modifier.fillMaxSize().padding(Space.x12)) {
        val arm = size.minDimension * 0.14f
        val stroke = 4.dp.toPx()
        val corners = listOf(
            Offset(0f, 0f) to Offset(1f, 1f),
            Offset(size.width, 0f) to Offset(-1f, 1f),
            Offset(0f, size.height) to Offset(1f, -1f),
            Offset(size.width, size.height) to Offset(-1f, -1f),
        )
        corners.forEach { (corner, direction) ->
            drawLine(paper, corner, corner + Offset(arm * direction.x, 0f), stroke, StrokeCap.Round)
            drawLine(paper, corner, corner + Offset(0f, arm * direction.y), stroke, StrokeCap.Round)
        }
    }
}
