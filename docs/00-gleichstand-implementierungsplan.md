# Implementierungsplan: Gleichstand der Oberflächen

**Dieses Dokument ist die Quelle für alle Fragen zum Stand von Android
gegenüber iOS.** Wer an der Oberfläche arbeitet, liest es vor dem
Arbeitsjournal und trägt Erledigtes hier ein.

Angelegt 19.08.2026. Sprache: Deutsch, wie der übrige Bestand in `docs/`
und die Quelltextkommentare.

---

## Stand und nächster Schritt

**Dieser Abschnitt wird nach jedem Arbeitspaket fortgeschrieben.** Er ist
das, was eine neue Sitzung als Erstes liest — hier steht, wo genau
weitergemacht wird, ohne dass jemand die Historie durchsuchen muss.

**Zuletzt geändert:** 20.08.2026 · Zweig
`codex/ios-android-parity-implementation` · letzter Commit `ec82032`

### Wo die Arbeit liegt

Es gibt drei Kopien dieses Baums, und nur eine davon wird bearbeitet.
Wer das verwechselt, baut ein Paket ein zweites Mal — genau das ist am
19.08. passiert.

| Kopie | Pfad | Wofür |
|---|---|---|
| **Arbeitskopie** | `C:\Users\Nils\.codex\worktrees\psmobile-ios-android-parity-sidebuild-local\.worktrees\ios-android-parity-implementation` | **Hier wird geschrieben, gebaut und committet.** Zweig `codex/ios-android-parity-implementation`. |
| Build-Host | `\\Localunraid\n3dp\KI Projekte\psmobile-parity-buildhost\repo` (dort `/mnt/user/N3DP/…`) | Nur der native Kern im Docker. Von Windows aus **nicht schreibbar**; sein Git steht auf dem Stand vom 10.08. |
| Alter Hauptcheckout | `\\Localunraid\n3dp\KI Projekte\PSMobile` | Stand vom 09.08., Elternverzeichnis der Worktrees. Nicht die laufende Arbeit. |

Die Fassungen dieses Plans auf dem Build-Host und im alten Checkout sind
**Kopien**. Maßgeblich ist die in der Arbeitskopie.

**Android bauen** (Windows, aus der Arbeitskopie):

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" \
  ./android/gradlew -p android :app:assembleProductionDebug
```

`ANDROID_SDK_ROOT` steht auf `S:\PC-Auslagerung\Android\Sdk` — kein
`ANDROID_HOME` dazusetzen, sonst bricht Gradle mit „several environment
variables … contain different paths to the SDK" ab. Die APK liegt
danach als `app-production-x86_64-debug.apk` für den Emulator und als
`…-arm64-v8a-…` fürs Gerät.

**Kern bauen** (nur auf dem Unraid, ~20–25 min):

```bash
ssh -i ~/.ssh/unraid_aipp root@100.109.46.54
cd "/mnt/user/N3DP/KI Projekte/psmobile-parity-buildhost/repo"
bash build/scripts/build-core.sh && bash build/scripts/stage-native.sh
```

`stage-native.sh` legt die `.so` unter `android/app/src/main/jniLibsFixed/`
ab; von dort gehört sie in die Arbeitskopie kopiert. Achtung: `du` meldet
über die Freigabe falsche Größen — für echte Zahlen `ls -l`.

### Erledigt

| Paket | Commit | Beleg am Emulator |
|---|---|---|
| AP-01 Zielflächen | `75c8dd1` | 118 Stellen, `psTouch()` |
| AP-02 Eckenradien (Android) | `75c8dd1`, `11c7ebb` | 164 Stellen, keine rohen Radien mehr |
| AP-03 Vorschau | `3377a96` | `0:30 · 3.59 m · 10.7 g`, Chips grauen aus |
| AP-07 Materialauswahl | `875a655` | Typ-Filter PLA blendet auf vier Karten ein |
| AP-06 Schwebende Dialoge | `469203a`, `1e785c0` | Einstellungen als Karte über dem Bett, Rand 104/120/80 px |
| AP-17 d ColorMix-Vorschau | `492799b` | Vorschau `#ED6941` vor dem Speichern |
| AP-17 a Weitergeben | `18817d5`, `5605ea0` | *Share · PSMobile-Druckbett.stl* nach dem Export |
| AP-17 e Reinigungsturm | `431d961` | steht unter der Extruderbank |
| AP-17 f Zoll-Einheiten | `bcb4361` | `0.0039 in` statt `0.1 mm` |
| AP-17 b Größenverhältnis | `d919c3d` | Kästchen in der Objektliste |
| AP-15 Druckerkarten | `50de001` | Karte mit Modell, Zustand, Düse |
| AP-16 Inspector | `82f3fa8` | Auswahl zeigt EDIT samt Griffen |
| AP-12 Bettleiste | `f03c7b0` | langer Druck zeigt *Rename* |
| AP-05 Werkzeugleisten | `f489a48`, `c41a1ba`, `f0c314a` | Schiene mit Trennen, Stützen, Naht |
| AP-11 Slice-Blatt | `8afd69d`, `c63e329` | `2 G-code files`, *Export all*, Zeile je Datei |
| AP-22 Alle Betten schneiden | `6bd3438` | `bett-1.gcode`, 53020 Bytes |
| AP-20 Bereiche statt Reiter | `2e5f5ec` | drei Einstellungszeilen, vier Überschriften, Schneiden-Block außerhalb |
| AP-08 Einstellungskopf | `bb47ae1` | Profilname mit Trichter, Zähler `1`, Rückfrage nennt „Perimeters 2 → 4" |
| AP-09 Leere Zustände | `875a655` | `LeeresPanel` als Muster |
| AP-18 Bindungen | `27ac6eb`, `1eb4c97` | 26 fehlende Funktionen → noch 2 |
| Bemalen (Strich, Füllmodi) | `e2f9dad`, `9da2adf` | Spur statt Punkt, 1358 Facetten |
| AP-26 Schichtprofil ehrlich | `ec82032` | *Not valid* statt erfundener Vorschau, Erklärzeile darüber |
| AP-25 Selbsttest | `712baa1` | *Run all checks*, Abbrechen mit übersprungenen Schritten, *Cancelled* |
| Beschriftungen angeglichen | `e25e22d` | *Export log* statt *Share log*, *Recent* statt *RECENT* |
| AP-24 Simple-Vorschau + Editor-Knopf | `091fc03` | *Finaler G-Code* rechts am Rand, *Editor* führt zurück |
| AP-24 Viewer statt schwebender Karte | `5ab12eb` | Band sagt *Preview*, Legende bricht um, über dem Bett steht nichts |
| AP-11 Slice-Blatt (Simple) | `feb230a` | *Send to printer* unter dem Export, Hinweis wenn nichts geschrieben wurde |
| Kleinigkeiten | `86c1842` | *Sliced in <1s* statt *0m*; *Wipe into infill* statt deutscher Beschriftung |
| AP-17 g ZIP-Import | `7298439` | *2 models came from a ZIP archive.*, Bett zeigt 3 Objekte |
| AP-14 Zweiter Regler | `ed84cd5` | Werkzeugweg 10–7189 unten, Schichtregler links |
| AP-13 Adaptive Schichthöhe | `0418edc` | *Compute* füllt 81 Höhenbereiche, *Apply* wird aktiv |
| AP-10 Anordnen mit Optionen | `ae316ed` | *Bed 2 is empty…* und *Bed 1: 1 instances arranged.* im Feld am Knopf |
| Bettzuordnung beim Ziehen | `6f17c85` | `Bed 1 · 0 / Bed 2 · 1` |

### Teilweise

- **AP-04** Objektleiste steht im Advanced Mode (`78ecc90`). Offen: iOS
  führt die Griffe *Verschieben · Drehen · Skalieren · Kein* am Anfang
  derselben Leiste, Android hat sie getrennt.
- **AP-09** Das Muster steht und ist in der Materialauswahl im Einsatz.
  Offen: dieselbe Behandlung für *Keine Druckeinstellungen vorhanden*.

### Als Nächstes

**Nachtrag vom 20.08.:** „Nichts mehr offen" stimmte nicht ganz. Ein
Abgleich der Beschriftungen Bildschirm für Bildschirm (iOS `st(…)` gegen
Android `appText(…)`) hat gezeigt, dass **AP-11 nur zur Hälfte umgesetzt
war**: gebaut wurde es in der Advanced-Seitenleiste, die Vorlage im Plan
(`Screens/SliceSheet.swift`) ist aber das **Simple-Mode-Blatt** — und
dort fehlten *An Drucker senden*, die Zeile je Datei und der Hinweis,
wenn keine Datei entstand. Das ist nachgeholt, siehe AP-11.

Der Abgleich läuft als Skript und lässt sich wiederholen; er findet
fehlende und zusätzliche Bedienelemente, sagt aber nichts über die
Reihenfolge — die muss man sich weiter ansehen.

**Neu von Nils (20.08.):** das schwebende Menü über dem Bett soll weg,
und rechts soll zwischen einer **Viewer-** und einer **Editor**-Ansicht
umgeschaltet werden. Umgesetzt als **AP-24**, in beiden Modi
fertig und belegt. Der Simple Mode hatte die Vorschau-Legende gar nicht;
wohin sie gehört, war keine offene Frage, sondern steht in
`FinalPreviewPanel.swift`.

Ebenfalls von ihm vorgemerkt: **iOS fehlt der Werkzeugweg-Regler** aus
AP-14. Steht als Punkt 7 in Abschnitt Z, braucht den Mac.

**Aus dem Beschriftungsabgleich (20.08.), erledigt:**

- *Share log* hieß drüben *Export log*. Die Datei verlässt die App —
  genauso heißt es beim G-Code. Angeglichen; Androids ausführlicherer
  Untertext (nennt, was drinsteht und was nicht) bleibt, er ist der
  bessere und gehört als Z-Punkt nach drüben.
- Die Überschrift der Zuletzt-Kacheln stand in Versalien (*RECENT*),
  drüben schlicht *Recent*. Angeglichen.

