# Gleichstand der Oberflächen — Befund und Umsetzungsplan

Stand 19.08.2026. Grundlage ist ein Abgleich Datei für Datei: alle 176
Bedienelement-Kennungen aus `ios/PSMobile` gegen die Android-Oberfläche,
und alle 188 Funktionen der C-Schnittstelle gegen `android/jni/psm_jni.cpp`.

Ergebnis: **26 Kernfunktionen, die iOS benutzt und Android nicht bindet.**
Daraus folgen sechs Unterschiede in der Bedienung, dazu drei reine
Oberflächenunterschiede ohne Kernbezug und drei Punkte, an denen
umgekehrt iOS hinterherhängt.

Ein zweiter Durchgang (Abschnitt K) vergleicht die Ausarbeitung statt
der Funktionen und findet sieben weitere Stellen, an denen Android
dünner ist. Ein dritter (Abschnitt L) liest die Historie: **neunzig
Commits fassen `ios/` an, ohne `android/` anzufassen** — darunter neun
Gestaltungsentscheidungen, die nie zurückkamen.

Damit sind es **24 Schritte**, nicht die fünf aus meinem ersten Bericht.
Wer nur den Zustand vergleicht, findet fehlende Funktionen. Wer die
Historie liest, findet auch die Entscheidungen, die auf Android in der
älteren Fassung stehengeblieben sind.

Was gleich ist, steht am Ende — damit klar ist, was nicht mehr geprüft
werden muss.

---

## A · Vorschau: Statistik und Legende fehlen ganz

**iOS** (`FinalPreviewPanel.swift`, in Simple und Advanced verwendet)

- Statistikzeile: Druckzeit, Filament in Metern, Gramm, Z-Bereich der
  gewählten Schichten.
- Legende: Umschalter *Merkmale ↔ Extruder*, darunter je ein Chip mit
  Farbpunkt. Antippen blendet diese Rolle bzw. diesen Extruder aus und
  wieder ein.

**Android** — nichts davon.

Nicht gebunden: `psm_preview_snapshot_get`, `psm_preview_role_at`,
`psm_preview_extruder_at`, `psm_preview_layer_at`,
`psm_viewport_set_preview_view`, `psm_viewport_set_role_visible`,
`psm_viewport_set_extruder_visible`, `psm_slice_load_gcode_for_preview`.

**Umsetzung**

1. JNI: die acht Funktionen binden. Der Schnappschuss geht wie
   `nativeSurfacePick` als Tab-getrennte Zeichenkette zurück, die Listen
   je Eintrag eine Zeile — kein eigener JNI-Objektbau.
2. `PsmCore.kt`: `PreviewSnapshot`, `PreviewRole`, `PreviewExtruder`,
   `PreviewLayerMetrics` als Datenklassen, spiegelbildlich zu
   `PsmCore.swift`.
3. `PsmViewport.kt`: `previewView`, `setRoleVisible`, `setExtruderVisible`.
4. Gemeinsames Modul: die Bereichsrechnung aus `PreviewRange.swift` nach
   `shared/rules/PreviewRange.kt` ziehen, damit beide Seiten dieselbe
   Zahl zeigen. Der Unit-Test dafür existiert auf iOS schon.
5. Compose: `PreviewStatsRow` und `PreviewLegendPicker` neu, in
   `SlicerScreen` unter dem Viewport und in `SimpleSliceSheet`.

Aufwand: groß. Größter sichtbarer Gewinn.

---

## B · Verbrauch je Werkzeug

**iOS** zeigt nach dem Slicen je Extruder eine Zeile: Farbfeld, `T1`,
Volumen im Modell in cm³, und getrennt davon der Anteil im Reinigungsturm
(`AdvancedWorkspaceView.swift:832ff`). Erst ab zwei Extrudern — bei
einfarbigem Druck stünde dieselbe Zahl zweimal da.

**Android** — fehlt.

Nicht gebunden: `psm_slice_extruder_count`, `psm_slice_extruder_at`.

**Umsetzung**: beide binden, `ExtruderUsage`-Datenklasse, Zeile in den
Vorschaubereich aus A. Klein, sobald A steht.

---

## C · Anordnen ohne Optionen

