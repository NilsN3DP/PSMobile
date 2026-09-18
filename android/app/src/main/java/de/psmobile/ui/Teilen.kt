package de.psmobile.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import de.psmobile.SlicerModel

/**
 * Gegenstueck zu `ShareLink` / dem Teilen-Blatt auf iOS: eine Datei an
 * eine andere App weiterreichen. Ueber den FileProvider des Modells,
 * weil file://-URIs seit Nougat nicht mehr nach aussen duerfen.
 */
fun teilen(context: Context, model: SlicerModel, datei: File, mime: String = "application/octet-stream") {
    val uri = model.shareableUri(datei) ?: run {
        Log.w("Teilen", "Datei nicht teilbar: ${datei.name}")
        return
    }
    teilen(context, uri, mime, datei.name)
}

fun teilen(context: Context, uri: Uri, mime: String = "application/octet-stream", titel: String? = null) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        titel?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, titel).apply {
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
        .onFailure { Log.w("Teilen", "Teilen-Dialog liess sich nicht oeffnen", it) }
}

/** Mehrere Dateien auf einmal - fuer "Alle Betten schneiden". */
fun teilen(context: Context, model: SlicerModel, dateien: List<File>, mime: String = "application/octet-stream") {
    val uris = dateien.mapNotNull { model.shareableUri(it) }
    if (uris.isEmpty()) return
    if (uris.size == 1) { teilen(context, uris.first(), mime, dateien.first().name); return }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { Log.w("Teilen", "Teilen-Dialog liess sich nicht oeffnen", it) }
}
