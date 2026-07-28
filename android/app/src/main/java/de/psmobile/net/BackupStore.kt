package de.psmobile.net

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sichert jede an einen Drucker gesendete Datei in einen frei waehlbaren
 * Ordner.
 *
 * Ueber das Storage Access Framework statt ueber einen Pfad: damit
 * funktionieren auch eingebundene Netzlaufwerke - SMB-Freigaben ueber die
 * Dateien-App, Nextcloud, Drive. Die App braucht dafuer keine
 * Speicherberechtigung und kein SMB im eigenen Code.
 *
 * Der Nutzer waehlt den Ordner einmal per ACTION_OPEN_DOCUMENT_TREE, die
 * Freigabe bleibt ueber Neustarts bestehen.
 */
object BackupStore {

    private const val TAG = "BackupStore"

    data class Result(val ok: Boolean, val message: String)

    fun isConfigured(context: Context): Boolean = PrinterStore.backupTree(context) != null

    fun folderName(context: Context): String? {
        val uri = PrinterStore.backupTree(context) ?: return null
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name
        }.getOrNull()
    }

    /**
     * @param printerName kommt in den Dateinamen, damit man spaeter sieht,
     *                    an welches Geraet die Datei ging.
     */
    fun archive(context: Context, source: File, printerName: String): Result {
        val treeUri = PrinterStore.backupTree(context)
            ?: return Result(false, "Kein Sicherungsordner festgelegt")

        return runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
                ?: return Result(false, "Sicherungsordner nicht erreichbar")
            if (!tree.canWrite())
                return Result(false, "Kein Schreibrecht im Sicherungsordner")

            val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val safePrinter = printerName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val name = "${stamp}_${safePrinter}_${source.name}"

            val target = tree.createFile("text/plain", name)
                ?: return Result(false, "Datei konnte nicht angelegt werden")

            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return Result(false, "Zieldatei nicht beschreibbar")

            Result(true, "Gesichert als $name")
        }.getOrElse {
            Log.w(TAG, "Sicherung fehlgeschlagen", it)
            Result(false, it.message ?: "Sicherung fehlgeschlagen")
        }
    }
}
