# Gleichstand der Oberflächen — vollständiger Befund und Umsetzungsplan

Stand 19.08.2026. **iOS ist die Referenz.** Alles hier beschreibt, was
Android nachziehen muss, damit beide denselben Stand haben. Die wenigen
Fälle, in denen Android weiter ist, stehen in Abschnitt Z.

## Wie dieser Befund entstanden ist

Vier Durchgänge, weil die ersten drei zu grob waren:

1. **Begriffe** — gibt es auf Android etwas, das dieselbe Aufgabe erfüllt?
   Findet fehlende Funktionen, sonst nichts.
2. **C-Schnittstelle** — alle 188 Funktionen gegen `android/jni/psm_jni.cpp`.
   Mechanisch und erschöpfend. **26 Funktionen fehlen.**
3. **Historie** — `git log --since="30 days" -- ios/`: **90 Commits fassen
   `ios/` an, ohne `android/` anzufassen**, zusammen **~5.700 geänderte
   Zeilen in 27 Ansichtsdateien**.
4. **Quelltext Datei für Datei** — die Fassung, aus der dieses Papier
   besteht.

Was der Quelltext **nicht** hergibt: Abstände, Rhythmus, Übergänge. Die
iOS-App habe ich nie gesehen; der Mac ist nicht erreichbar. Für
Gestaltungsfragen im engeren Sinn brauche ich Bildschirmfotos — siehe
ganz unten.

---

# 1 · Grundlagen, die überall wirken

## 1.1 Zielflächen schrumpfen unter das Mindestmaß

iOS rechnet jedes Maß einzeln um und **deckelt Zielflächen bei 44 pt**:

```swift
func pt(_ v: CGFloat)         -> CGFloat { v * factor }
func font(_ v: CGFloat)       -> CGFloat { v * PSScale.fontScaleFor(factor) }
func touch(_ v: CGFloat = 44) -> CGFloat { max(pt(v), 44) }   // ← Deckel
```

Android staucht stattdessen die Dichte global im Theme. Die Schrift ist
gedämpft (`FONT_FOLLOW = 0.6`, identisch zu iOS) — **Zielflächen sind es
nicht.** Bei `MIN_SCALE = 0.7` wird aus einem 48-dp-Knopf ein
33,6-dp-Knopf.

**Umsetzung**: ein `PsTouch`-Gegenstück zu `ps.touch()`, das die
Stauchung für Zielflächen rückgängig macht, und alle festen
Knopfhöhen darauf umstellen.

Das ist die Änderung mit der größten Breitenwirkung im ganzen Papier.

## 1.2 Zoll-Einheiten

`KEY_UNITS_IMPERIAL` wertet nur `ios/Screens/SettingField.swift` aus. Auf
Android steht der Schalter in der Liste „nur iOS" und ist versteckt.

---

# 2 · Advanced Mode

## 2.1 Schwebende Objektleiste über dem Bett

Die größte Einzeländerung. iOS zeigt `SimpleObjectBarView` **auch im
Advanced Mode** schwebend über dem Bett, sobald ein Objekt ausgewählt
ist. Im Quelltext steht warum:

> Im Advanced Mode fehlte sie — dort war jeder Handgriff ein Weg nach
> rechts.

Inhalt: Verschieben · Drehen · Skalieren · Kein — dann Schneiden ·
Teilen · Klonen · Ablegen · Hinlegen · Auf Fläche · Einpassen ·
Entfernen.

Android benutzt `SimpleObjectBar` **nur** in `SimpleModeScreen`.

## 2.2 Obere Werkzeugleiste

| iOS | Android |
|---|---|
| Start │ Neu · Öffnen · **Projekte** · Sichern · **Vorschau** │ Simple · **Leiste** | Start · Neu · Sichern · **Sichern unter** · Simple · Einstellungen |

Android fehlen **Öffnen**, **Projekte** (alle Projekte), der
**Vorschau-Umschalter** und der **Leiste-Umschalter**. iOS fragt beim
Sichern nach dem Namen, statt einen zweiten Knopf zu führen.

## 2.3 Untere Ansichtsleiste

iOS: **Zurück · Vor** │ 3D · Oben · Vorn · Hinten · Links · Rechts

> Zurück und Vor stehen am Anfang der unteren Leiste: sie sind das, was
> man am häufigsten braucht, und unten links liegt der Daumen ohnehin.

Android hat Rückgängig und Wiederholen in der **linken Schiene**, also
diagonal am weitesten weg vom Daumen.

Dazu die Wortwahl:

> Nicht „Iso": der Name ist in der CAD-Welt richtig und sonst nirgends.

Android sagt **Iso**, iOS **3D**.

