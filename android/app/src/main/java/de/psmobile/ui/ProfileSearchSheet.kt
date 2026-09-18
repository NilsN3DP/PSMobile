package de.psmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import de.psmobile.ui.theme.PrusaColors
import de.psmobile.SlicerModel
import de.psmobile.ui.theme.ScaledOverlay

/**
 * Profilsuche fuer Druck-, Filament- und Druckereinstellungen -
 * Gegenstueck zu `ios/PSMobile/Screens/ProfileSearchSheet.swift`.
 *
 * Filamentlisten haben je nach Drucker mehrere hundert Eintraege; ein
 * Suchfeld ist hier der einzige zumutbare Weg zum eigenen Profil.
 *
 * iOS zeigt das Blatt mit `.presentationDetents([.medium, .large])` -
 * hier ein ModalBottomSheet. `@Binding isPresented` wird zu Wert plus
 * `onIsPresentedChange`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSearchSheet(
    model: SlicerModel,
    /** "print", "filament" oder "printer" - derselbe String wie `SettingsView.tab`. */
    tab: String,
    titel: String,
    isPresented: Boolean,
    onIsPresentedChange: (Boolean) -> Unit,
) {
    if (!isPresented) return
    val ps = LocalPsScale.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var suche by remember { mutableStateOf("") }

    val aktuell: String? = model.selectedPreset(tab)
    val alle: List<String> = model.presetNames(tab)
    val treffer: List<String> =
        if (suche.isEmpty()) alle else alle.filter { it.contains(suche, ignoreCase = true) }

    ModalBottomSheet(
        onDismissRequest = { onIsPresentedChange(false) },
        sheetState = sheetState,
        containerColor = PrusaColors.background,
        contentColor = PrusaColors.textPrimary,
    ) {
        ScaledOverlay {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ps.windowSize.height * 0.5f)
                    .background(PrusaColors.background),
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Die Navigationsleiste: Titel mittig, "Fertig" rechts.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(ps.touch(44)),
                    ) {
                        Text(
                            titel,
                            fontSize = ps.font(15),
                            fontWeight = FontWeight.SemiBold,
                            color = PrusaColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = ps.pt(72)),
                        )
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .clickable { onIsPresentedChange(false) }
                                .heightIn(min = ps.touch(44))
                                .padding(horizontal = ps.pt(16))
                                .testTag("profilsuche.fertig"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                st("Done", "Fertig"),
                                fontSize = ps.font(15),
                                fontWeight = FontWeight.SemiBold,
                                color = PrusaColors.orange,
                            )
                        }
                    }

                    Suchfeld(suche, onSucheChange = { suche = it })

                    if (treffer.isEmpty()) {
                        Text(
                            st("No profile matches the search.", "Kein Profil passt zur Suche."),
                            fontSize = ps.font(13),
                            color = PrusaColors.textMuted,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = ps.pt(24)),
                        )
                        Spacer(Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = ps.pt(6)),
                        ) {
                            items(treffer, key = { it }) { name ->
                                Zeile(
                                    name = name,
                                    gewaehlt = name == aktuell,
                                    onClick = {
                                        model.selectPreset(tab, name)
                                        onIsPresentedChange(false)
                                    },
                                )
                            }
                        }
                    }
                }
                PSMarke(name = "profilsuche")
            }
        }
    }
}

@Composable
private fun Suchfeld(suche: String, onSucheChange: (String) -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(ps.pt(12))
            .clip(RoundedCornerShape(ps.pt(6)))
            .background(PrusaColors.panelRaised)
            .height(ps.touch(46))
            .padding(horizontal = ps.pt(12)),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfSymbol("magnifyingglass", Modifier.size(ps.pt(16)), tint = PrusaColors.textMuted)
        BasicTextField(
            value = suche,
            onValueChange = onSucheChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            textStyle = TextStyle(fontSize = ps.font(14), color = PrusaColors.textPrimary),
            cursorBrush = SolidColor(PrusaColors.orange),
            modifier = Modifier
                .weight(1f)
                .testTag("profilsuche.suche"),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (suche.isEmpty()) {
                        Text(
                            st("Search profiles", "Profile durchsuchen"),
                            fontSize = ps.font(14),
                            color = PrusaColors.textMuted,
                        )
                    }
                    inner()
                }
            },
        )
        if (suche.isNotEmpty()) {
            Box(
                Modifier
                    .size(ps.touch(32))
                    .clickable { onSucheChange("") }
                    .testTag("profilsuche.leeren"),
                contentAlignment = Alignment.Center,
            ) {
                SfSymbol("xmark.circle.fill", Modifier.size(ps.pt(16)), tint = PrusaColors.textMuted)
            }
        }
    }
}

@Composable
private fun Zeile(name: String, gewaehlt: Boolean, onClick: () -> Unit) {
    val ps = LocalPsScale.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (gewaehlt) PrusaColors.panelRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = ps.touch(46))
            .padding(horizontal = ps.pt(16))
            .testTag("profilsuche.eintrag.$name"),
        horizontalArrangement = Arrangement.spacedBy(ps.pt(8)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontSize = ps.font(14),
            fontWeight = if (gewaehlt) FontWeight.SemiBold else FontWeight.Normal,
            color = PrusaColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (gewaehlt) {
            SfSymbol("checkmark", Modifier.size(ps.pt(16)), tint = PrusaColors.orange)
        }
    }
}