**iOS** öffnet ein Popover am Knopf (`BedSelector.swift:450ff`):
Zielmodus *aktuelles Bett ↔ alle Betten*, Liste der Zielbetten mit
Schloss und Objektzahl, Abstand 0–50 mm in Halbschritten, Schalter
*Drehen erlauben*, und danach ein Ergebnistext.

**Android** hat einen Knopf, der `arrange()` mit Abstand 0 und ohne
Drehen aufruft. Keine Rückmeldung.

Nicht gebunden: `psm_arrange_bed_ex` (liefert zusätzlich `psm_arrange_info`
mit Status, Objekt- und Instanzzahl und meldet *gesperrt* und *voll* als
eigene Fehler).

**Umsetzung**: `nativeArrangeBedEx` binden, Dialog nachbauen. Der
gemeinsame `BedStripContract` prüft die Verfügbarkeit bereits — die
Entscheidung *anordnen möglich?* muss nicht doppelt entstehen.

---

## D · Adaptive Schichthöhe

**iOS** hat im Schichthöhen-Editor einen Qualitätsregler und
*Berechnen* (`LayerProfileView.swift:191ff`) — PrusaSlicers adaptive
Schichthöhe.

**Android** kann im selben Editor nur Punkte von Hand setzen.

Nicht gebunden: `psm_model_layer_profile_adaptive`.

**Umsetzung**: binden, Regler und Knopf ergänzen. Klein.

---

## E · ZIP-Import fehlt

**iOS** öffnet eine `.zip` (typisch von Printables), entpackt die Modelle
und fragt danach, in welchem Modus es weitergeht (`ZipModusDialog`).

**Android** lässt das Format nicht einmal zu.

Nicht gebunden: `psm_zip_extract_models`.

**Umsetzung**: binden, `zip` in `ModelFormats` aufnehmen, Auswahl-Dialog
*Simple ↔ Advanced* nachbauen, Intent-Filter in `AndroidManifest.xml`
erweitern.

---

## F · Materialauswahl ist auf Android eine Liste

**iOS** (`MaterialAuswahlView.swift`) filtert über Suchfeld, Typ-Knöpfe
und **Farbpunkte** der häufigsten Farben im Bestand und zeigt Karten mit
Hersteller, Typ, Farbe und einer Spulen-Grafik. Dazu ein Schalter
*inkompatible Profile zeigen*.

**Android** (`SimpleModeScreen.kt:1039ff`) hat ein Suchfeld, Typ-Knöpfe,
die bloß den Suchtext setzen, und eine Liste der ersten fünfzehn Treffer.

Kein Kernproblem: `FilamentCatalog` mit `types()`, `colors()` und
`filter()` liegt seit langem in `android/shared` und wird auf Android
nicht benutzt.

**Umsetzung**: die Auswahl auf `FilamentCatalog` umstellen, Farbpunkte
und Karten bauen. Rein Compose, kein Bauen des Kerns nötig.

---

## G · Einstellungskopf ohne Profilsuche und ohne Zurücksetzen

**iOS** zeigt im Kopf der Einstellungsseiten den Namen des geltenden
Profils mit Lupe — antippen öffnet die Profilsuche — und daneben einen
Zähler der gegenüber dem gespeicherten Profil geänderten Werte mit
Rückfrage vor dem Zurücksetzen (`SettingsView.swift:95ff`).

**Android** hat dort nur *Zurück* und die drei Reiter.

Die Profilsuche selbst gibt es auf Android schon, aber nur im
Advanced-Seitenband (`filterPresetOptions`).

**Umsetzung**: Kopfzeile ergänzen, vorhandene Suche wiederverwenden,
`profilaenderungen()` als Gegenstück in `SlicerService`.

---

## H · Druckerauswahl: Karten gegen Suchliste

**iOS** zeigt Drucker als Karten, in Simple und Advanced dieselbe Ansicht
(`DruckerAuswahlView.swift`). Im Code steht ausdrücklich, dass die
Klappliste mit rohen Profilnamen genau deshalb ersetzt wurde.

**Android** öffnet im Advanced einen Dialog mit Suchliste
(`AdvancedSidebar.kt:891`).

