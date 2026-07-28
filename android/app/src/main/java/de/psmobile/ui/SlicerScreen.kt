package de.psmobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.psmobile.core.PsmCore
import de.psmobile.slicing.SlicerService
import kotlin.math.roundToInt

/**
 * Oberflaeche fuer M3.
 *
 * Bewusst noch ohne 3D-Ansicht: erst muss der Weg Datei -> Slice -> G-Code
 * auf echter Hardware belegt sein (M2/M3), dann kommt der Viewport (M4)
 * und darauf die Gesten- und Stiftbedienung (M5).
 *
 * Die Aktionsleiste sitzt schon unten, weil das Layout spaeter genau so
 * bleiben soll - siehe docs/05-ui-konzept-touch-stift.md.
 */
@Composable
fun SlicerScreen(
    service: SlicerService?,
    onPickFile: (android.net.Uri) -> Unit,
) {
    // Bewusst hier verzweigen und nicht im Rumpf: `collectAsState` darf
    // nicht bedingt aufgerufen werden, sonst wechselt die Aufrufstelle
    // in der Komposition.
    if (service == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    SlicerContent(service, onPickFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlicerContent(
    service: SlicerService,
    onPickFile: (android.net.Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onPickFile) }

    val objects by service.objects.collectAsState()
    val progress by service.progress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("PSMobile")
                    Text(
                        "Kern ${runCatching { PsmCore.coreVersion() }.getOrDefault("?")}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            })
        },
        // Kein FAB: er lag ueber der Aktionsleiste. Alle Aktionen gehoeren
        // ohnehin in eine daumenreichbare Zeile unten - siehe
        // docs/05-ui-konzept-touch-stift.md.
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Modell", modifier = Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = { service.startSlice() },
                    enabled = objects.isNotEmpty() && progress !is SlicerService.Progress.Running,
                    modifier = Modifier.weight(1f),
                ) { Text("Slicen") }

                if (progress is SlicerService.Progress.Running) {
                    OutlinedButton(onClick = { service.cancelSlice() }) { Text("Stopp") }
                }
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Platzhalter fuer den 3D-Viewport (M4) -----------------
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("3D-Ansicht folgt in M4", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${objects.size} Objekt(e) auf dem Bett",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // --- Objektliste -------------------------------------------
            if (objects.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(objects, key = { it.id }) { obj ->
                        ObjectRow(obj) { service.removeObject(obj.id) }
                    }
                }
            }

            // --- Fortschritt -------------------------------------------
            ProgressSection(progress)
        }
    }
}

@Composable
private fun ObjectRow(obj: PsmCore.ObjectInfo, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    obj.name.ifBlank { "Objekt ${obj.id}" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "%.1f x %.1f x %.1f mm  -  %,d Dreiecke".format(
                        obj.sizeMm.first, obj.sizeMm.second, obj.sizeMm.third, obj.triangles,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (obj.outsideBed) {
                    Text(
                        "ragt ueber das Bett hinaus",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Entfernen")
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: SlicerService.Progress) {
    when (progress) {
        is SlicerService.Progress.Idle -> Unit

        is SlicerService.Progress.Running -> Column(Modifier.fillMaxWidth()) {
            // Die Phase wird bewusst mit angezeigt, nicht nur Prozent -
            // ein Slice kann auf dem Handy mehrere Minuten dauern.
            Text("${progress.percent}%  -  ${progress.stage}",
                 style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }

        is SlicerService.Progress.Done -> {
            val st = progress.stats
            Text(
                buildString {
                    append("Fertig in %.1f s".format(progress.seconds))
                    if (st != null) {
                        append("  -  Druckzeit %d min".format((st.printTimeSeconds / 60).roundToInt()))
                        append("  -  %.1f g".format(st.filamentGrams))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is SlicerService.Progress.Failed -> Text(
            progress.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )

        is SlicerService.Progress.Cancelled -> Text(
            "abgebrochen",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
