package de.psmobile.net

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Spiegelt gesicherte Projekte nach `Documents/PSMobile/Projects`, damit
 * sie in der Dateien-App und am USB-Kabel zu sehen sind.
 *
 * Warum: auf iOS liegen die Projekte in `Documents/Projects` und sind
 * ueber die Dateien-App sichtbar (`UIFileSharingEnabled`). Androids
 * Gegenstueck `getExternalFilesDir` ist seit Android 11 fuer Nutzer
 * praktisch unsichtbar. Die App arbeitet weiter mit ihrem eigenen
 * Ordner (dort liest sie), hier liegt nur eine Kopie fuer den Menschen.
 *
 * MediaStore statt Storage Access Framework: fuer eigene Dateien unter
 * Documents/ braucht es ab API 29 kein Recht und keinen Dialog - und
 * iOS fragt fuer seinen Ordner ja auch nicht. Vor API 29 gibt es diesen
 * Weg nicht ohne Speicherrecht; dann bleibt es beim App-Ordner.
 */
object ProjektSpiegel {
    private const val ORDNER = "Documents/PSMobile/Projects/"

    fun spiegeln(context: Context, datei: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            entfernen(context, datei.name)
            val resolver = context.contentResolver
            val werte = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, datei.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "model/3mf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, ORDNER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val ziel = resolver.insert(MediaStore.Files.getContentUri("external"), werte) ?: return
            resolver.openOutputStream(ziel)?.use { out -> datei.inputStream().use { it.copyTo(out) } }
            werte.clear()
            werte.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(ziel, werte, null, null)
        }
    }

    fun entfernen(context: Context, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(name, ORDNER),
            )
        }
    }
}