**Umsetzung**: Kartenansicht nachbauen und für die Druckerzeile
verwenden. Filament und Druckeinstellungen bleiben Suchlisten — dort sind
es hunderte Einträge, und iOS macht es genauso.

---

## I · Kleinere Kernlücken ohne eigenen Bildschirm

Gebunden werden sollten außerdem, weil iOS sie benutzt:

| Funktion | Wofür |
|---|---|
| `psm_viewport_gesture_begin` | Eine Geste = ein Rückgängig-Schritt. Android klammert stattdessen selbst über `beginHistory`; das funktioniert, ist aber ein zweiter Weg für dieselbe Sache. |
| `psm_viewport_set_move_range`, `psm_viewport_move_range_bounds` | Grenzen beim Ziehen, damit ein Objekt nicht ins Nichts wandert. |
| `psm_viewport_gizmo_axis_screen`, `psm_viewport_get_gizmo` | Achsenbeschriftung am Griff. |
| `psm_viewport_active_layer_visualization`, `psm_viewport_active_paint_visualization` | Legenden zu Schichthöhen und Bemalung. |
| `psm_slice_result_is_current`, `psm_slice_accept_remote_gcode` | Fernslicen: fertiges G-Code übernehmen, statt lokal neu zu rechnen. |
| `psm_model_lay_on_facet_instance` | Auf Fläche legen für genau diese Kopie statt immer Instanz 0. |
| `psm_model_bed_state` | Feinere Auskunft als `outside_bed`: schneidet den Rand / ganz daneben / unter dem Bett. |
| `psm_design_revision`, `psm_preset_option_at` | Innereien, kein eigenes Bedienelement. |

---

## J · Wo umgekehrt iOS hinterherhängt

Der Gleichstand gilt in beide Richtungen.

1. **Sonderwerte in den Einstellungen.** Android hat Bettform,
   Reinigungsmatrix, **Ramming**, **Ersetzungen** und
   **Kompatibilitätsregeln** sowie das Hochladen von Bettmodell und
   -textur (`SpecialSettingsDialogs.kt`). iOS kennt nur Bettform und
   Reinigungsmatrix (`SettingsView.swift:277f`).
2. **Fuzzy Skin bemalen** — am 19.08. auf iOS nachgezogen, gebaut ist es
   noch nicht (Mac war nicht erreichbar).
3. **Blockieren bei der Naht** — auf Android fehlte der Zustand, auf iOS
   wurde er fälschlich auch für Fuzzy angeboten. Beides am 19.08.
   korrigiert.

---

## K · Zweiter Durchgang: Ausarbeitung statt Funktionen

Der erste Durchgang verglich Konzepte und Kernfunktionen. Er findet, was
ganz fehlt, aber nicht, wo dieselbe Sache auf Android duenner ausfaellt.
Der zweite Durchgang vergleicht deshalb den Textbestand beider Seiten
Bildschirm fuer Bildschirm.

### K1 · Slice-Blatt

iOS zeigt nach dem Slicen mehr als einen Sichern-Knopf
(`SliceSheet.swift:79ff`):

- **„Geslict in 2:14"** — wie lange es gedauert hat.
- **Mehrere G-Code-Dateien** bei Mehrbett: eine Zeile je Datei mit
  eigenem *Senden*-Knopf, dazu *Alle exportieren*.
- **An Drucker senden** direkt aus dem Blatt.
- Ein Hinweis, wenn der G-Code nicht geschrieben werden konnte.

Android hat *G-Code sichern* und *Schliessen*. Senden geht nur ueber
einen anderen Weg, die Dauer steht nirgends, und bei mehreren Betten
gibt es keine Liste.

### K2 · ColorMix-Vorschau

iOS zeigt die gemischte Farbe vor dem Speichern (`ColorMixView.swift:92`)
und meldet, wenn sie nicht gespeichert werden konnte. Android speichert
blind.

### K3 · Leere Zustaende im Simple Mode

iOS hat ein eigenes Muster dafuer (`leeresPanel`): eine Aussage plus den
Knopf, der aus der Lage herausfuehrt.

- *Kein Material vorhanden* → **Advanced Mode oeffnen**
- *Keine Druckeinstellungen vorhanden* → **Druckeinstellungen einrichten**