**Aus demselben Abgleich, bewusst nicht entschieden** — das sind Fragen
an Nils, keine, die beim Bauen nebenbei beantwortet werden:

1. **Schreibweise *colour* ↔ *color*.** Android schreibt 39-mal
   *colour* und 5-mal *color*, iOS 4-mal *color* und 1-mal *colour*.
   **Beide Seiten sind in sich uneinheitlich.** Eine Entscheidung, dann
   ein Durchgang auf beiden Seiten.
2. **ColorMix heißt überall anders.** *Add mixed colour* ↔ *Create a
   mixed color*; *Remove* ↔ *Delete mixed color*; *Choose heads (2–3)* ↔
   *Choose two or three physical positions…*; *First-head share* ↔
   *Share of position N*. iOS führt zusätzlich die Überschriften *Save
   mixed color* und *Saved mixed colors*. Hier ist nicht offensichtlich,
   welche Seite recht hat.
3. **Alle Projekte.** iOS öffnet einen eigenen Bildschirm *All
   projects* mit *Löschen* je Zeile und *Fertig*; Android klappt die
   Liste an Ort und Stelle auf (*Show all* ↔ *Show less*) und löscht
   dort. Beide können dasselbe. Androids Weg spart einen Bildschirm —
   Empfehlung: Android behalten und iOS nachziehen, aber das ist deine
   Entscheidung.

**Beobachtung, kein Paket:** beim Kaltstart nach einem erzwungenen Stopp
kam einmal ein ANR — `executing service de.psmobile/.slicing.SlicerService,
waited 20176ms`. Nicht die Startseite, sondern der Dienst beim Hochfahren
des Kerns. Ein sauberer Neustart danach war unauffällig. Auf schwächeren
Geräten könnte das echt werden; ein Fall zum Nachmessen, nicht zum
Raten.

**Sonst ist ohne den Mac nichts mehr offen.** Alle Android-Pakete des Plans
sind zu; die Behauptung „Kern: ja" bei AP-10, AP-13, AP-14 und AP-17 g
war in allen vier Fällen falsch — die Bindungen lagen längst in
`android/jni/psm_jni.cpp` und in der abgelegten `libpsmobile_core.so`.
Das ist inzwischen der dritte Anlass für dieselbe Regel: **im Quelltext
von heute nachsehen, nicht in der Historie.**

Was bleibt:

- **AP-04** (Griffe am Anfang der Objektleiste), **AP-02 iOS**
  (~160 Radien) und Abschnitt **Z** (sechs Punkte, an denen iOS
  nachzieht) — alle **brauchen den Mac**.
- **AP-19 Hochformat** und **AP-23 Werkzeugbereich** sind
  zurückgestellt. AP-23 braucht zuerst eine Entscheidung von Nils: die
  Werkzeuge sehen auf beiden Seiten unterschiedlich aus, und auf iOS
  gefallen sie ihm auch nicht — hier wird also nicht kopiert, sondern
  beides zusammen neu entworfen.
- **Der Symboldurchgang** kommt laut Nils zum Schluss und ist bewusst
  noch nicht angefasst.

Zwei Kleinigkeiten sind dabei mit erledigt:

- `SliceSummary.duration()` meldete für Läufe unter einer Minute „0m".
  Jetzt Sekunden, und unter einer Sekunde „<1s" — ein halbsekündiger
  Schnitt hätte sonst „0s" ergeben, dieselbe Null in kleinerer Einheit.
  In der gemeinsamen Regel, also auf beiden Plattformen; zwei Tests
  decken es ab.
- Ein Schalter in den Werkzeugen hieß auch auf Englisch „In Infill
  wischen". Der Rest der Zeile stand längst zweisprachig daneben.

### Worauf zu achten ist

- **Kernbau dauert 20–25 Minuten.** Wer einen braucht, stößt ihn zuerst
  an und arbeitet währenddessen an einem Paket ohne Kern.
- **Neue Dialoge gehören in `ScaledOverlay`.** Sie rendern in einem
  eigenen Fenster und erben die gestauchte Dichte nicht. Ein Regeltest
  wacht darüber.
- **`psTouch()` statt fester dp-Höhen** an allem, was man antippt.
- **`Corners.SHEET/CARD/FIELD/PILL`** statt roher Radien.
- **Kein Paket gilt als fertig ohne Bildschirmfoto vom Emulator.**
- **Das rechte Band scrollt nur auf langsame Wischer.** `adb shell input
  swipe … 900` (Millisekunden) bewegt es; ein schneller Wisch (250 ms)
  wird verschluckt, und man haelt die Leiste faelschlich fuer festgefahren.
- Der Katalog in `filamentCatalog()` wird gemerkt. Wer die Profilliste
  ändert, muss ihn nicht selbst verwerfen — er hängt an den Namen.

### Blockiert

- **Der Mac** unter `192.168.1.107` antwortet nicht — er ist ab dem
  **20.08.2026** wieder da. Bis dahin liegen die iOS-Seite von AP-02
  (~160 Stellen), der Rest von AP-04 und der ganze Abschnitt Z still.
  Geschriebener, aber ungebauter Swift-Code zählt nicht als erledigt.
- **AP-19 Hochformat ist zurückgestellt** (Nils, 19.08.). Nicht
  geschätzt, weil Android dort nie gelaufen ist, und es steht hinter
  allem anderen: erst soll die Bedienung im Querformat auf beiden
  Geräten gleich sein. Wird angefasst, wenn die übrigen Pakete zu sind.

---

## Grundsatz

**iOS ist die Referenz. Android zieht nach.**

**Das Ziel ist dieselbe Bedienung, nicht nur derselbe Funktionsumfang:**
dieselbe Anordnung, dieselbe Reihenfolge, derselbe Ort, dieselben Namen
— in beiden Modi. Es soll egal sein, auf welchem Geraet jemand sitzt
(Nils, 19.08.).

Zwei Dinge sind davon ausgenommen, beide auf Nils' Ansage:

- **Systemeigenes darf abweichen.** Dateiwaehler, Teilen-Blatt,
  Zurueck-Geste — und was daran haengt. *Sichern unter* auf Android ist
  so ein Fall: der Name kommt dort aus dem Systemdialog. Solche
  Unterschiede sind erlaubt und brauchen keine Angleichung.
- **Symbole kommen zuletzt.** Sie werden am Ende in einem eigenen
  Durchgang vereinheitlicht. Bis dahin ist ein abweichendes Symbol kein
  offener Punkt — eine abweichende **Anordnung** dagegen schon.

Das war nicht immer so — die README nannte lange „Android-first". Seit
dem Aufbau der iOS-Fassung im August ist es umgekehrt: dort sind die
Gestaltungsentscheidungen gefallen, dort wurden sie mehrfach überarbeitet.
Wer eine Abweichung findet, gleicht Android an iOS an, nicht umgekehrt.

Ausnahmen stehen in Abschnitt Z. Sie sind bewusst wenige.

---

## Wie der Befund entstanden ist

Vier Durchgänge, weil die ersten drei zu grob waren. Wer den Befund
erweitern will, nimmt Durchgang 3 und 4 — die anderen beiden finden zu
wenig.

| # | Verfahren | Findet | Blind für |
|---|---|---|---|
| 1 | Begriffe suchen | fehlende Funktionen | alles andere |
| 2 | C-Schnittstelle vergleichen | nicht gebundene Kernfunktionen | reine Oberfläche |
| 3 | `git log --since=… -- ios/` | Entscheidungen, die nie zurückkamen | — |
| 4 | Quelltext Datei für Datei | Ausarbeitung, Wortwahl, Anordnung | Abstände, Rhythmus |

Zahlen aus Durchgang 2 und 3:

- **26** Funktionen der C-Schnittstelle nutzte iOS, ohne dass Android sie
  band. **Stand 19.08.: noch 2** — `psm_viewport_active_layer_visualization`
  und `psm_viewport_active_paint_visualization`, beide nur Legenden.
- **90** Commits fassen `ios/` an, ohne `android/` anzufassen.
- **~5.700** geänderte Zeilen in **27** iOS-Ansichtsdateien, nie übertragen.

Was kein Verfahren hergibt: Abstände, Rhythmus, Übergänge. Dafür braucht
es Bildschirmfotos der iOS-App — siehe Abschnitt „Offene Fragen".

---

## Arbeitspakete

Jedes Paket hat dieselbe Form: **Ziel**, **Fundstelle iOS**, **Zustand
Android**, **Umsetzung**, **Fertig wenn**. Ein Paket gilt erst als
fertig, wenn es gebaut, auf dem Emulator angesehen und mit einem
Bildschirmfoto belegt ist.

Die Spalte „Kern" sagt, ob der native Kern neu gebaut werden muss. Pakete
ohne Kernbau lassen sich erledigen, während auf dem Unraid ein Kern baut.

---

### AP-01 · Zielflächen deckeln · Kern: nein

**Ziel** Kein Bedienelement fällt unter das Mindestmaß von 44 dp, egal
wie klein das Fenster ist.

**Fundstelle iOS** `ios/PSMobile/UI/PSScale.swift`

```swift
func touch(_ v: CGFloat = 44) -> CGFloat { max(pt(v), 44) }
```

**Zustand Android** `ui/theme/Theme.kt:141` staucht die Dichte global.
Die Schrift ist über `FONT_FOLLOW = 0.6` gedämpft — identisch zu iOS —,
Zielflächen sind es nicht. Bei `MIN_SCALE = 0.7` wird aus einem
48-dp-Knopf ein 33,6-dp-Knopf.

**Umsetzung** Ein `PsTouch`-Gegenstück, das die Stauchung für
Zielflächen zurückrechnet, und alle festen Knopfhöhen darauf umstellen.

**Fertig wenn** Auf einem Fenster an der Untergrenze misst kein Knopf
weniger als 44 dp.

**Stand: erledigt** (19.08., `75c8dd1`). `psTouch()` in
`ui/theme/Theme.kt`, 118 Fundstellen zwischen 40 und 58 dp umgestellt.
Was darüber liegt, bleibt: Kartenhöhen sind eine Gestaltungsfrage, keine
Trefferfrage.

