package de.psmobile.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import de.psmobile.shared.rules.OpenSource
import de.psmobile.ui.theme.PrusaColors

/// "Open Source & Lizenzen": woraus Slicer Mobile besteht und wo der
/// Quellcode liegt. Gegenstueck zu `OpenSourceView.swift`.
///
/// PrusaSlicer steht unter AGPL-3.0 - ein abgeleitetes Werk muss seinen
/// Quellcode zugaenglich machen und die verwendeten Bibliotheken nennen.
/// Die Liste selbst kommt aus `OpenSource` im gemeinsamen Modul.
@Composable
fun OpenSourceView(onClose: () -> Unit) {
    val ps = LocalPsScale.current
    val browser = LocalUriHandler.current
    val deutsch = Sprache.aktuell == "de"

    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(PrusaColors.background),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.Start) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(ps.touch(56))
                    .padding(horizontal = ps.pt(16)),
                horizontalArrangement = Arrangement.spacedBy(ps.pt(16)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹  " + st("Back", "Zurück"),
                    Modifier
                        .clickable(onClick = onClose)
                        .testTag("opensource.zurueck"),
                    fontSize = ps.font(15), color = PrusaColors.orange,
                )
                Text(st("Open source & licenses", "Open Source & Lizenzen"), fontSize = ps.font(20), color = PrusaColors.textPrimary)
                Spacer(Modifier.weight(1f))
            }
            HorizontalDivider(color = PrusaColors.divider)

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ps.pt(16))
                    .testTag("opensource.liste"),
                verticalArrangement = Arrangement.spacedBy(ps.pt(10)),
            ) {
                Text(
                    st(
                        "Slicer Mobile is an unofficial app built on PrusaSlicer. It is not affiliated with or endorsed by Prusa Research. " +
                            "Like PrusaSlicer it is released under the ${OpenSource.LIZENZ} license – the complete source code is public.",
                        "Slicer Mobile ist eine inoffizielle App auf Basis von PrusaSlicer. Sie ist nicht mit Prusa Research verbunden und nicht von Prusa Research freigegeben. " +
                            "Wie PrusaSlicer steht sie unter der Lizenz ${OpenSource.LIZENZ} – der vollständige Quellcode ist öffentlich.",
                    ),
                    fontSize = ps.font(13), color = PrusaColors.textPrimary,
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ps.touch(48))
                        .clip(RoundedCornerShape(ps.pt(3)))
                        .background(PrusaColors.orange)
                        .clickable { runCatching { browser.openUri(OpenSource.REPO) } }
                        .testTag("opensource.github")
                        .padding(horizontal = ps.pt(14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        st("Source code on GitHub", "Quellcode auf GitHub"),
                        fontSize = ps.font(14), fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                }
                Text(OpenSource.REPO, fontSize = ps.font(11), color = PrusaColors.textMuted)

                Text(
                    st("Third-party components", "Verwendete Komponenten").uppercase(),
                    Modifier.padding(top = ps.pt(12)),
                    fontSize = ps.font(11), fontWeight = FontWeight.SemiBold, color = PrusaColors.textMuted,
                )
                OpenSource.fuer(OpenSource.Plattform.ANDROID).forEachIndexed { index, k ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ps.pt(10)))
                            .background(PrusaColors.panelRaised)
                            .clickable { runCatching { browser.openUri(k.url) } }
                            .testTag("opensource.komponente.$index")
                            .padding(horizontal = ps.pt(14), vertical = ps.pt(10)),
                        verticalArrangement = Arrangement.spacedBy(ps.pt(2)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(k.name, Modifier.weight(1f), fontSize = ps.font(14), color = PrusaColors.textPrimary)
                            Text(k.lizenz, fontSize = ps.font(11), color = PrusaColors.orange)
                        }
                        Text(if (deutsch) k.rolleDe else k.rolleEn, fontSize = ps.font(11), color = PrusaColors.textMuted)
                        Text(k.url, fontSize = ps.font(10), color = PrusaColors.textMuted)
                    }
                }
                Spacer(Modifier.size(ps.pt(8)))
            }
        }
        PSMarke(name = "opensource")
    }
}
