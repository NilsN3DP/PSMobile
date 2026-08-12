package de.psmobile.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Zeigt einen Text als QR-Code - Gegenstueck zu `QRCodeView` auf iOS.
 *
 * iOS bringt mit CIFilter einen Erzeuger mit, Android nicht: ML Kit
 * liest Strichcodes, schreibt aber keine. Deshalb zxing, und nur dafuer.
 *
 * Auf weissem Grund und mit einem Rand, auch im dunklen Thema. Ein
 * QR-Code lebt vom Kontrast, und viele Kameras finden ihn ohne die
 * ruhige Zone am Rand schlicht nicht.
 */
@Composable
fun QrCodeBild(inhalt: String, modifier: Modifier = Modifier, kantePx: Int = 512) {
    val bild = remember(inhalt, kantePx) { erzeugen(inhalt, kantePx) } ?: return
    Image(
        bitmap = bild.asImageBitmap(),
        contentDescription = null,
        modifier = modifier.background(Color.White),
        contentScale = ContentScale.Fit,
        // Ohne das glaettet Compose die harten Kanten weich - fuer ein
        // Foto richtig, fuer einen Code das Gegenteil von hilfreich.
        filterQuality = FilterQuality.None,
    )
}

private fun erzeugen(inhalt: String, kantePx: Int): Bitmap? = runCatching {
    val hinweise = mapOf(
        // M vertraegt rund 15 % Verlust - genug fuer einen Bildschirm,
        // ohne den Code unnoetig dicht zu machen.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(inhalt, BarcodeFormat.QR_CODE, kantePx, kantePx, hinweise)
    val bmp = createBitmap(matrix.width, matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            bmp[x, y] = if (matrix.get(x, y)) android.graphics.Color.BLACK
            else android.graphics.Color.WHITE
        }
    }
    bmp
}.getOrNull()

private operator fun Bitmap.set(x: Int, y: Int, farbe: Int) = setPixel(x, y, farbe)