**Warum zuerst** Das Paket wirkt auf jedem Bildschirm. Alles danach baut
darauf auf.

---

### AP-02 · Eckenradien vereinheitlichen · Kern: nein

**Ziel** Eine Skala für beide Plattformen, statt siebenundzwanzig
gewachsener Werte.

**Befund** iOS kennt zwölf verschiedene Radien (1 bis 16, am häufigsten
3 und 4), Android fünfzehn (1 bis 18, am häufigsten 8 und 10). Beide
Seiten sind in sich uneinheitlich und untereinander verschieden.

Aufgefallen an der Filamentauswahl: die ist auf iOS ein SwiftUI-`.sheet`
und bekommt Apples eigene, großzügige Rundung geschenkt, während der
Einstellungs-Panel daneben auf `cornerRadius: 3` steht. Nebeneinander
sieht das aus wie zwei Anwendungen. Auf Android steht an derselben
Stelle `RoundedCornerShape(3.dp)` — derselbe Wert, derselbe Eindruck.

**Umsetzung** `shared/ui/Corners.kt`, wie `WindowScale` im gemeinsamen
Modul:

| Wert | Radius | Wofür |
|---|---|---|
| `SHEET` | 28 | Blätter, große Dialoge |
| `CARD` | 14 | Karten, Panels, schwebende Leisten |
| `FIELD` | 8 | Felder, Knöpfe, Zeilen |
| `PILL` | rund | Typfilter, Bettkapseln, Chips |

**Stand: Android erledigt** (19.08., `75c8dd1`). 164 Fundstellen
umgestellt: bis 9 dp `FIELD`, 10 bis 12 `CARD`, darüber `SHEET`.
`grep -c 'RoundedCornerShape([0-9]'` findet im `ui`-Paket nichts mehr.

**Offen: iOS**, rund 160 Fundstellen. Der Mac ist nicht erreichbar, also
weder zu schreiben noch zu prüfen.

---

### AP-03 · Vorschau: Statistik, Legende, Verbrauch · Kern: ja

**Ziel** Nach dem Slicen dieselbe Auskunft wie auf iOS.

**Fundstelle iOS** `Screens/FinalPreviewPanel.swift`,
`Screens/AdvancedWorkspaceView.swift:832ff`

- Statistikzeile: Druckzeit, Filament in Metern, Gramm, Z-Bereich
- Zusammenfassung in der Seitenleiste: Druckzeit, Material, Filament,
  **Kosten**, Objekte
- Legende: Umschalter *Merkmale ↔ Extruder*, darunter je ein Chip mit
  Farbpunkt zum Aus- und Einblenden
- Verbrauch je Werkzeug ab zwei Extrudern: Farbfeld, `T1`, cm³ im
  Modell, getrennt der Anteil im Reinigungsturm

**Zustand Android** Nichts davon.

**Nicht gebunden** `psm_preview_snapshot_get`, `psm_preview_role_at`,
`psm_preview_extruder_at`, `psm_preview_layer_at`,
`psm_viewport_set_preview_view`, `psm_viewport_set_role_visible`,
`psm_viewport_set_extruder_visible`, `psm_slice_load_gcode_for_preview`,
`psm_slice_extruder_count`, `psm_slice_extruder_at`

**Umsetzung**

1. Die zehn Funktionen in `android/jni/psm_jni.cpp` binden. Listen gehen
   wie `nativeSurfacePick` als Tab-getrennte Zeichenkette zurück, eine
   Zeile je Eintrag — kein eigener JNI-Objektbau.
2. `PsmCore.kt`: `PreviewSnapshot`, `PreviewRole`, `PreviewExtruder`,
   `ExtruderUsage` als Datenklassen, spiegelbildlich zu `PsmCore.swift`.
3. `PsmViewport.kt`: `previewView`, `setRoleVisible`, `setExtruderVisible`.
4. Die Bereichsrechnung aus `UI/PreviewRange.swift` nach
   `shared/rules/PreviewRange.kt` ziehen, damit beide Seiten dieselbe
   Zahl zeigen. Der Unit-Test dafür existiert auf iOS bereits.
5. `PreviewStatsRow` und `PreviewLegendPicker` in Compose, eingesetzt
   unter dem Viewport und in `SimpleSliceSheet`.

**Fertig wenn** Nach dem Slicen steht dieselbe Statistik da wie auf iOS,
und ein abgeschalteter Merkmals-Chip blendet die Wege im Viewport aus.

**Stand: erledigt** (19.08., `3377a96`). Belegt am Emulator: `0:30 ·
3.59 m · 10.7 g · 0.20–20.00 mm`, Umschalter Merkmale/Extruder, Chips
mit Farbpunkt, und *Perimeter* und *Solid infill* lassen sich ausgrauen.
`PreviewRange` liegt im gemeinsamen Modul mit sechs Tests.

Der Verbrauch je Werkzeug ist eingebaut, aber noch **nicht am Geraet
gesehen** — er erscheint erst ab zwei wirklich druckenden Extrudern.

---

### AP-04 · Schwebende Objektleiste im Advanced Mode · Kern: nein

**Ziel** Was man am ausgewählten Objekt am häufigsten tut, liegt am
Objekt und nicht in einer Spalte am Rand.

**Fundstelle iOS** `AdvancedWorkspaceView.swift:795ff` setzt
`SimpleObjectBarView` schwebend über das Bett. Begründung im Quelltext:

> Im Advanced Mode fehlte sie — dort war jeder Handgriff ein Weg nach
> rechts.

Inhalt: Verschieben · Drehen · Skalieren · Kein — dann Schneiden ·
Teilen · Klonen · Ablegen · Hinlegen · Auf Fläche · Einpassen ·
Entfernen.

**Zustand Android** `SimpleObjectBar` wird nur in `SimpleModeScreen`
verwendet.

**Umsetzung** Dieselbe Leiste in `SlicerScreen` über dem Viewport
einsetzen, sobald ein Objekt ausgewählt und die Vorschau nicht aktiv ist.
Sie gehört **in** den Viewport-Bereich, nicht in die äußere Anordnung —
sonst muss der Abstand zur Werkzeugleiste geraten werden. Auf iOS war
genau das der Fehler, der zweimal korrigiert wurde.

**Fertig wenn** Objekt auswählen zeigt die Leiste mittig über dem Bett.

**Stand: teilweise** (19.08.). Die Leiste steht im Advanced Mode und ist
am Emulator belegt. **Offen: der Inhalt weicht ab.** iOS führt die Griffe
*Verschieben · Drehen · Skalieren · Kein* am Anfang derselben Leiste,
Android hat sie im Simple Mode in einer eigenen Spalte
(`SimpleWerkzeugSpalte`) und im Advanced Mode nur im Seitenband. Dafür
hat Android *Ziehen zu*, *Entfernen* und *Zurück*, die iOS dort nicht
führt. Beides muss auf iOS angeglichen werden.

---

### AP-05 · Werkzeugleisten angleichen · Kern: nein

**Obere Leiste**

| iOS | Android |
|---|---|
| Start │ Neu · Öffnen · Projekte · Sichern · Vorschau │ Simple · Leiste | Start · Neu · Sichern · Sichern unter · Simple · Einstellungen |

Android fehlen **Öffnen**, **Projekte**, der **Vorschau-Umschalter** und
der **Leiste-Umschalter**. iOS fragt beim Sichern nach dem Namen, statt
einen zweiten Knopf zu führen.

**Untere Leiste** Auf iOS stehen **Zurück und Vor** am Anfang:

> sie sind das, was man am häufigsten braucht, und unten links liegt der
> Daumen ohnehin

Auf Android liegen sie in der linken Schiene, diagonal am weitesten weg.
Dazu die Wortwahl: iOS nennt die Ansicht **3D**, nicht *Iso* —

> Iso ist in der CAD-Welt richtig und sonst nirgends.

**Linke Schiene**

| | iOS | Android |
|---|---|---|
| Einträge | Import · Löschen · Leeren · Anordnen · Kopieren · Einfügen · +Kopie · −Kopie · Trennen (Untermenü) · Stützen · Naht | löschen · kopieren · mehr · weniger · in Objekte · in Volumen · Schichten · einfügen · zurück · vor · hinzufügen |
| Fußzeile | Drucker · App-Einstellungen | — |

*Trennen* fasst „Zu Objekten" und „Zu Volumen" zusammen. *Anordnen*
öffnet per Tipp **und Halten**.

**Stand: erledigt** (20.08., `f489a48`, `c41a1ba`, `f0c314a`). Obere
Leiste, untere Leiste, Fußzeile und Schiene stehen.

Die Schiene liest jetzt: Import · Löschen · Leeren · Anordnen ·
Kopieren · Einfügen · +Kopie · −Kopie · Optionen · **Trennen** ·
**Stützen** · **Naht**, darunter die Fußzeile mit Drucker und
App-Einstellungen. *Anordnen* nimmt beim Tippen alle Betten und beim
Halten nur das aktuelle — der eigene Knopf *Aktuelles Bett* entfällt.
Das Trennen-Menü zeigt *To objects* und *To parts*.

*Stützen* und *Naht* kommen nicht aus PrusaSlicers `toolbar.json` — als
Leistenknopf gibt es sie dort nicht —, sondern von Hand hinter der
Schleife. Zweites Tippen legt das Werkzeug wieder weg.

---

### AP-06 · Schwebende Dialoge statt Vollbildseiten · Kern: nein

**Ziel** Man verliert nie den Kontext.

**Fundstelle iOS** `Screens/ProfilWechselDialog.swift` stellt
`SchwebenderDialog` bereit; verwendet von Einstellungen, Ersteinrichtung,
App-Einstellungen, Profilwechsel und ZIP-Frage. Muster: abgedunkelter
Hintergrund, Tipp daneben schließt, 10 pt Rand auf schmalen und 28 pt auf
breiten Geräten.

> Die Einstellungen schweben über der Platte statt sie zu ersetzen: mit
> einem Rand ringsherum sieht man, dass es weiter um dieses Projekt geht.

