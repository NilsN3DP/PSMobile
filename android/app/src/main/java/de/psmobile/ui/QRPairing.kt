package de.psmobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import de.psmobile.shared.rules.SimpleModeState
import de.psmobile.ui.theme.PrusaColors
import java.util.concurrent.Executors

/*
 * QR-Pairing fuer Remote Slicing - Gegenstueck zu
 * `ios/PSMobile/UI/QRPairing.swift`.
 *
 * Ein Geraet zeigt seinen eingerichteten Server als QR-Code
 * (QRCodeView), ein zweites scannt ihn (QRScanSheet) und uebernimmt
 * Adresse und Token, ohne beides abzutippen.
 *
 * Das Format `psmobile-remote://pair?host=<Adresse>&token=<Token>` steht
 * im gemeinsamen Modul (`de.psmobile.shared.rules.RemotePairing`) und
 * wird hier nicht noch einmal definiert.
 */

/**
 * Zeigt den eigenen Server als QR-Code - fuer ein zweites Geraet zum
 * Abscannen. Das Bild erzeugt [QrCodeBild] (zxing); hier nur die
 * Huelle mit Kennung und dem Ersatztext, wenn sich nichts erzeugen
 * laesst.
 */
@Composable
fun QRCodeView(inhalt: String, modifier: Modifier = Modifier) {
    val erzeugbar = remember(inhalt) {
        runCatching { QRCodeWriter().encode(inhalt, BarcodeFormat.QR_CODE, 1, 1) }.isSuccess
    }
    if (erzeugbar) {
        QrCodeBild(inhalt = inhalt, modifier = modifier.testTag("remote.qr.anzeige"))
    } else {
        Text(st("QR code could not be generated", "QR-Code lässt sich nicht erzeugen"), color = PrusaColors.textMuted, modifier = modifier)
    }
}

/**
 * Kamera-Scanner fuer einen einzelnen QR-Code - Gegenstueck zu
 * `QRScannerView`. CameraX + ML Kit, dieselbe Route wie der Scanner
 * der lokalen Kopplung in `QrScanner.kt`; dessen Kamera-Composable ist
 * dort privat und traegt eigene Beschriftungen, deshalb hier die
 * Kamera ohne Chrome noch einmal.
 */
@Composable
private fun QRScannerView(onFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onFoundState = rememberUpdatedState(onFound)
    var found by remember { mutableStateOf(false) }

    // iOS fragt beim ersten Kamerazugriff automatisch nach - Android
    // muss die Berechtigung ausdruecklich holen. Ohne sie bleibt die
    // Flaeche schwarz, wie auf iOS ohne Freigabe.
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    DisposableEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }
    if (!hasPermission) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    // Kamera, Analyse-Thread und ML-Kit-Client haengen am Lebenslauf
    // dieser Composable - das iOS-Gegenstueck raeumt in
    // viewWillDisappear auf.
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
                            val value = barcodes
                                .firstOrNull { it.format == Barcode.FORMAT_QR_CODE && it.rawValue != null }
                                ?.rawValue
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
                    // Kamera bereits gebunden oder nicht verfuegbar.
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
    )
}

/**
 * Das Scanner-Blatt mit Rahmen und Abbrechen-Knopf - die reine Kamera-
 * vorschau allein wirkt ohne jede Fuehrung wie ein Programmierfehler.
 * Gegenstueck zu `QRScanSheet`.
 */
@Composable
fun QRScanSheet(
    onCode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val ps = LocalPsScale.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        QRScannerView { code -> onCode(code) }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(ps.pt(220))
                    .border(2.dp, Color.White, RoundedCornerShape(ps.pt(16))),
            )
            Spacer(Modifier.weight(1f))
            Text(
                SimpleModeState.text(
                    "Point the camera at the QR code shown on the other device.",
                    "Kamera auf den QR-Code des anderen Geräts halten.",
                ),
                fontSize = ps.font(13),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = ps.pt(24))
                    .padding(bottom = ps.pt(12)),
            )
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.25f),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .padding(bottom = ps.pt(24))
                    .testTag("remote.qr.abbrechen"),
            ) {
                Text(SimpleModeState.text("Cancel", "Abbrechen"))
            }
        }
    }
}
