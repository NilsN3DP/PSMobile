package de.psmobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import de.psmobile.ui.theme.PrusaColors
import java.util.concurrent.Executors

/**
 * Vollflaechiger QR-Scanner fuer die experimentelle lokale PrusaLink-Kopplung.
 *
 * Gegenstueck zu QRScanSheet/QRScannerView auf iOS. Liefert den rohen
 * Text des erkannten Codes zurueck; was das bedeutet, entscheidet der
 * Aufrufer (LocalPrusaLinkPairing.parse), damit dieser Scanner selbst
 * nichts vom Zahlungsformat wissen muss.
 */
@Composable
fun QrScanSheet(
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    DisposableEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
        if (hasPermission) {
            QrCameraPreview(onFound = onCode)
            ScannerOverlay()
        } else {
            Text(
                PsUi.appText("Camera access is needed to scan the printer’s QR code.", "Kamera-Zugriff wird benötigt, um den QR-Code des Druckers zu scannen."),
                color = PrusaColors.TextPrimary, fontSize = 14.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            OutlinedButton(onClick = onCancel) { Text(PsUi.appText("Cancel", "Abbrechen")) }
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Box(
        Modifier.size(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Transparent),
    )
    Text(
        PsUi.appText("Hold the printer’s QR code inside the frame", "QR-Code des Druckers in den Rahmen halten"),
        color = Color.White, fontSize = 13.sp,
        modifier = Modifier.padding(top = 320.dp),
    )
}

@Composable
private fun QrCameraPreview(onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFoundState = rememberUpdatedState(onFound)
    var found by remember { mutableStateOf(false) }

    // Kamera, Analyse-Thread und ML-Kit-Client haengen am Lebenslauf
    // dieser Composable, nicht an dem der Activity. Ohne das lief nach
    // dem Schliessen des Scanners die Kamera weiter (Kontrollleuchte an,
    // Bilder in eine laengst abgehaengte Vorschau), und jeder erneute
    // Aufruf legte einen weiteren Executor samt Scanner-Client oben
    // drauf. Das iOS-Gegenstueck raeumt in viewWillDisappear auf.
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val provider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { provider.value?.unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                provider.value = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null || found) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(image)
                        .addOnSuccessListener { barcodes: List<Barcode> ->
                            val value = barcodes.firstOrNull { it.rawValue != null }?.rawValue
                            if (value != null && !found) {
                                found = true
                                onFoundState.value(value)
                            }
                        }
                        .addOnCompleteListener { imageProxy.close() }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                    )
                } catch (_: Exception) {
                    // Kamera bereits gebunden oder nicht verfuegbar - der Nutzer
                    // faellt dann auf die manuelle JSON-Eingabe zurueck.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}