**Zustand Android** Überall Vollbildseiten.

**Achtung** Auf Android rendern Dialoge in einem eigenen Fenster und
erben `LocalDensity` nicht. Jeder neue Dialog gehört in `ScaledOverlay`,
sonst ist er falsch skaliert. Ein Regeltest wacht darüber.

**Stand: erledigt** (19.08., `469203a`, `1e785c0`).
`SchwebenderDialog` steht in `ui/SchwebenderDialog.kt` und trägt
**Einstellungen, Drucker (beide Wege), ColorMix, App-Einstellungen und
Ersteinrichtung**. Belegt am Emulator: die Einstellungen stehen als
Karte über dem Bett, Werkzeugleiste und Seitenband schauen ringsum
hervor; App-Einstellungen und Druckerverwaltung stehen als 900-dp-Karte
über der abgedunkelten Startseite. Ein Tipp daneben schließt.

Die Ersteinrichtung kennt `abbrechbar = false`: beim allerersten Start
gibt es keinen Weg hinaus, dort darf weder ein Tipp daneben noch die
Zurück-Geste schließen.

**Bewusst nicht umgestellt:** Profilwechsel und ZIP-Frage. Beide sind
kurze Rückfragen mit zwei bis drei Knöpfen und liegen schon im
`AlertDialog` aus `ui/theme`, der `ScaledOverlay` selbst mitbringt. In
eine formatfüllende Karte gefasst wären sie größer als ihre Frage.

**Noch offen, aber keine Vollbildseite aus der Liste:**
`RemoteSliceScreen` ersetzt weiterhin den Bildschirm. Es hat auf iOS
kein Gegenstück, deshalb sagt der Vergleich hier nichts — erst ansehen,
dann entscheiden.

**Der Fallstrick, den der nächste kennen sollte:** mit
`usePlatformDefaultWidth = false` ist das Dialogfenster so hoch wie der
Bildschirm, sitzt aber unter der Statusleiste — es ragt genau um deren
Höhe unten heraus (gemessen: 104 px Rand oben, 8 px unten). Weder
`windowInsetsPadding` noch `decorFitsSystemWindows` noch
`FLAG_LAYOUT_NO_LIMITS` ändern daran etwas. Die Karte rechnet ihre Größe
deshalb aus der Ansicht der Aktivität minus Systemleisten und hängt oben
an. Wer einen weiteren schwebenden Bildschirm baut, nimmt
`SchwebenderDialog` und misst nicht neu.

---

### AP-07 · Materialauswahl · Kern: nein

**Fundstelle iOS** `Screens/MaterialAuswahlView.swift`: Suchfeld ·
Typ-Knöpfe als echter Filter · Farbpunkte der häufigsten Farben im
Bestand · Karten mit Hersteller, Typ, Farbe und einer echten Spule
(Ring mit Loch) · Schalter *inkompatible zeigen*, standardmäßig aus.

**Zustand Android** `SimpleModeScreen.kt:1039ff`: Suchfeld, Typ-Knöpfe
die nur den Suchtext setzen, Liste der ersten fünfzehn Treffer.

**Umsetzung** Auf `FilamentCatalog` umstellen — der liegt seit langem in
`android/shared` mit `types()`, `colors()` und `filter()` und wird auf
Android nicht benutzt.

---

### AP-08 · Einstellungskopf · Kern: nein

**Fundstelle iOS** `SettingsView.swift:95ff`: Profilname mit Lupe öffnet
die Profilsuche; daneben ein Zähler der gegenüber dem gespeicherten
Profil geänderten Werte mit Rückfrage vor dem Zurücksetzen.

**Zustand Android** Nur *Zurück* und die drei Reiter. Die Profilsuche
existiert, aber nur im Advanced-Seitenband (`filterPresetOptions`).

**Stand: erledigt** (19.08., `bb47ae1`). Rechts im Kopf stehen der Name
des geltenden Profils und — sobald es etwas gibt — der Zähler der
ungespeicherten Werte. `SlicerService.profilaenderungen()` sammelt über
alle drei Sammlungen und holt den lesbaren Namen aus dem Kern
(`configMeta.label`); die Suche ist `filterPresetOptions` aus dem
Advanced-Seitenband, nicht eine zweite.

Belegt am Emulator: Kopf zeigt `0.10mm FAST DETAIL @COREON…`, nach
*Perimeters* 2 → 4 erscheint der Zähler `1`, die Rückfrage nennt
„Perimeters 2 → 4", Zurücksetzen stellt 2 wieder her; die Suche filtert
auf „SOLUBLE" von acht auf zwei Profile.

Statt der Lupe steht der **Trichter** aus PrusaSlicers Symbolsatz — eine
Lupe gibt es dort nicht, und eigene Symbole kommen nicht dazu (E-12).
Das Symbol wird im Symbol-Durchgang am Ende vereinheitlicht, nicht
jetzt; Ort und Verhalten sind gleich.

---

### AP-09 · Leere Zustände im Simple Mode · Kern: nein

**Fundstelle iOS** `leeresPanel` — eine Aussage plus der Knopf, der
herausführt.

| Lage | Knopf |
|---|---|
| Kein Material vorhanden | Advanced Mode öffnen |
| Keine Druckeinstellungen vorhanden | Druckeinstellungen einrichten |

**Zustand Android** Leere Fläche, kein Weg heraus.

**Stand: teilweise** (19.08., `875a655`). `LeeresPanel` steht als Muster
und ist in der Materialauswahl im Einsatz. Offen: dieselbe Behandlung
für *Keine Druckeinstellungen vorhanden*.

---

### AP-10 · Anordnen mit Optionen · Kern: nein

**Fundstelle iOS** `BedSelector.swift:450ff`, Popover am Knopf:
Zielmodus *aktuelles Bett ↔ alle Betten*, Zielbettliste mit Schloss und
Objektzahl, Abstand 0–50 mm in Halbschritten, Schalter *Drehen erlauben*,
danach ein Ergebnistext.

**Zustand Android** Ein Knopf, `arrange()` mit Abstand 0, ohne Drehen,
ohne Rückmeldung.

**Bindung** `psm_arrange_bed_ex` mit `psm_arrange_info` (Status,
Objekt- und Instanzzahl; meldet *gesperrt* und *voll* als eigene
Fehler). Der Plan führte sie als fehlend — sie stand längst in
`android/jni/psm_jni.cpp:1527` und in `PsmCore.arrangeBed(…)`, und die
abgelegte `.so` enthält sie. **Kein Kernbau.**

**Hinweis** Der gemeinsame `BedStripContract` prüft die Verfügbarkeit
bereits. Die Entscheidung „anordnen möglich?" darf nicht doppelt
entstehen.

**Stand: fertig** (20.08.). `AnordnenPanel.kt` hängt als Feld am
*Anordnen*-Knopf und öffnet sich beim Halten; Tippen ordnet weiter
sofort an. Darin: Zielmodus *Current bed ↔ All beds*, Zielbettliste mit
Schloss und Objektzahl, Abstand 0–50 mm in Halbschritten, *Allow
rotation*, danach der Ergebnissatz.

Die Verfügbarkeit kommt aus dem Vertrag, nicht aus der Ansicht:
`AndroidBedStripAdapter.arrangeFuer(…)` fragt ihn mit dem Zielbett als
aktivem — dieselbe Drehung wie `arrangeAvailability(for:)` drüben. Die
Meldung *danach* kommt vom Kern (`ArrangeStatus`), damit *voll* und
*gesperrt* unterscheidbar bleiben.

Belegt am Emulator: mit Bett 2 als Ziel „Bed 2 is empty. There is
nothing to arrange.", mit Bett 1 „Bed 1: 1 instances arranged." —
wortgleich mit `ArrangePanel.anordnen()`.

---

### AP-11 · Slice-Blatt · Kern: nein

**Fundstelle iOS** `Screens/SliceSheet.swift:79ff`

- „Geslict in 2:14"
- bei Mehrbett eine Zeile je G-Code-Datei mit eigenem *Senden*-Knopf
- *Alle exportieren*
- *An Drucker senden* direkt aus dem Blatt
- Hinweis, wenn die Datei nicht geschrieben werden konnte

**Zustand Android** *G-Code sichern*, *Schließen*.

**Wortwahl** iOS sagt **„Export G-Code"**, nicht *Sichern* — die Datei
verlässt die Anwendung.

**Stand: fertig** (20.08.). Zuerst nur zur Hälfte: der Block entstand
in der Advanced-Seitenleiste (`8afd69d`, `c63e329`), die Vorlage
`Screens/SliceSheet.swift` ist aber das **Simple-Mode-Blatt**. Dort
fehlten bis zuletzt *An Drucker senden*, die Zeile je Datei und der
Hinweis auf nicht geschriebene Dateien. Nachgeholt; der Aufbau folgt
jetzt `SliceSheet.swift:88ff`:

- mehrere Dateien → Zahl, *Alle exportieren*, dann eine Zeile je Datei
  (*Senden*, wenn genau ein Drucker eingerichtet ist, sonst
  *Exportieren*),
- genau eine Datei → *G-Code exportieren*, darunter *An Drucker senden*,
- keine Datei → der Grund statt eines Knopfs, der ins Leere führt.

Die einzelne Datei kommt aus `lastGcode`, nicht aus `gcodeDateien`: die
Liste füllt nur ein Lauf über alle Betten. Ein erster Versuch hing am
Listenumfang und zeigte nach einem gewöhnlichen Schnitt „Der G-Code ließ
sich nicht schreiben." — obwohl die Datei da war.

Belegt am Emulator mit einem eingerichteten Drucker: *Export G-code*
prominent, darunter *Send to printer*, darunter *Close* — dieselbe
Reihenfolge wie drüben.

Vorher (`8afd69d`): Im Ergebnisblock der
Seitenleiste stehen jetzt die Zahl der Dateien und je eine Zeile mit
Namen und eigenem Knopf — *Senden*, wenn genau ein Drucker eingerichtet
ist, sonst *Exportieren*. Dafür nehmen `sendToPrinter(...)` und
`shareableGcodeUri(...)` jetzt eine bestimmte Datei.

