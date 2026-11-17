package com.ecoloop.app.feature.activity.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layar penuh untuk memindai QR serah terima.
 * Meminta izin kamera, lalu memakai CameraX + ML Kit untuk membaca token.
 */
@Composable
fun QrScannerDialog(
    onTokenScanned: (String) -> Unit,
    onManualEntry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var permission by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                CameraPermission.GRANTED
            } else {
                CameraPermission.UNKNOWN
            },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = if (granted) CameraPermission.GRANTED else CameraPermission.DENIED
    }
    LaunchedEffect(Unit) {
        if (permission == CameraPermission.UNKNOWN) launcher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when (permission) {
                CameraPermission.GRANTED -> CameraPreview(onTokenScanned)
                CameraPermission.DENIED -> PermissionDenied(
                    onRetry = { launcher.launch(Manifest.permission.CAMERA) },
                    onManualEntry = onManualEntry,
                )
                CameraPermission.UNKNOWN -> Unit
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White)
            }

            if (permission == CameraPermission.GRANTED) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Arahkan kamera ke QR serah terima di HP penerima",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onManualEntry) {
                        Text("Masukkan token manual", color = Color.White)
                    }
                }
            }
        }
    }
}

private enum class CameraPermission { UNKNOWN, GRANTED, DENIED }

@Composable
private fun CameraPreview(onTokenScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onScanned by rememberUpdatedState(onTokenScanned)
    val previewView = remember {
        // TextureView (COMPATIBLE) agar preview tampil benar di dalam window Dialog.
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    DisposableEffect(lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val delivered = AtomicBoolean(false)
        val disposed = AtomicBoolean(false)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            if (disposed.get()) return@addListener
            val cameraProvider = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { image ->
                analyze(image, scanner) { value ->
                    if (delivered.compareAndSet(false, true)) {
                        ContextCompat.getMainExecutor(context).execute { onScanned(value) }
                    }
                }
            }
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            provider?.unbindAll()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // Bingkai bidik
        Box(
            Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(3.dp, Color.White, RoundedCornerShape(24.dp)),
        )
    }
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(image: ImageProxy, scanner: BarcodeScanner, onValue: (String) -> Unit) {
    val mediaImage = image.image
    if (mediaImage == null) {
        image.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(onValue)
        }
        .addOnCompleteListener { image.close() }
}

@Composable
private fun PermissionDenied(onRetry: () -> Unit, onManualEntry: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.NoPhotography,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            "Izin kamera diperlukan",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Izinkan kamera untuk memindai QR, atau masukkan token secara manual.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Izinkan kamera") }
        OutlinedButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Masukkan token manual")
        }
    }
}