## 2.4 Werkzeugschiene links

| | iOS | Android |
|---|---|---|
| Einträge | Import · Löschen · **Leeren** · **Anordnen** · Kopieren · Einfügen · +Kopie · −Kopie · **Trennen** (Untermenü) · **Stützen** · **Naht** | löschen · kopieren · mehr · weniger · in Objekte · in Volumen · Schichten · einfügen · zurück · vor · hinzufügen |
| Fußzeile | **Drucker · App-Einstellungen** | — |

*Trennen* fasst „Zu Objekten" und „Zu Volumen" in einem Untermenü
zusammen (`d4cf9a1`); Android führt beide nebeneinander. *Anordnen*
öffnet auf iOS per Tipp **und Halten**.

## 2.5 Zweiter Regler in der Vorschau

iOS hat **zwei** `DualHandleSlider`:

- links senkrecht — Schichtbereich
- unten waagerecht — **Werkzeugweg innerhalb der Schicht**

> wie der Desktop-Regler unter dem Bett

Android hat nur den senkrechten. Der zweite braucht
`psm_viewport_set_move_range` und `psm_viewport_move_range_bounds`.

## 2.6 Vorschau: Statistik und Legende fehlen ganz

- **Statistik**: Druckzeit, Filament in Metern, Gramm, Z-Bereich.
- **Legende**: Umschalter *Merkmale ↔ Extruder*, darunter je ein Chip
  mit Farbpunkt zum Aus- und Einblenden.
- **Verbrauch je Werkzeug** ab zwei Extrudern: Farbfeld, `T1`, cm³ im
  Modell, getrennt der Anteil im Reinigungsturm.

Nicht gebunden: `psm_preview_snapshot_get`, `psm_preview_role_at`,
`psm_preview_extruder_at`, `psm_preview_layer_at`,
`psm_viewport_set_preview_view`, `psm_viewport_set_role_visible`,
`psm_viewport_set_extruder_visible`, `psm_slice_load_gcode_for_preview`,
`psm_slice_extruder_count`, `psm_slice_extruder_at`.

Die Bereichsrechnung (`PreviewRange.swift`) gehört ins gemeinsame Modul,
damit beide Seiten dieselbe Zahl zeigen. Der Unit-Test dafür existiert
auf iOS schon.

## 2.7 Inspector öffnet sich nach der Auswahl

Vier Anläufe auf iOS (`3e45739`, `cb08ec1`, `bda94c2`, `8a53aa3`): nach
einer Auswahl klappt der Bearbeiten-Bereich von selbst auf, und der Fokus
bleibt beim Blättern layoutstabil. Auf Android muss man den Reiter selbst
wählen.

## 2.8 Einstellungen und Dialoge schweben über der Platte

> Die Einstellungen schweben über der Platte statt sie zu ersetzen: mit
> einem Rand ringsherum sieht man, dass es weiter um dieses Projekt geht.

Abgedunkelter Hintergrund, Tipp daneben schließt, 10 pt Rand auf schmalen
und 28 pt auf breiten Geräten. Dasselbe Muster (`SchwebenderDialog`) gilt
für **Ersteinrichtung**, **App-Einstellungen**, **Profilwechsel** und
**ZIP-Modus**. Android zeigt überall Vollbildseiten.

---

# 3 · Betten

## 3.1 Anordnen ohne Optionen

iOS öffnet ein Popover am Knopf: Zielmodus *aktuelles Bett ↔ alle
Betten*, Zielbettliste mit Schloss und Objektzahl, Abstand 0–50 mm in
Halbschritten, Schalter *Drehen erlauben*, danach ein Ergebnistext.

Android ruft `arrange()` mit Abstand 0, ohne Drehen, ohne Rückmeldung.
`psm_arrange_bed_ex` (mit `psm_arrange_info`: Status, Objekt- und
Instanzzahl, eigene Fehler für *gesperrt* und *voll*) ist nicht gebunden.

## 3.2 Bettleiste

Drei Umbauten auf iOS, keiner auf Android:

- **raus aus dem Easy Mode** (`04b2c3d`) — dort störte sie mehr als sie half
- **Kapselreihe** im Advanced (`c4402bb`)
- **Sperren, Umbenennen, Entfernen ins Kontextmenü** (`c4402bb`)

---

# 4 · Simple Mode

## 4.1 Leere Zustände

iOS hat dafür ein Muster (`leeresPanel`): eine Aussage plus den Knopf,
der herausführt.

| Lage | Knopf |
|---|---|
| Kein Material vorhanden | **Advanced Mode öffnen** |
| Keine Druckeinstellungen vorhanden | **Druckeinstellungen einrichten** |