Belegt am Emulator: Würfel auf zwei Betten, danach *2 G-code files*,
`bett-1.gcode` und `bett-2.gcode` mit je einem Knopf, beide Dateien mit
53020 Bytes in `files/`.

**Stand: erledigt** (20.08., `8afd69d`, `c63e329`). Dazu kamen
*Alle exportieren* (`ACTION_SEND_MULTIPLE`, ein Durchgang statt fünf),
die Wortwahl und die Dauer im Simple-Blatt, und der Hinweis bei einem
Bett, dessen Datei sich nicht schreiben ließ — der Lauf bricht dabei
nicht mehr ab, die übrigen Dateien sind trotzdem etwas wert.

Belegt am Emulator: Advanced zeigt *2 G-code files*, *Export all* und je
eine Zeile für `bett-1.gcode` und `bett-2.gcode`; das Simple-Blatt zeigt
*Ready to print*, *Sliced in 0m*, die Zahlen, *2 G-code files* und
*Export all*.

**Nebenbefund, mitgefixt:** `onShareGcode` im Simple Mode war nie
verdrahtet — der Parameter hatte eine leere Vorgabe, und `MainActivity`
hat sie nie überschrieben. *G-Code sichern* führte dort ins Leere.

**Kleiner Rest:** `SliceSummary.duration()` sagt bei einem halben
Sekundenlauf *0m*. Unter einer Minute gehören Sekunden dorthin — betrifft
beide Plattformen, weil die Regel im gemeinsamen Modul liegt.

---

### AP-12 · Bettleiste · Kern: nein

Drei Umbauten auf iOS:

- ~~raus aus dem Easy Mode (`04b2c3d`)~~ — **überholt.** Drei Tage
  später hat `6875fd2` („unify multi-bed presentation rules",
  09.08.) die Leiste dort wieder eingesetzt. iOS zeigt sie heute im
  Simple Mode, Android auch. Nichts zu tun.
- Kapselreihe im Advanced (`c4402bb`) — steht seit AP-21.
- Sperren, Umbenennen, Entfernen ins Kontextmenü (`c4402bb`) — teils.
  Schloss und X stehen bei iOS **sichtbar** in der Kapsel (Foto vom
  iPad, `bettKapsel:190ff`), das Kontextmenü kommt zusätzlich.

**Stand: erledigt** (20.08., `f03c7b0`). Der lange Druck öffnet jetzt
ein Menü mit *Umbenennen* und — bei einem leeren Bett — *Entfernen*,
statt sofort ins Umbenennen zu springen.

Belegt am Emulator: langer Druck auf *Bed 1* zeigt *Rename*; *Remove*
fehlt dort richtig, weil das Bett ein Objekt trägt.

**Lehre für den Plan:** ein Commit-Verweis allein sagt nur, was einmal
passiert ist. `04b2c3d` stand hier als Aufgabe, obwohl iOS die
Entscheidung drei Tage später zurückgenommen hatte. Vor dem Nachbauen
prüfen, was drüben **heute** im Quelltext steht.

---

### AP-13 · Adaptive Schichthöhe · Kern: nein

**Fundstelle iOS** `LayerProfileView.swift:191ff` — Qualitätsregler und
*Berechnen*.

**Zustand Android** Nur Punkte von Hand.

**Bindung** `psm_model_layer_profile_adaptive` stand längst in JNI und
in `PsmCore.adaptiveLayerProfile(…)`. **Kein Kernbau.**

**Stand: fertig** (20.08.). Über der Stützstellenliste steht jetzt eine
Zeile *Adaptiv* mit Regler und *Berechnen*, darunter der Satz „Links
fein und glatt, rechts grob und schnell." — wortgleich mit
`LayerProfileView.swift`. *Berechnen* füllt nur die Zeilen; angewendet
wird wie beim Profil von Hand erst mit *Übernehmen*, damit dieselbe
Seite nicht zwei Wege hat, wirksam zu werden.

**Dabei gefunden: die Treppe des Kerns.**
`psm_model_layer_profile_adaptive` liefert das Profil als Treppe — an
einer Stufe stehen zwei Paare mit demselben z (gemessen: 83 Paare, z=0.2
zweimal). Roh übernommen sind die Z-Werte nicht mehr streng steigend,
`LayerProfile.canApply` findet das Profil ungültig, und *Übernehmen*
bleibt grau, ohne dass jemand erklären könnte warum. `fromPoints`
zieht doppelte z jetzt zusammen und behält den **letzten** Eintrag: ab
dieser Höhe gilt die neue Dicke.

Das steht in der **gemeinsamen** Regel, nicht in der Ansicht — iOS ruft
dieselbe `fromPoints` und hatte denselben Fehler. Zwei Tests decken es
ab. `LayerProfileEditorState` rechnete die Umwandlung bis dahin selbst;
sie geht jetzt auch dort durch die Regel.

Belegt am Emulator: *Compute* macht aus den 83 Kernpunkten „81 height
ranges · 0.20 mm · 0.25 mm …", *Apply* ist aktiv.

---

### AP-14 · Zweiter Regler in der Vorschau · Kern: nein

iOS hat zwei `DualHandleSlider`: links senkrecht den Schichtbereich,
unten waagerecht den **Werkzeugweg innerhalb der Schicht** —

> wie der Desktop-Regler unter dem Bett

Android hat nur den senkrechten.

**Bindung** `psm_viewport_set_move_range` und
`psm_viewport_move_range_bounds` standen bereits in `PsmViewport.kt`
(Zeile 195ff) — nur gerufen hat sie niemand. **Kein Kernbau.**

**Stand: fertig** (20.08.). Zwei Änderungen, beide an der Anordnung:

1. Der **senkrechte** Schichtregler steht jetzt **links** am Bett, wie
   der Desktop-Regler und wie drüben. Er saß rechts, an derselben Kante
   wie die Seitenleiste.
2. Darunter der **waagerechte** Regler für den Werkzeugweg innerhalb der
   sichtbaren Schichten — `WegSlider`, zwei Griffe wie beim
   `LayerSlider`, nur liegend.

Die Grenzen kommen vom Kern, nicht vom Nutzer, und gelten immer nur für
den gerade sichtbaren Schichtbereich. Deshalb liefert
`SceneController.setLayerRange(lo, hi, onBounds)` sie im selben Zug
zurück: getrennt gerufen arbeitete man mit den Grenzen von gestern. Die
Antwort kommt über einen Rückruf, weil der Viewport auf dem GL-Faden
läuft — genau wie in `ViewportView.swift`. Übernommen wird nur, was sich
wirklich geändert hat, sonst überschreibt jeder Bildaufbau eine laufende
Ziehgeste (derselbe Vorbehalt steht in `AdvancedWorkspaceView.swift`).

Die Statistikkarte weicht beiden aus: links um den senkrechten Regler
herum, unten um den waagerechten.

Belegt am Emulator: nach dem Schnitt steht unten *10 … 2617*; ein Zug am
senkrechten Regler auf Schicht 127 macht daraus *10 … 160x*, und ein Zug
am rechten Griff auf *7189* schneidet die Werkzeugwege im Bild sichtbar
ab.

---

### AP-15 · Druckerauswahl als Karten · Kern: nein

**Fundstelle iOS** `Screens/DruckerAuswahlView.swift`, in beiden Modi
dieselbe Ansicht. Im Quelltext steht, dass die Klappliste mit rohen
Profilnamen genau deshalb ersetzt wurde.

**Zustand Android** Dialog mit Suchliste (`AdvancedSidebar.kt:891`).

**Abgrenzung** Filament und Druckeinstellungen bleiben Suchlisten — dort
sind es hunderte Einträge, und iOS macht es genauso.

**Stand: erledigt** (20.08., `50de001`). Die Kartenansicht liegt jetzt
als `ui/DruckerAuswahl.kt` für sich und wird von **beiden** Modi
benutzt — vorher stand sie nur im Simple Mode, und der Advanced hätte
eine zweite bekommen. Die Karten stehen untereinander statt zu zweit
nebeneinander, wie drüben, und der Materialhinweis ist weg: er stand
da, solange die Karte nur im Simple Mode vorkam.

Belegt am Emulator: unter *PRINTER* steht die Karte *Prusa CORE One L
MMU3 · SELECTED · OFFLINE · Nozzle 0.4 nozzle* mit orangem Rand,
darunter die Kachel *Add printer*.

---

### AP-16 · Inspector öffnet nach der Auswahl · Kern: nein

Vier Anläufe auf iOS (`3e45739`, `cb08ec1`, `bda94c2`, `8a53aa3`): nach
einer Auswahl klappt der Bearbeiten-Bereich von selbst auf, und der Fokus
bleibt beim Blättern layoutstabil.

**Stand: erledigt** (20.08., `82f3fa8`). Aufklappen kam mit AP-20; jetzt
scrollt das Band auch dorthin. Der Bereich meldet seine Position, und
die Auswahl scrollt hin — nach einem abgewarteten Bild, weil die
Position vor dem nächsten Layout noch die vor dem Aufklappen ist.