Auf Android fehlen beide. Wer in diese Lage geraet, sieht eine leere
Flaeche ohne Weg heraus.

### K4 · Material je Extruder im Simple Mode

iOS hat in der Materialseite einen Abschnitt **„Je Extruder"**
(`SimpleModeView.swift:1028`). Android nicht.

### K5 · Projekt weitergeben

iOS kann das Projekt (`.3mf`) aus dem Projektpanel weitergeben, ebenso
die Platte und alle G-Code-Dateien auf einmal. Android kann nur den
G-Code einer Datei und das Protokoll teilen.

### K6 · Reinigungsturm sitzt woanders

Kein Verlust, aber ein Unterschied: iOS stellt X, Y und Drehung des
Reinigungsturms direkt in die Extruderbank, wo man ohnehin arbeitet.
Android versteckt sie in den Projektwerkzeugen.

### K7 · Teile-Liste

iOS zeigt je Teil den Typ im Klartext — *Teil, Aussparung, Modifikator,
Stuetzensperre, Stuetzenzwang*. Android zeigt den Typnamen ebenfalls,
aber die Liste liegt im Werkzeuge-Reiter statt neben den Objektwerten.
Gleicher Inhalt, anderer Ort.

---

## L · Dritter Durchgang: was die Historie zeigt

Die beiden ersten Durchgaenge lesen den Zustand. Der dritte liest die
Entstehung — und der ist der ehrlichste.

    git log --since="21 days ago" -- ios/

**Neunzig Commits fassen ios/ an, ohne android/ anzufassen.** Der groesste
Teil davon ist der Aufbau der Plattform selbst und ihre Tests; die gab es
vorher nicht, und dafuer gibt es auf Android kein Gegenstueck. Aber rund
ein Dutzend davon sind **Gestaltungsentscheidungen**, die nie
zurueckgekommen sind. Genau die faellt niemand auf, der nur den Zustand
vergleicht: auf Android steht dort nichts Falsches, es steht nur noch die
aeltere Fassung.

### L1 · Schwebende Dialoge statt Vollbildseiten

Commits `8eb97e0`, `f85c169`, `170b014`, `e1e74a0` (05.08.)

Auf iOS liegen **Ersteinrichtung**, **App-Einstellungen**,
**Profilwechsel** und **ZIP-Modus** als Dialog ueber dem Arbeitsbereich
(`SchwebenderDialog`). Man sieht, wohin man zurueckkehrt. Auf Android
sind es Vollbildseiten.

### L2 · Griffe schwebend statt permanent oben

Commit `94a53aa` (06.08.)

Move, Rotate und Scale wurden von einer festen Leiste oben zu einer
kompakten, schwebenden Gruppe. Im selben Commit wanderten **Drucker und
App-Einstellungen nach unten links in die Werkzeugschiene**.

### L3 · Bettleiste

Commits `04b2c3d`, `c4402bb` (06.08.)

Die Bettleiste flog **aus dem Easy Mode heraus** — dort stoerte sie mehr
als sie half. Im Advanced wurde sie eine **Kapselreihe**, und Sperren,
Umbenennen und Entfernen zogen ins **Kontextmenue**. **Arrange oeffnet
per Tipp und Halten.**

### L4 · Separate-Menue statt Doppelung

Commit `d4cf9a1` (06.08.)

*In Objekte teilen* und *In Volumen teilen* wurden zu einem
Separate-Menue zusammengefasst. Android hat weiterhin zwei Knoepfe
nebeneinander.

### L5 · Groessenverhaeltnis je Objekt

Commit `0befe95` (04.08.)

iOS zeigt in beiden Objektlisten ein kleines Kaestchen mit dem
Groessenverhaeltnis (`SimpleModelSheetView.swift:288`). Android nicht.

### L6 · Inkompatible Filamente

Commits `2213e04`, `61c1b47` (06.08.)

Erst **gekennzeichnet statt versteckt**, dann **standardmaessig
ausgeblendet mit Umschalter**. Auf Android gibt es die Einstellung im
Kern, aber keinen Umschalter an der Materialauswahl.