Auf Android: leere Fläche, kein Weg heraus.

## 4.2 Material je Extruder

Abschnitt **„Je Extruder"** in der Materialseite. Fehlt auf Android.

## 4.3 Größenverhältnis je Objekt

Ein kleines Kästchen mit dem Größenverhältnis in **beiden** Objektlisten
(`0befe95`). Fehlt.

## 4.4 Projekt weitergeben

iOS teilt das **Projekt (.3mf)** aus dem Projektpanel, dazu die **Platte**
und **alle G-Code-Dateien auf einmal**. Android teilt nur eine
G-Code-Datei und das Protokoll.

---

# 5 · Material und Profile

## 5.1 Materialauswahl ist auf Android eine Liste

iOS: Suchfeld · **Typ-Knöpfe als echter Filter** · **Farbpunkte der
häufigsten Farben im Bestand** · Karten mit Hersteller, Typ, Farbe und
**einer echten Spule** (Ring mit Loch, `b9795d2`) · Schalter *inkompatible
zeigen*, standardmäßig aus (`61c1b47`).

Android: Suchfeld, Typ-Knöpfe die nur den Suchtext setzen, Liste der
ersten fünfzehn Treffer.

`FilamentCatalog` mit `types()`, `colors()` und `filter()` liegt seit
langem in `android/shared` — **und wird auf Android nicht benutzt.**

## 5.2 Einstellungskopf

iOS: Profilname mit **Lupe** → Profilsuche, daneben ein **Zähler der
geänderten Werte** mit Rückfrage vor dem Zurücksetzen.
Android: nur *Zurück* und die drei Reiter.

## 5.3 Druckerauswahl als Karten

iOS zeigt Drucker als Karten, in beiden Modi. Im Quelltext steht warum:
die Klappliste mit rohen Profilnamen war auf einem Gerät nicht bedienbar.
Android öffnet einen Dialog mit Suchliste.

---

# 6 · Slicen

## 6.1 Slice-Blatt

iOS nach dem Slicen:

- **„Geslict in 2:14"**
- bei Mehrbett **eine Zeile je G-Code-Datei** mit eigenem *Senden*-Knopf
- **Alle exportieren**
- **An Drucker senden** direkt aus dem Blatt
- Hinweis, wenn die Datei nicht geschrieben werden konnte

Android: *G-Code sichern*, *Schließen*.

## 6.2 Wortwahl

**„Export G-Code"** statt *Sichern* (`2213e04`) — bewusst, weil die Datei
die App verlässt.

---

# 7 · Weitere fehlende Funktionen

| | Fehlt auf Android | Kernfunktion |
|---|---|---|
| 7.1 | **ZIP-Import** samt Modusfrage | `psm_zip_extract_models` |
| 7.2 | **Adaptive Schichthöhe** (Qualitätsregler + Berechnen) | `psm_model_layer_profile_adaptive` |
| 7.3 | **ColorMix-Vorschau** vor dem Speichern | — |
| 7.4 | Fernslicen: **fertiges G-Code übernehmen** | `psm_slice_accept_remote_gcode`, `psm_slice_result_is_current` |
| 7.5 | Auf Fläche legen **je Kopie** | `psm_model_lay_on_facet_instance` |
| 7.6 | Zuggrenzen · Achsenbeschriftung am Griff · Legenden zu Schichthöhe und Bemalung | `psm_viewport_set_move_range`, `psm_viewport_move_range_bounds`, `psm_viewport_gizmo_axis_screen`, `psm_viewport_get_gizmo`, `psm_viewport_active_layer_visualization`, `psm_viewport_active_paint_visualization`, `psm_viewport_gesture_begin` |
| 7.7 | Feinere Bettlage als `outside_bed` | `psm_model_bed_state` |
| 7.8 | Innereien ohne Bedienelement | `psm_design_revision`, `psm_preset_option_at` |

## 7.9 Reinigungsturm sitzt woanders

iOS: X, Y und Drehung direkt in der Extruderbank, wo man ohnehin ist.
Android: in den Projektwerkzeugen.

---

# 8 · Hochformat und schmale Geräte

iOS hat dafür ausdrückliche Zweige:

- `schmal` — kein iPad **und** Fenster schmaler als 760 pt
- `leisteUnten` — im Hochformat die Leiste unten andocken statt rechts
- Seitenleiste höchstens **zwei Fünftel** der Breite
- **iPad-Seitenleiste bleibt auch hochkant ausgeklappt** (`2213e04`)
- **iPhone-Vorschau scrollbar**, sonst fiel der untere Regler aus dem
  Bottom-Sheet (`12f5b52`)
