package de.psmobile.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import de.psmobile.shared.rules.RemotePairing
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der erzeugte QR-Code wird von genau dem Leser zurueckgelesen, den die
 * App zum Scannen benutzt.
 *
 * Dass die Zeichenkette den Weg durch `RemotePairing` heil uebersteht,
 * pruefen die Unit-Tests im gemeinsamen Modul. Was die dort nicht
 * pruefen koennen: ob aus der Zeichenkette echte Pixel werden, die ein
 * Scanner auch wieder versteht. Ein Code, der nur schoen aussieht, hilft
 * niemandem vor der Kamera.
 */
@RunWith(AndroidJUnit4::class)
class KopplungscodeTest {

    private fun erzeugen(inhalt: String, kante: Int = 600): Bitmap {
        val hinweise = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(inhalt, BarcodeFormat.QR_CODE, kante, kante, hinweise)
        val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }

    private fun lesen(bild: Bitmap): String? {
        var ergebnis: String? = null
        val fertig = CountDownLatch(1)
        val scanner = BarcodeScanning.getClient()
        scanner.process(InputImage.fromBitmap(bild, 0))
            .addOnSuccessListener { codes ->
                ergebnis = codes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                fertig.countDown()
            }
            .addOnFailureListener { fertig.countDown() }
        // Der Leser laedt sein Modell beim ersten Aufruf nach - grosszuegig
        // warten, sonst schlaegt der Test beim ersten Lauf fehl und danach
        // nie wieder, was schlimmer ist als ein langsamer Test.
        assertTrue("Der Leser hat nicht geantwortet", fertig.await(30, TimeUnit.SECONDS))
        return ergebnis
    }

    @Test
    fun kopplungscodeUeberstehtDenWegDurchDiePixel() {
        val erwartet = RemotePairing.url("192.168.1.50:8420", "geheim-token")
        assertEquals(erwartet, lesen(erzeugen(erwartet)))
    }

    @Test
    fun auchMitSonderzeichenImToken() {
        // Genau hier zeigt sich, ob die Prozentkodierung durchhaelt: ein
        // & im Token wuerde eine naiv gebaute URL entzweischneiden.
        val erwartet = RemotePairing.url("http://drücker.local:8420", "a+b&c=d/e f")
        val gelesen = lesen(erzeugen(erwartet))
        assertEquals(erwartet, gelesen)

        val zurueck = RemotePairing.parse(gelesen!!)
        assertEquals("http://drücker.local:8420", zurueck?.host)
        assertEquals("a+b&c=d/e f", zurueck?.token)
    }
}
