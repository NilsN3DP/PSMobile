package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.psmobile.core.PsmCore
import de.psmobile.ui.theme.PrusaColors

/**
 * Ersteinrichtung - Gegenstueck zum Konfigurationsassistenten des Desktops.
 *
 * Zweck ist nicht Kosmetik: Werden alle 37 Druckermodelle installiert,
 * dauert der Start 13,5 s und die Auswahllisten enthalten 5762 Filamente.
 * Mit einem einzelnen gewaehlten Drucker sind es 1,9 s und 189 Filamente.
 */
@Composable
fun SetupScreen(
    models: List<PsmCore.PrinterModel>,
    busy: Boolean,
    onConfirm: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showSla by remember { mutableStateOf(false) }
    var langMenu by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(PsUi.language) }

    val shown = models.filter { showSla || !it.isSla }

    Box(modifier.fillMaxSize().background(PrusaColors.Background), Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(24.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        PsUi.tr("Configuration Assistant"),
                        color = PrusaColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        PsUi.tr("Select all printers, you want to use."),
                        color = PrusaColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }

                // Sprache: Englisch ist Standard, uebersetzt wird aus den
                // mitgelieferten PrusaSlicer-Katalogen.
                Box {
                    Row(
                        Modifier.height(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrusaColors.PanelRaised)
                            .clickable { langMenu = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(lang.uppercase(), color = PrusaColors.TextPrimary, fontSize = 13.sp)
                        Text("  ▾", color = PrusaColors.TextMuted)
                    }
                    DropdownMenu(langMenu, { langMenu = false },
                                 Modifier.background(PrusaColors.PanelRaised)) {
                        PsUi.availableLanguages.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code.uppercase(),
                                              color = PrusaColors.TextPrimary, fontSize = 13.sp) },
                                onClick = {
                                    langMenu = false
                                    lang = code
                                    PsUi.setLanguage(context, code)
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = PrusaColors.Divider)

            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)) {
                Checkbox(
                    checked = showSla,
                    onCheckedChange = { showSla = it },
                    colors = CheckboxDefaults.colors(checkedColor = PrusaColors.Orange),
                )
                Text(PsUi.tr("SLA materials"), color = PrusaColors.TextMuted, fontSize = 13.sp)
                Box(Modifier.weight(1f))
                Text(
                    "${selected.size} / ${shown.size}",
                    color = PrusaColors.TextMuted, fontSize = 13.sp,
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown, key = { it.key }) { m ->
                    val on = m.key in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (on) PrusaColors.Orange.copy(alpha = 0.16f)
                                        else PrusaColors.PanelRaised)
                            .border(1.dp,
                                    if (on) PrusaColors.Orange else PrusaColors.Divider,
                                    RoundedCornerShape(4.dp))
                            .clickable {
                                selected = if (on) selected - m.key else selected + m.key
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = PrusaColors.Orange),
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(m.name, color = PrusaColors.TextPrimary, fontSize = 15.sp)
                            Text(
                                buildString {
                                    append(if (m.isSla) "SLA" else "FFF")
                                    if (m.family.isNotBlank()) append("  ·  ${m.family}")
                                    if (m.variantCount > 0)
                                        append("  ·  ${m.variantCount} ${PsUi.tr("nozzles")}")
                                },
                                color = PrusaColors.TextMuted, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onConfirm(selected.toList()) },
                enabled = selected.isNotEmpty() && !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrusaColors.Orange,
                    contentColor = PrusaColors.TextPrimary,
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = PrusaColors.TextPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(PsUi.tr("Finish"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