- Bettwähler und Seitenleiste dürfen nicht gleichzeitig schweben — sonst
  kollidieren Stift- und Schloss-Knöpfe

Android hat zwei Schalter (`compactNavigation`, `tightChrome`) und ist
**im Hochformat nie geprüft worden**. Hier erwarte ich weitere Befunde,
die nur am Gerät sichtbar werden.

---

# Z · Wo Android weiter ist

1. **Sonderwerte**: Bettform, Reinigungsmatrix, **Ramming**,
   **Ersetzungen**, **Kompatibilitätsregeln**, Hochladen von Bettmodell
   und -textur. iOS kennt nur die ersten beiden.
2. **Fuzzy Skin bemalen** — am 19.08. auf iOS nachgezogen, noch nicht
   gebaut.
3. **Blockieren bei der Naht** — am 19.08. auf beiden Seiten korrigiert.

---

# Reihenfolge

Zuerst, was jeden Bildschirm betrifft. Dann nach sichtbarem Gewinn.
Schritte ohne Kernbau laufen, während der Unraid baut.

| # | Schritt | Kern | Abschnitt |
|---|---|---|---|
| 1 | Zielflächen deckeln | — | 1.1 |
| 2 | Vorschau: Statistik, Legende, Verbrauch je Werkzeug | ja | 2.6 |
| 3 | Schwebende Objektleiste im Advanced | — | 2.1 |
| 4 | Materialauswahl auf `FilamentCatalog` | — | 5.1 |
| 5 | Leere Zustände Simple Mode | — | 4.1 |
| 6 | Einstellungskopf: Profilsuche, Zurücksetzen | — | 5.2 |
| 7 | Schwebende Dialoge statt Vollbildseiten | — | 2.8 |
| 8 | Werkzeugleisten: obere, untere, linke Schiene | — | 2.2–2.4 |
| 9 | Anordnen mit Optionen | ja | 3.1 |
| 10 | Adaptive Schichthöhe | ja | 7.2 |
| 11 | Zweiter Regler in der Vorschau | ja | 2.5 |
| 12 | Bettleiste: Easy Mode, Kapseln, Kontextmenü | — | 3.2 |
| 13 | Slice-Blatt: Dauer, Dateiliste, Senden | — | 6.1 |
| 14 | Druckerkarten | — | 5.3 |
| 15 | Inspector öffnet nach Auswahl | — | 2.7 |
| 16 | Projekt und Platte weitergeben | — | 4.4 |
| 17 | Größenverhältnis je Objekt | — | 4.3 |
| 18 | Material je Extruder | — | 4.2 |
| 19 | ColorMix-Vorschau | — | 7.3 |
| 20 | ZIP-Import | ja | 7.1 |
| 21 | Reinigungsturm in die Extruderbank | — | 7.9 |
| 22 | Zoll-Einheiten | — | 1.2 |
| 23 | Restliche Bindungen | ja | 7.4–7.8 |
| 24 | Hochformat prüfen und nachziehen | — | 8 |
| 25 | iOS nachziehen (braucht den Mac) | — | Z |

Jeder Schritt endet gleich: bauen, Emulator, Bildschirmfoto. Ohne den
Beleg gilt er nicht als fertig.

---

# Geprüft und gleich

Startseite mit Zuletzt-Kacheln · Objektleiste im Simple Mode ·
Extruderbank samt Farbwahl mit eigenem Hexwert · Schichtregler senkrecht
am Rand · Farbwechsel-, Pause- und G-Code-Marken · Modifier hinzufügen ·
Schneiden · Vereinfachen · Zerlegen · Text und SVG prägen · Extruder je
Teil · Objektliste mit Suche und Mehrfachauswahl · Profil unter Namen
sichern · Bettform- und Reinigungsmatrix-Editor · gesperrte Einstellung
mit Begründung · Fernslicen mit QR-Kopplung · Drucker verwalten ·
Ersteinrichtung · App-Einstellungen · Selbsttest · Absturzprotokoll
teilen · Bemalen (19.08.) · Mehrbett samt Bettzuordnung beim Ziehen
(19.08.).

---

# Was ich für den Rest brauche

Fünf Bildschirmfotos von iOS wären am nützlichsten, weil daraus genau
das hervorgeht, was im Quelltext nicht steht — Abstände,
Größenverhältnisse, Anordnung:

1. **Advanced Mode mit ausgewähltem Objekt** — die schwebende Leiste
2. **Vorschau nach dem Slicen** — Statistik und Legende
3. **Materialauswahl** — Farbpunkte, Karten, Spule
4. **Simple Mode, Seite Einstellungen**
5. **Hochformat auf dem iPhone** — Advanced Mode