### L7 · „Export G-Code" statt „Save"

Commit `2213e04` (06.08.)

Bewusste Wortwahl: die Datei verlaesst die App. Android sagt weiterhin
*G-Code sichern*.

### L8 · Schmale Geraete

Commits `2213e04`, `12f5b52` (06.08.)

Die **iPad-Seitenleiste bleibt auch hochkant ausgeklappt**, und die
**iPhone-Vorschau wurde scrollbar**, weil der untere Schichtregler sonst
aus dem Bottom-Sheet fiel. Auf Android ist das Verhalten im Hochformat
nie geprueft worden.

### L9 · Advanced-Inspector

Commits `3e45739`, `cb08ec1`, `bda94c2`, `8a53aa3` (05.08.)

Nach einer Auswahl **oeffnet sich der Bearbeiten-Bereich von selbst**,
und der Fokus bleibt beim Blaettern layoutstabil. Vier Anlaeufe, bis es
sass — auf Android ist keiner davon angekommen.

---

## Reihenfolge

Nach sichtbarem Gewinn je Aufwand, und so, dass jeder Schritt für sich
prüfbar ist:

| # | Schritt | Kern neu bauen? |
|---|---|---|
| 1 | **A** Vorschau: Statistik und Legende | ja |
| 2 | **B** Verbrauch je Werkzeug | ja, zusammen mit 1 |
| 3 | **F** Materialauswahl | nein |
| 4 | **G** Einstellungskopf | nein |
| 5 | **C** Anordnen mit Optionen | ja |
| 6 | **D** Adaptive Schichthöhe | ja, zusammen mit 5 |
| 7 | **H** Druckerkarten | nein |
| 8 | **E** ZIP-Import | ja |
| 9 | **K1** Slice-Blatt: Dauer, Dateiliste, Senden | nein |
| 10 | **K3** Leere Zustaende im Simple Mode | nein |
| 11 | **K4** Material je Extruder | nein |
| 12 | **K5** Projekt und Platte weitergeben | nein |
| 13 | **K2** ColorMix-Vorschau | nein |
| 14 | **K6** Reinigungsturm in die Extruderbank | nein |
| 15 | **L1** Schwebende Dialoge | nein |
| 16 | **L3** Bettleiste: Easy Mode, Kapseln, Kontextmenue | nein |
| 17 | **L2** Griffe schwebend, Drucker/Einstellungen unten links | nein |
| 18 | **L9** Inspector oeffnet nach Auswahl | nein |
| 19 | **L6** Inkompatible Filamente mit Umschalter | nein |
| 20 | **L5** Groessenverhaeltnis je Objekt | nein |
| 21 | **L4** Separate-Menue, **L7** Wortwahl | nein |
| 22 | **L8** Hochformat pruefen und nachziehen | nein |
| 23 | **I** restliche Bindungen | ja |
| 24 | **J** iOS nachziehen | nein, aber der Mac muss erreichbar sein |

Die Schritte ohne Kernbau (3, 4, 7) lassen sich erledigen, während ein
Kernbau läuft, statt auf ihn zu warten.

Jeder Schritt endet gleich: bauen, auf dem Emulator ansehen, und den
Beleg als Bildschirmfoto festhalten. Ohne das gilt er nicht als fertig.

---

## Geprüft und gleich

Startseite mit Zuletzt-Kacheln · Simple Mode · Bettleiste und
Bettauswahl · Objektleiste · Werkzeugschiene · Extruderbank ·
Schichtregler, auch senkrecht am Rand · Farbwechsel-, Pause- und
G-Code-Marken · Modifier hinzufügen · Schneiden · Vereinfachen ·
Zerlegen · Text und SVG prägen · ColorMix · Fernslicen mit
QR-Kopplung · Drucker verwalten · Ersteinrichtung · App-Einstellungen ·
Selbsttest · Absturzprotokoll teilen · Projekt und Ergebnis weitergeben ·
Objektliste mit Suche und Mehrfachauswahl · Profil unter Namen sichern ·
Bettform- und Reinigungsmatrix-Editor · Bemalen (seit 19.08.) ·
Mehrbett-Darstellung samt Bettzuordnung beim Ziehen (seit 19.08.).