Der zweite Teil („Fokus bleibt beim Blättern layoutstabil") ist seit
AP-20 gegenstandslos: es gibt nur noch einen durchgehenden Scrollbereich
statt vier, die beim Wechsel zurückgesetzt wurden.

Belegt am Emulator: Würfel antippen zeigt *EDIT* mit Objektnamen und den
Griffen direkt unter den drei Einstellungszeilen.

---

### AP-17 · Kleinere Pakete · Kern: gemischt

| | Ziel | Kern |
|---|---|---|
| a | ~~Projekt (.3mf), Platte und alle G-Code-Dateien weitergeben~~ — **erledigt** (20.08., `c63e329`, `18817d5`, `5605ea0`) | nein |
| b | ~~Größenverhältnis je Objekt in beiden Listen~~ — **erledigt** (20.08., `d919c3d`) | nein |
| c | ~~Abschnitt „Je Extruder" in der Simple-Materialseite~~ — **überholt.** Android hat dort die *Materialpalette*: Kacheln je Kopf mit Farbe und Material, *Alle setzen* und eine Farbreihe. iOS zeigt an derselben Stelle nur eine Liste mit Farbfeld und Menü. Nichts nachzuziehen — umgekehrt ist es ein Z-Punkt. | nein |
| d | ~~ColorMix-Vorschau vor dem Speichern, Fehlermeldung beim Sichern~~ — **erledigt** (20.08., `492799b`) | nein |
| e | ~~Reinigungsturm (X, Y, Drehung) in die Extruderbank~~ — **erledigt** (20.08., `431d961`) | nein |
| f | ~~Zoll-Einheiten (`KEY_UNITS_IMPERIAL`)~~ — **erledigt** (20.08., `bcb4361`) | nein |
| g | ~~ZIP-Import~~ — **erledigt** (20.08.). `application/zip` steht in den Dateitypen des Wählers, `importOne` schickt eine ZIP an `SlicerService.loadZip` statt an die Endungsprüfung — eine ZIP ist keine Modelldatei, sondern eine Tüte voll davon; dieselbe Reihenfolge wie in `PSMobileApp.swift:168`. Gezählt werden die *geladenen* Modelle, nicht die ausgepackten. Der Hinweis erscheint für ZIPs immer, auch bei einer einzelnen Auswahl — sonst stünde dort „1 Datei geladen", und auf dem Bett lägen zwölf. **Die Modusfrage entfällt:** sie gehört auf iOS zum Teilen von außen, wo noch kein Modus gewählt ist (`ZipModusDialog`); Androids Wähler wird immer aus einem Modus heraus geöffnet. | nein |

---

### AP-18 · Restliche Bindungen · Kern: ja

**Stand: erledigt bis auf zwei** (19.08., `27ac6eb`, `1eb4c97`).
Gebunden und im gestrippten `.so` nachgewiesen, App startet ohne
`UnsatisfiedLinkError`. Offen bleiben nur
`psm_viewport_active_layer_visualization` und
`psm_viewport_active_paint_visualization` — beide liefern Legenden, die
erst mit AP-03 sichtbar werden.

Beim Binden fiel auf: `psm_slice_accept_remote_gcode` nimmt eine
**Szenenrevision** entgegen. Wer sie nicht mitgibt, bekommt ein Ergebnis
von vorhin auf einer Anordnung von jetzt. Die Kotlin-Seite fragt sie
über `designRevision()` ab.

| Funktion | Wofür |
|---|---|
| `psm_viewport_gesture_begin` | Eine Geste = ein Rückgängig-Schritt. Android klammert selbst über `beginHistory`; das funktioniert, ist aber ein zweiter Weg für dieselbe Sache. |
| `psm_viewport_gizmo_axis_screen`, `psm_viewport_get_gizmo` | Achsenbeschriftung am Griff |
| `psm_viewport_active_layer_visualization`, `psm_viewport_active_paint_visualization` | Legenden zu Schichthöhe und Bemalung |
| `psm_slice_result_is_current`, `psm_slice_accept_remote_gcode` | Fernslicen: fertiges G-Code übernehmen statt lokal neu rechnen |
| `psm_model_lay_on_facet_instance` | Auf Fläche legen für genau diese Kopie statt immer Instanz 0 |
| `psm_model_bed_state` | Feinere Auskunft als `outside_bed`: schneidet den Rand / ganz daneben / unter dem Bett |
| `psm_design_revision`, `psm_preset_option_at` | Innereien, kein eigenes Bedienelement |

---

### AP-19 · Hochformat und schmale Geräte · Kern: nein

iOS hat ausdrückliche Zweige:

- `schmal` — kein iPad **und** Fenster schmaler als 760 pt
- `leisteUnten` — im Hochformat die Leiste unten andocken statt rechts
- Seitenleiste höchstens zwei Fünftel der Breite
- iPad-Seitenleiste bleibt auch hochkant ausgeklappt (`2213e04`)
- iPhone-Vorschau scrollbar, sonst fiel der untere Regler aus dem
  Bottom-Sheet (`12f5b52`)
- Bettwähler und Seitenleiste dürfen nicht gleichzeitig schweben

Android hat zwei Schalter (`compactNavigation`, `tightChrome`) und ist im
Hochformat **nie geprüft worden**. Hier sind weitere Befunde zu erwarten,
die nur am Gerät sichtbar werden.

---

### AP-20 · Rechter Bereich: Bereiche statt Reiter · Kern: nein

**Von Nils gemeldet** (19.08.): „die Anordnung vom Toolbereich rechts ist
noch komplett unterschiedlich". Stimmt — und iOS hat den Weg, den Android
noch geht, ausdrücklich verlassen.

**Fundstelle iOS** `AdvancedWorkspaceView.swift:941ff` (`seitenleiste`).
Aufbau von oben nach unten:

1. **Drei Einstellungszeilen** — *Druckeinstellungen*, *Filament*,
   *Drucker*. Jede öffnet die zugehörige Einstellungsseite.
   Begründung im Quelltext: sie wirken auf das Profil, und das Profil
   steht rechts; oben in die Werkzeugleiste gehört, was auf den
   Viewport wirkt.
2. Trennlinie.
3. **Alle vier Inspektorbereiche untereinander**, jeder mit Überschrift
   und Aufklapppfeil: *Profile · Objekte · Bearbeiten · Werkzeuge*.
   Wörtlich aus dem Quelltext:

   > Untereinander statt hinter Reitern: vier Reiter heissen, dass drei
   > Viertel des Gesuchten unsichtbar sind. So sieht man alle
   > Ueberschriften und klappt auf, was man braucht.

   *Bearbeiten* und *Werkzeuge* sind ohne Auswahl ausgegraut, nicht
   verschwunden.
4. **Abschluss außerhalb der Bereiche** — Fortschritt, Ergebnis und der
   Schneiden-Knopf. Begründung: „was ein Schnitt ergeben hat, ist keine
   Frage des gerade offenen Reiters."

**Zustand Android** `AdvancedSidebar.kt:811` (`InspectorTabs`): dieselben
vier Namen, aber als **Reiterzeile** — immer nur einer sichtbar. Die drei
Einstellungsseiten stecken im Reiter *Profile* statt oben zu stehen.
Kopfzeile *Arbeitsbereich · Bett 1 · 1 Objekt* darüber, Schneiden-Knopf
unten (der stimmt schon).

**Umsetzung**

1. `InspectorSection` bleibt als Aufzählung, wird aber vom *einen*
   gewählten Reiter zu einer **Menge offener Bereiche**
   (`Set<InspectorSection>`, gemerkt über `rememberSaveable`).
2. `InspectorTabs` entfällt. An seine Stelle tritt je Bereich eine
   Kopfzeile mit Namen und Pfeil, darunter der bisherige Inhalt —
   die vier Inhaltsblöcke selbst bleiben unverändert.
3. Die drei Einstellungszeilen aus dem Profile-Block nach oben ziehen,
   über die Trennlinie.
4. Der `LaunchedEffect` auf `selected?.id`, der heute den Reiter
   umschaltet, wird zum Öffnen des passenden Bereichs: bei Auswahl
   *Bearbeiten* aufklappen, ohne Auswahl zuklappen. *Werkzeuge* bleibt
   unangetastet, wie schon heute.
5. Die Scrollstand-Zurücksetzer an `section` entfallen — es gibt nur
   noch einen durchgehenden Scrollbereich.

**Fertig wenn** Rechts stehen von oben nach unten: drei
Einstellungszeilen, Trennlinie, vier aufklappbare Bereiche mit
sichtbaren Überschriften, unten der Schneiden-Block — und ein
Bildschirmfoto zeigt dieselbe Reihenfolge wie auf iOS.

**Stand: erledigt** (19.08., `2e5f5ec`, `e0d…`). Belegt am Emulator:
Kopfzeile, *Print Settings · Filament Settings · Printer Settings*,
Trennlinie, `PROFILES` offen, darunter `OBJECTS`, `EDIT` und `TOOLS` als
Überschriften mit Pfeil — *Bearbeiten* und *Werkzeuge* ohne Auswahl
gedämpft statt verschwunden —, unten der Schneiden-Block.

---

### AP-23 · Werkzeugbereich: erst gestalten, dann angleichen · Kern: nein

**Von Nils gemeldet** (19.08.): „die ganzen Tools sehen noch
unterschiedlich aus, aber ehrlich gesagt sehen sie auf iOS auch noch
nicht gut aus."

Damit greift der Grundsatz hier **nicht**: Android an iOS anzugleichen
hiesse, eine Gestaltung zu übernehmen, die auf keiner der beiden Seiten
überzeugt. Das ist der eine Fall, in dem Nachziehen der falsche Schritt
wäre.

**Vorgehen** Erst entscheiden, wie der Bereich aussehen soll — dann auf
beiden Seiten gleichzeitig bauen. Bis dahin bleibt der Werkzeugbereich
so, wie er ist; er wird nicht Stück für Stück an iOS herangeschoben.

**Braucht** eine Vorgabe von Nils und, für die iOS-Hälfte, den Mac.

---

### AP-21 · Bettübersicht: entfernen je Bett · Kern: nein

**Von Nils gemeldet** (19.08.): „bei iOS habe ich in der Bettübersicht
ein x zum Entfernen".

**Fundstelle iOS** `Screens/BedSelector.swift:268ff`
(`BedSelectionSheet`). Je Bett eine Karte — Symbol, Name, *N Objekte*,
aktiv orange — und rechts daneben drei kleine Knöpfe: **Stift**
(umbenennen), **Schloss**, **Papierkorb**. Der Papierkorb erscheint nur,
wenn `canRemove` aus dem gemeinsamen `BedStripContract` es erlaubt.
Darunter *Bett hinzufügen*, gesperrt wenn `canAdd` falsch ist.
Erreichbar über den Bettwähler (`onOpenSelection`), zusätzlich zur
Kapselreihe — iOS hat **beides**.

**Zustand Android** `SlicerScreen.kt:2160` (`BedSelector`) hat zwei
Zweige, und nur einer stimmt:

- **schmal**: eine vollständige Liste im Bottom-Sheet, je Bett Schloss,
  Stift und ein **X** — inhaltlich dasselbe wie iOS.
- **breit** (also auf dem Tablet, dem Hauptfall): nur die Kapselreihe
  mit Schloss und Stift, dazu ein globales **+** und ein Papierkorb,
  der das *aktive* Bett entfernt. Kein Weg zur Übersicht, kein
  Entfernen je Bett.

Damit unterscheidet sich Android nicht nur von iOS, sondern auch von
sich selbst, je nach Fensterbreite.

**Umsetzung** Den Sheet-Zweig aus `schmal` herausziehen und aus beiden
Zweigen erreichbar machen: die Kapselreihe bekommt denselben Weg in die
Übersicht wie auf iOS. Der globale Papierkorb am Ende der Reihe
entfällt dann — er tut, was der Eintrag in der Übersicht genauer tut.

**Fertig wenn** Auf dem Tablet führt ein Weg von der Bettreihe in eine
Übersicht, in der jedes Bett Stift, Schloss und X hat, und *Bett
hinzufügen* darunter steht.

**Erster Anlauf war die falsche Stelle.** Ich hatte das Blatt aus dem
schmalen Zweig auf dem Tablet erreichbar gemacht — iOS zeigt dort aber
gar kein Blatt. Die „Bettübersicht", die Nils meint, ist die
**Kapselreihe selbst**: `bettKapsel` in `BedSelector.swift:153ff`.

**Stand: erledigt** (19.08.). Die Kapsel auf Android liest jetzt wie
drüben — Name, Objektzahl als kleine Zahl, Schloss, und **X nur bei
einem leeren Bett**. Kein Stift mehr: Umbenennen liegt im langen Druck,
weil es selten ist und der Knopf die Kapsel breiter machte, als der Name
Platz hatte. Das aktive Bett ist nicht mehr voll orange gefüllt, sondern
`PanelRaised` mit orangem Rand und orangem Namen — es ist ein Zustand,
kein Befehl. Das *+* am Ende ist klein und orange.

Belegt am Emulator gegen ein Foto vom iPad: `Bed 1  1  🔓` ohne X (trägt
ein Objekt), `Bed 2  0  🔓  ×` aktiv mit Rand, dahinter das *+*.

---

### AP-22 · Alle Betten schneiden · Kern: nein

**Von Nils gemeldet** (19.08.): „iOS hat unter *Slice now* ein *Slice
all beds*".

**Fundstelle iOS** `AdvancedWorkspaceView.swift:1209ff` — unter dem
Schneiden-Knopf, **nur wenn mehr als ein Bett existiert**. Während des
Laufs steht dort *Bett 3/5* statt der Beschriftung, und der Knopf ist
gesperrt. Die Hinderungsgründe sind dieselben wie beim einzelnen
Schnitt: „ohne Bett auf dem Bett ist *alle Betten schneiden* derselbe
leere Auftrag".

`SlicerModel.sliceAll()` (`SlicerModel.swift:1635ff`) nimmt alle Betten
mit Objekten, wählt sie der Reihe nach aus, schneidet je Bett und sammelt
die Dateien in `gcodeURLs` — plus Summen für Zeit und Gramm.

**Zustand Android** Gibt es nicht; `SlicerService` kennt kein
`sliceAll`.

**Umsetzung**

1. `SlicerService.sliceAlleBetten()` nach demselben Muster: Betten mit
   Objekten sammeln, der Reihe nach auswählen und schneiden, Dateien
   sammeln, am Ende das Ausgangsbett wieder aktiv setzen.
2. Ein Fortschritt *Bett i/n* im vorhandenen `Progress`-Zustand, damit
   der Knopf dasselbe zeigen kann.
3. Der zweite Knopf unter *Slice now*, nur bei mehr als einem Bett.

**Hängt zusammen mit AP-11**: das Slice-Blatt braucht dann eine Zeile je
G-Code-Datei und *Alle exportieren*.

**Stand: Dienst und Knopf erledigt** (20.08., `6bd3438`).
`startSliceAlleBetten()` läuft über einen eigenen Service-Befehl, nimmt
alle Betten mit Objekten, legt `bett-N.gcode` ab und stellt am Ende das
Ausgangsbett wieder her. Der Knopf steht unter *Slice now*, nur bei mehr
als einem Bett, und liest während des Laufs *Bett i/n*.

Belegt am Emulator: nach dem Tippen entsteht `files/bett-1.gcode`
(53020 Bytes), der Lauf endet auf *Fertig*, *Export G-code* erscheint.

**Offen:** die Dateien sind noch unsichtbar — das ist AP-11.

---

### AP-24 · Rechts: Viewer statt Editor · Kern: nein

**Vorgabe von Nils** (20.08.): „Allerdings möchte ich das Menü, was da
frei schwebend ist, nicht mehr haben. Das soll bei Ansicht des G-Codes
im rechten Menü mit verschwinden. Allgemein sollte auf der rechten Seite
zwischen einer Viewer- und einer Editor-Ansicht hin und her geswitcht
werden, je nachdem, in welchem Bereich man sich gerade befindet."

**Zustand vorher** Zahlen, Merkmals-/Extruderwahl und Legende lagen als
schwebende Karte über dem Bett — mit der Begründung, das Seitenband sei
im Vorschaumodus oft zu. Sie verdeckte einen Teil der Platte, und
seit der Schichtregler links steht (AP-14), auch dessen unteren Griff.

**Stand: fertig** (20.08., Advanced Mode). Das rechte Band trägt jetzt
zwei Gesichter:

| | Kopfzeile | Inhalt |
|---|---|---|
| Editor | *Arbeitsbereich* | Print/Filament/Printer Settings, Bereiche, Objektbaum |
| Vorschau | *Vorschau* | Zahlen, *Merkmale ↔ Extruder*, Legende, Verbrauch |

Der Schneiden-Block unten (Fortschritt, *Slice now*, *Export G-code*)
bleibt in beiden sichtbar — er gehört zum Projekt, nicht zur Ansicht.

Die drei Profilseiten sind in der Vorschau **weg**: sie wirken auf den
nächsten Schnitt, nicht auf das, was man gerade ansieht.

Das Band **geht beim Wechsel in die Vorschau von selbst auf**. Sonst
wären die Zahlen auf schmalen Geräten gar nicht mehr erreichbar — genau
das war die ursprüngliche Begründung für die schwebende Karte.

Die Legende bricht jetzt um (`FlowRow`) statt seitwärts zu scrollen: im
schmaleren Band lief die Reihe rechts aus dem Bild, und ein waagerechter
Schieber ohne sichtbaren Rand sieht aus wie abgeschnittener Text.

**Der Weg zurück steht in der Kopfzeile.** Neben *G-Code-Vorschau*
liegt ein Knopf *Editor* — wortgleich mit drüben
(`AdvancedWorkspaceView.vorschauInhalt`). Wer rechts liest, sucht dort
weiter; die obere Leiste allein war ein Weg quer über den Bildschirm.

**Simple Mode: erledigt** (20.08.). Erst als offene Frage notiert — zu
Unrecht: iOS hat dafür eine feste Antwort, `FinalPreviewPanel.swift`.
Auf breiten Geräten steht die Karte am rechten Rand, auf schmalen
unten; drüben ist das die Unterscheidung iPad/iPhone, hier die
Bildschirmbreite (< 840 dp). Kopfzeile *Finaler G-Code* mit demselben
*Editor*-Knopf, darunter die Zahlen und die Legende.

Den Schichtbereich führt iOS in dieser Karte als zwei Regler mit —
hier nicht: der senkrechte Regler steht schon am linken Rand, und
zweimal dieselbe Einstellung ist eine zu viel.

**Zwei Kollisionen dabei behoben:** die Karte lag über der
Werkzeugspalte am rechten Rand (jetzt 84 dp Abstand), und der
senkrechte Schichtregler begann am Bildschirmrand statt unter der
Kopfzeile — sein oberer Griff saß in der Statusleiste, nach dem ersten
Korrekturversuch mitten in der Kapsel *Bett 1*.

---

### AP-25 · Selbsttest angleichen · Kern: nein

**Gefunden am 20.08.** durch den erweiterten Beschriftungsabgleich: die
Paarliste kannte `SelbsttestView.swift` gar nicht, weil die
Android-Datei anders heißt. „Kein Gegenstück gefunden" hieß bis dahin
schlicht, dass ein Bildschirm **nie verglichen wurde**.

**Zustand vorher** Ein Knopf *Start*, der während des Laufs
ausgegraut war und *Läuft…* sagte. Kein Weg heraus, und am Ende keine
Auskunft, ob etwas schiefging — dafür musste man den Bericht öffnen oder
sechzehn Haken einzeln durchsehen.

**Stand: fertig** (20.08.). Drei Angleichungen an
`SelbsttestView.swift`:

1. Der Knopf heißt *Alles prüfen* (drüben *Run all checks*).
2. Während des Laufs wird derselbe Knopf zu *Abbrechen* — dieselbe
   Doppelbelegung wie drüben.
3. Danach steht eine Zeile da: *Alles bestanden*, *N fehlgeschlagen*
   oder *Abgebrochen*.

**Abgebrochen wird zwischen den Schritten, nicht mitten in einem.** Ein
halb gerechneter Schnitt ließe den Kern in einem Zustand zurück, über
den der Bericht nichts Wahres sagen könnte. Die übrigen Schritte stehen
danach als *übersprungen · abgebrochen* im Bericht — wer nur aufhört,
liefert einen Bericht, der aussieht wie ein vollständiger Durchgang mit
weniger Zeilen.

**Die dritte Zeile war nötig, weil die zweite sonst lügt.** Nach einem
Abbruch stand zuerst *All checks passed* da: es war nichts
fehlgeschlagen, aber eben auch nicht alles geprüft worden. Am
Bildschirmfoto sofort zu sehen, im Quelltext nicht.

**Geprüft und gleich:** die Schrittnamen sind auf beiden Seiten deutsch
(*Gerät und App*, *Kern starten*) — auch bei englischer Oberfläche. Das
ist kein Versäumnis, sondern auf beiden Seiten so gewollt: der Bericht
ist ein Werkzeug für die Entwicklung.

---

### AP-26 · Schichtprofil: erfundene Vorschau raus · Kern: nein

**Gefunden am 20.08.** im Abgleich `LayerProfileView.swift` gegen
`GeometryTools.kt`.

**Der Befund** Wenn die Eingabe nicht gültig war (Z nicht lesbar, Werte
nicht steigend), zeigte Android **ein erfundenes Profil**: ein
Ersatzband aus 0.0 / 0.2 mm, dargestellt als „1 height ranges · 0.20
mm". Das sah aus wie ein gültiges Ergebnis. Der Nutzer sah eine
Vorschau für etwas, das er nie eingegeben hatte, und erfuhr nicht,
warum *Übernehmen* grau blieb.

Das ist schlimmer als eine leere Fläche: eine leere Fläche sagt nichts,
eine falsche Fläche sagt etwas Falsches.

**Stand: fertig** (20.08.). Zwei Änderungen:

1. Bei ungültiger Eingabe steht dort jetzt **„Nicht gültig"** —
   wortgleich mit `LayerProfileView.swift:109`.
2. Über der Liste steht der Satz **„Ab dieser Höhe gilt die angegebene
   Schichtdicke."** Drüben steht er seit langem unter der Überschrift;
   ohne ihn muss man aus zwei Spalten raten, ob *Z* den Anfang oder das
   Ende meint.

**Geprüft und verworfen** — der Abgleich meldete drei weitere
Unterschiede, die keine sind:

- *Eigener G-Code*: Android hat alle fünf Typen (Farbwechsel, Pause,
  Werkzeugwechsel, Vorlage, Eigener Code) mit demselben Wortlaut. Das
  Skript sah `"Pause"` nicht, weil es dort einsprachig steht.
- *Objektinspektor*, *Profilsuche*, *Vorschaukarte*: die Beschriftungen
  liegen auf Android in anderen Dateien als im Paar genannt. Ein
  Dateipaar-Vergleich kann das nicht wissen — beim Lesen der Liste
  gehört jede Zeile nachgesehen.

---

## Z · Wo umgekehrt iOS nachzieht

Braucht einen erreichbaren Mac.

1. **Sonderwerte in den Einstellungen.** Android hat Bettform,
   Reinigungsmatrix, Ramming, Ersetzungen, Kompatibilitätsregeln und das
   Hochladen von Bettmodell und -textur. iOS kennt nur die ersten beiden.
2. **Fuzzy Skin bemalen** — am 19.08. nachgezogen, noch nicht gebaut.
3. **Blockieren bei der Naht** — am 19.08. auf beiden Seiten korrigiert.
4. **Rohe Verlaufsbezeichnung im Rückgängig-Knopf.** Der Knopf zeigt
   `model.undoLabel` unverändert; bei englischer Oberfläche steht dort
   „Druckbett leeren". Die Übersetzungstabelle `CoreLabels` liegt im
   gemeinsamen Modul und wird für diesen Weg nicht benutzt.
5. **Reiter statt Bereiche rechts.** Siehe AP-20 — dort steht die
   Angleichung auf der Android-Seite. Falls sich beim Umbau zeigt, dass
   iOS eine Stelle besser löst oder umgekehrt, gehört die Rückrichtung
   hierher.
6. **Materialpalette statt bloßer Liste.** Android zeigt im Simple
   Mode je Kopf eine Kachel mit Farbe und Material, dazu *Alle setzen*
   und eine Farbreihe; iOS hat dort nur eine Liste mit Farbfeld und
   Menü. Die Android-Fassung ist die reichere — hier zieht iOS nach.
7. **Der Werkzeugweg-Regler fehlt.** iOS hat nur den senkrechten
   Schichtregler; den waagerechten darunter — welcher Ausschnitt der
   Werkzeugwege innerhalb der sichtbaren Schichten gezeigt wird — gibt
   es dort nicht, obwohl `psm_viewport_set_move_range` und
   `…_move_range_bounds` gebunden sind. Android hat ihn seit AP-14
   (`ed84cd5`); von Nils ausdrücklich zum Nachziehen vorgemerkt
   (20.08.).
8. **Rechts umschalten statt nebeneinanderlegen.** *Korrektur vom
   20.08.:* hier stand, iOS lege Zahlen und Legende schwebend über das
   Bett. Das ist falsch — `AdvancedWorkspaceView.vorschauInhalt` zeigt
   sie längst im rechten Band, und `FinalPreviewPanel.swift` sagt es im
   Quelltext ausdrücklich. Android war der Nachzügler, nicht iOS.
   Was iOS **noch nicht** tut: den Editor dabei *ausblenden*. Drüben
   steht die Vorschau unter dem Editorinhalt, hier ersetzt sie ihn —
   das ist Nils' Vorgabe vom 20.08. („zwischen Viewer und Editor hin
   und her switchen"). Diese Richtung zieht iOS nach.
9. **Untertext beim Protokoll.** Android sagt, was in der Datei steht
   und was nicht („Gerät und Version, keine Kontodaten und keine
   Netzwerkadressen"); iOS sagt nur, dass es die letzten Warnungen sind.
   Die Android-Fassung ist die bessere.
10. **Auswahlblatt überlappt die Seitenleiste.** Das Filamentblatt ist ein
   `.sheet` und schneidet auf dem iPad die rechte Seitenleiste mitten im
   Wort. Der Advanced-Einstellungsdialog macht es richtig und legt einen
   Schleier über den ganzen Bereich.

---

## Reihenfolge

Die Spalte *Kern* sagt, ob ein Kernbau nötig ist. Sie stand für vier
Pakete auf „ja" und war falsch — die Bindungen lagen längst in der
abgelegten `.so` (geprüft 20.08.2026).

| # | Paket | Kern | Stand |
|---|---|---|---|
| 1 | AP-01 Zielflächen | nein | fertig |
| 2 | AP-02 Eckenradien (Android) | nein | fertig |
| 3 | AP-03 Vorschau | nein | fertig |
| 4 | AP-04 Objektleiste | nein | teilweise, Rest braucht den Mac |
| 5 | AP-07 Materialauswahl | nein | fertig |
| 6 | AP-09 Leere Zustände | nein | teilweise |
| 7 | AP-08 Einstellungskopf | nein | fertig |
| 8 | AP-06 Schwebende Dialoge | nein | fertig |
| 9 | AP-05 Werkzeugleisten | nein | fertig |
| 10 | AP-10 Anordnen mit Optionen | nein | fertig |
| 11 | AP-13 Adaptive Schichthöhe | nein | fertig |
| 12 | AP-14 Zweiter Regler | nein | fertig |
| 13 | AP-12 Bettleiste | nein | fertig |
| 14 | AP-11 Slice-Blatt | nein | fertig |
| 15 | AP-15 Druckerkarten | nein | fertig |
| 16 | AP-16 Inspector | nein | fertig |
| 17 | AP-17 a–f Kleinere Pakete | nein | fertig |
| 18 | AP-17 g ZIP-Import | nein | fertig |
| 19 | AP-18 Restliche Bindungen | nein | fertig bis auf zwei |
| 20 | AP-20 Bereiche statt Reiter | nein | fertig |
| 21 | AP-21 Bettübersicht | nein | fertig |
| 22 | AP-22 Alle Betten schneiden | nein | fertig |
| 23 | AP-19 Hochformat | nein | zurückgestellt |
| 24 | AP-23 Werkzeugbereich | nein | zurückgestellt, braucht eine Entscheidung |
| 25 | AP-24 Rechts: Viewer statt Editor | nein | fertig |
| 26 | AP-25 Selbsttest angleichen | nein | fertig |
| 27 | AP-26 Schichtprofil ehrlich | nein | fertig |
| 28 | Z iOS nachziehen | Mac | offen |

---

## Geprüft und gleich

Kein Handlungsbedarf, nicht erneut prüfen:

Startseite mit Zuletzt-Kacheln · Objektleiste im Simple Mode ·
Extruderbank samt Farbwahl mit eigenem Hexwert · Schichtregler senkrecht
am Rand · Farbwechsel-, Pause- und G-Code-Marken · Modifier hinzufügen ·
Schneiden · Vereinfachen · Zerlegen · Text und SVG prägen · Extruder je
Teil · Objektliste mit Suche und Mehrfachauswahl · Profil unter Namen
sichern · Bettform- und Reinigungsmatrix-Editor · gesperrte Einstellung
mit Begründung · Fernslicen mit QR-Kopplung · Drucker verwalten ·
Ersteinrichtung · App-Einstellungen · Selbsttest · Absturzprotokoll
teilen · Bemalen · Mehrbett samt Bettzuordnung beim Ziehen.

---

## Offene Fragen

**Bildschirmfotos von iOS** für die Punkte, die im Quelltext nicht
stehen — Abstände, Größenverhältnisse, Anordnung. Am nützlichsten:
Advanced Mode mit ausgewähltem Objekt · Vorschau nach dem Slicen ·
Materialauswahl · Simple Mode Einstellungsseite · **Hochformat auf dem
iPhone**.

Vorhanden sind bereits neun Aufnahmen vom 19.08.; das Hochformat fehlt.

**Der Mac** unter `192.168.1.107` ist nicht erreichbar. Ohne ihn lässt
sich weder iOS bauen noch je feststellen, ob beide Fassungen gleich
aussehen. Abschnitt Z bleibt bis dahin liegen.

---

## Eintragen, was fertig ist

Erledigte Pakete hier abhaken und im Arbeitsjournal mit Datum und
Commit vermerken. Ein Paket gilt als fertig, wenn es gebaut, auf dem
Emulator angesehen und mit einem Bildschirmfoto belegt ist — nicht
vorher.
