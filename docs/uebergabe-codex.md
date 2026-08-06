# Übergabe

Stand: 5. August 2026, früher Morgen. Geschrieben zum Weiterarbeiten in
einer neuen Sitzung — mit allem, was man sonst erst wieder herausfinden
müsste.

## Wo was steht

| | |
|---|---|
| Projekt | `\\Localunraid\n3dp\KI Projekte\PSMobile` = `/mnt/user/N3DP/KI Projekte/PSMobile` |
| Unraid | `ssh -i ~/.ssh/unraid_aipp root@100.109.46.54` — Android, Kern, Regelmodul, Docker |
| Mac | `ssh -i ~/.ssh/macincloud2 user289137@FF738.macincloud.com` — nur dort baut iOS |
| Auslieferung | `C:\Users\Nils\OneDrive\PSMobile\*.ipa`, per Sideloadly aufs iPad |

Der Mac ist ein gemieteter M4 in der Cloud. An das iPad des Nutzers
kommt man **nicht** — kein libimobiledevice unter Windows, und
Sideloadly verlangt sein Apple-Passwort, das nicht angefasst wird. Für
das Android-Tablet liegt `adb` auf dem Windows-Rechner bereit, ein
Skript dazu unter `build/scripts/geraetetest.ps1`.

## Bauen

```bash
# Android (auf Unraid, alles in Docker)
bash build/scripts/build-core.sh          # C++-Kern
bash build/scripts/build-apk.sh           # APK
bash build/scripts/build-apk.sh test      # Unit-Tests
bash build/scripts/build-apk.sh shared    # Regelmodul-Tests

# iOS (erst spiegeln, dann auf dem Mac)
bash build/scripts/sync-mac.sh alles --trotzdem
#   "alles" ist Pflicht, sobald android/shared angefasst wurde -
#   ohne das bleibt das Kotlin-Modul auf dem Mac alt und der
#   Swift-Build meldet fehlende Funktionen.
PSM_IOS_PLATFORM=SIMULATORARM64 bash build/scripts/build-ios.sh core
PSM_IOS_PLATFORM=OS64          bash build/scripts/build-ios.sh core
bash build/scripts/build-shared-ios.sh simulator|device
cd ios && xcodegen generate && xcodebuild ... test
bash build/scripts/package-ipa.sh         # -> ~/PSMobile-unsigniert.ipa
```

**Fallen, jede davon hat schon Zeit gekostet:**

* `xcodegen generate` nach **jedem** `sync-mac.sh`, sonst „cannot find X
  in scope" für neue Dateien.
* Auf dem Mac muss `PATH=~/psmobile/build-out/mac-tools/bin:$PATH`
  gesetzt sein, sonst fehlen `cmake` und `xcodegen`.
* Simulator immer über die ID `C4687F59-5167-4B0E-A255-16EA02F65B41`
  ansprechen — zwei Geräte heißen „iPad Pro 11-inch (M5)".
* Nie einen Kern-Build parallel zu einem zeitkritischen Testlauf: der
  Compiler nimmt alle zehn Kerne, und der Lasttest im Selbsttest läuft
  ins Zeitlimit.
* Skripte nicht als Shell-Einzeiler mit Anführungszeichen übertragen.
  Datei lokal schreiben, `base64 -w0 … | ssh "cat > /tmp/x.b64"`, drüben
  `base64 -d`. Alles andere zerlegt `$0`, Backslashes und Backticks.
* Das Dateisystem unter dem Simulator ignoriert Groß- und
  Kleinschreibung, verhält sich zur App hin aber case-sensitiv. Zwei
  Dateinamen, die sich nur darin unterscheiden, lassen sich nicht
  nebeneinander anlegen — hat einen halben Tag gekostet.

## Grundsätze (stehen in `docs/entscheidungen.md`)

* **E-12: übernehmen statt nachbauen.** Alles, was PrusaSlicer schon
  kann, kommt aus PrusaSlicer — auch wenn es in der GUI-Schicht liegt
  und herauskopiert werden muss. Nie selbst nachrechnen.
* **E-13: native Oberfläche je Plattform, gemeinsame Regeln.** SwiftUI
  und Compose getrennt; alles, was eine Entscheidung ist, gehört ins
  KMP-Modul `android/shared`. Achtung: Kotlin-Generics (`Pair`,
  `Triple`, `Set<Int>`) überleben die Brücke nach Swift nicht — eigene
  Datenklassen nehmen.
* Kommentare auf Deutsch, und sie sollen das *Warum* erklären, nicht das
  Was. Der Ton im Bestand ist der Maßstab.
* Jeder fertige Schritt wird committet, das Ergebnis kommt ins
  `docs/arbeitsjournal.md`.
* Tests, die eine kaputte Funktion für gut erklären, sind schlimmer als
  keine. Ein Beispiel steckt im Journal: der Vorschau-Test verglich das
  ganze Bild samt Reglerbeschriftung und bestand deshalb immer.

## Stand heute

`git log` der Sitzung, neueste zuerst:

* Advanced-Umbau: Band statt Reiter, Einstellungen schweben, Zurück unten
* Extruderfarben, Objektleiste im Advanced, Pinsel nach links
* Profiländerungen: die Frage vor dem Wechsel statt des stillen Verlusts
* Gerätemeldungen: Drehung, Flächenwerkzeug, Griffe, Hersteller
* Teile am Objekt auf iOS: Aussparung, Modifier, Stützenwunsch
* Vier Funktionen nachgezogen, und ein Name an die richtige Stelle
* Der Kern sagt jetzt, warum etwas nicht geht

Zahlen: **126 von 154 ABI-Funktionen auf beiden Plattformen**, iOS 52
UI-Tests, Regel- und Android-Tests grün. `build/scripts/bestand.py`
erzeugt die Übersicht neu — sie ist die ehrlichste Fortschrittsanzeige
des Projekts, weil sie misst statt zu erinnern.

**Nicht abgenommen:** der Selbsttest (App-Einstellungen → Diagnose) ist
auf dem iPad einmal abgestürzt. Er schreibt jetzt vor jedem Schritt eine
Zeile nach `Dateien → PSMobile → Selbsttest`; die Datei nennt beim
nächsten Lauf die Stelle. Ursache noch unbekannt, Verdacht Speicher.

## Offen, in der Reihenfolge, die mit dem Nutzer abgesprochen ist

### 1. Flächen anzeigen beim Hinlegen
Heute wird das getroffene **Dreieck** nach unten gedreht. Bei
gerundeten Teilen ist das die falsche Lage. PrusaSlicer rechnet die
ebenen Flächen aus und zeigt sie an: `external/PrusaSlicer/src/slic3r/
GUI/Gizmos/GLGizmoFlatten.cpp`, 414 Zeilen. Liegt in der GUI-Schicht,
die wir nicht bauen — die Rechnung selbst hängt an nichts davon ab und
ist übernehmbar. Danach: Flächen halbdurchsichtig zeichnen, Antippen
wählt eine Fläche statt eines Dreiecks.

### 2. Variable Schichthöhe sichtbar machen
Der Kern kann das Profil setzen und lesen
(`psm_model_layer_profile_set/_at`, Paare z/Höhe). Es fehlt **alles
Sichtbare**: Einfärbung des Modells nach Höhe (Shader, wie am Desktop),
die Kurve am Rand (kann SwiftUI aus denselben Zahlen zeichnen), und das
Bearbeiten mit dem Finger. `smooth_height_profile` liegt in
`libslic3r/Slicing.hpp`. **Vorher prüfen**, ob ein gesetztes Profil den
Schnitt überhaupt verändert — der Nutzer meldet „tut nichts", und ob das
nur die fehlende Anzeige ist, ist ungeklärt.

### 3. Pinseloptionen
Stützen/Naht/MMU bemalen gibt es (`psm_model_paint_brush`), seit heute
in der linken Spalte. Es fehlen: automatisches Bemalen nach
Überhangwinkel, Smart fill, Pinselgröße und -form, „nur auf
Überhängen", Schnittebene. `TriangleSelector::seed_fill_select_triangles`
liegt in **libslic3r**, nicht in der GUI — also nur ABI und Bedienung.

### 4. Mehrere Betten auf einem Schirm
Der Viewport kennt heute genau **ein** Bett. Der Kern kennt sie alle und
hat die Versätze (`s_multiple_beds.get_bed_translation`). Zu bauen:
Bettgeometrie und Objekte je Bett versetzt zeichnen, Treffererkennung
mit Versatz, Namen über den Betten (dafür eine ABI-Funktion Welt →
Bildschirm; die Beschriftung zeichnet dann SwiftUI, im GL gibt es kein
Schriftsystem), Umschalten wie bisher, abschaltbar in den Einstellungen.

### 5. Arrange-Panel
`psm_arrange(gap_mm)` ist alles, was es gibt, und ordnet **nur das
aktive Bett**. Gewünscht: Antippen öffnet eine Auswahl mit „alle Betten"
/ „aktuelles Bett", Mindestabstand und „Drehen erlauben".
`ArrangeSettings` kennt die Rotation, wir setzen sie nicht. Für alle
Betten über `s->bed_models` laufen.

### 6. Vorschau
Der Schichtregler wirkt (im Simulator nachgewiesen). Es fehlen: Farben
nach Feature-Typ — wir übergeben `libvgcode::convert` leere Farblisten,
deshalb ist alles weiß —, ein zweiter, waagerechter Regler für die
Bewegungen innerhalb einer Schicht
(`Viewer::set_view_visible_range`), automatischer Wechsel in die
Vorschau nach dem Schneiden mit den Kennzahlen rechts statt „Slice
now", und ein klarer Umschalter Vorschau ↔ Vorbereiten.

### 7. Kleineres
* Schnellleiste neben dem Bett wie in EasyPrint (senkrechte Icon-Reihe)
* Objektleiste: Extruderwahl mit hinein
* Panel wegklickbar → dann etwas heranzoomen
* Miniaturbild je Objekt in der Liste
* Rahmenauswahl per Halten und Ziehen
* Lasttest auf echter Hardware (Aufgabe #30)
* Android zieht nach: Herstellerebene in der Ersteinrichtung,
  Profildialog, Extruderfarben, Objektleiste

## Was der Nutzer erwartet

Er testet auf einem iPad und meldet in schneller Folge. Zwei Dinge sind
ihm wichtig geworden:

* **Ehrlichkeit über den Stand.** Wenn etwas ungetestet ist, gehört das
  dazugesagt. Er hat mehrfach Fehler gefunden, die als erledigt galten.
* **Keine halben Sachen bei zusammenhängenden Dingen.** Der Profildialog
  wurde als Ganzes gebaut, nicht in Teilen — halbfertig wäre dort
  schlimmer als gar nicht.

Nach jedem abgeschlossenen Block eine `.ipa` in die OneDrive legen, mit
Datum und Stichwort im Namen, damit er testen kann statt zu warten.

## Nachgereicht, kurz vor Schluss der Sitzung

Drei Punkte, die der Nutzer noch genannt hat. Nicht mehr umgesetzt.

### Vorschau-Eintrag oben fällt weg
Sobald nach dem Schneiden automatisch in die Vorschau gewechselt wird
und es einen klaren Umschalter Vorschau ↔ Vorbereiten gibt (Punkt 6 der
Liste oben), ist der eigene Eintrag `werkzeug.vorschau` in der oberen
Leiste doppelt. Der Nutzer hat das selbst bemerkt und gefragt — die
Antwort ist ja: er gehört weg, aber **erst zusammen mit** dem
Umschalter, nicht vorher. Sonst gibt es zwischenzeitlich gar keinen Weg
in die Vorschau.

### Bettnamen gelten nur für die Sitzung
Sie werden heute dauerhaft gespeichert (`bedNames` in `SlicerModel`,
Gegenstück auf Android). Das ist falsch gedacht: ein Bettname beschreibt,
was gerade auf dem Bett liegt — „Halterungen", „Deckel" —, und das gilt
für dieses Projekt und nicht für alle künftigen. Also nur für die
laufende Sitzung halten, oder mit dem Projekt ins 3MF, aber nicht in die
App-Einstellungen.

### Profilauswahl in die Einstellungsseiten, mit Suche
In den Druck-, Filament- und Druckereinstellungen fehlt die Auswahl des
Profils selbst — man sieht oben nur, welches gerade gilt (`SettingsView`,
`kopfzeile`). Wer wechseln will, muss den Bildschirm verlassen. Gewünscht:
die Auswahl direkt dort, mit **Suchfeld** — die Filamentliste hat je nach
Drucker mehrere hundert Einträge.

Bausteine sind da: `model.presetNames(_:)` liefert die Liste,
`model.selectPreset(_:_:)` wählt, und `MaterialAuswahlView` zeigt bereits,
wie eine Auswahl mit Suche und Filtern aussieht. Achtung: ein Wechsel des
Druckerprofils setzt über `standardwerteSetzen()` Prusament PLA und
Gyroid neu — das ist gewollt, muss aber beim Bauen mitgedacht werden.

## Zuletzt gemeldet, ungeprüft — bitte zuerst

Vom Gerät, nach der letzten .ipa. Nichts davon ist untersucht.

### Arrange tut nichts
Der Weg: `WerkzeugSchiene.ausfuehren("arrange")` → `model.arrange()` →
`psm_arrange(gap_mm)`. Zu prüfen, in dieser Reihenfolge:

1. Kommt der Aufruf überhaupt an? Der Knopf steht in der linken Spalte
   und hängt **nicht** an `brauchtAuswahl` — er sollte also immer
   auslösen.
2. `psm_arrange` bricht mit `PSM_ERR_GENERIC` ab, wenn
   `get_bed_shape(s->config)` leer ist („Druckbettgeometrie fehlt").
   Seit dieser Sitzung meldet der Kern seine Gründe: die Zeile steht
   im Protokoll und über `PsmLog.recent` auch in der App.
3. Der Verdacht mit der höchsten Wahrscheinlichkeit: `psm_arrange`
   schreibt vor dem Anordnen alle Instanzen über
   `s_multiple_beds.set_instance_bed(..., 0)` auf Bett 0 und setzt
   `set_active_bed(0)`. Wer auf Bett 2 arbeitet, ordnet damit
   möglicherweise ein anderes Bett an oder gar keines. Das ist auch
   die Stelle, an der „alle Betten / aktuelles Bett" ansetzen muss
   (Punkt 5 der Liste oben) — beides zusammen anfassen.

### Objekte mit dem Finger schieben
Gewünschtes Verhalten, klar getrennt:

* **Ohne aktiven Griff:** ein Objekt anfassen und ziehen verschiebt es
  direkt. Das ist die Erwartung auf einem Touchgerät.
* **Mit aktivem Move-Griff:** nur noch über die Pfeile, damit man
  achsweise genau arbeiten kann.

Die Weiche steht in `ViewportView.touchesMoved`:

```swift
if gizmoAxis >= 0      { vp.gizmoDrag(...) }
else if dragObject     { vp.dragSelected(...) }
else                   { vp.orbit(...) }
```

`dragObject` wird beim Berührungsbeginn gesetzt. Zu ändern: ohne Griff
soll eine Berührung, die auf einem Objekt beginnt, `dragObject` setzen
(heute vermutlich nur bei bereits ausgewähltem Objekt); mit Move-Griff
soll `dragObject` **nie** gesetzt werden, sondern ausschließlich
`gizmoAxis` greifen.

### Die Pfeile sind weiterhin schlecht zu treffen
Dritte Meldung dazu — die bisherigen Verbesserungen haben nicht
gereicht. Die Trefferprüfung sitzt in `psm_viewport_gizmo_pick` bzw.
`psm_viewport_get_gizmo_axis` im Viewport. Ansätze:

* Trefferzone in **Bildschirmpixeln** statt in Millimetern rechnen —
  am Modell weit weg ist ein Pfeil sonst wenige Pixel breit.
* Mindestbreite unabhängig vom Zoom, wie bei PrusaSlicers eigenen
  Gizmos.
* Größe der Pfeile an `ps.touch(44)` koppeln statt an die
  Objektgröße.

Vorher messen: einen Test schreiben, der an definierten Bildpunkten
neben der Pfeilachse tippt und prüft, ab welchem Abstand der Griff noch
anspricht. Ohne Zahl wird das die vierte Runde Raten.

### Auch die Bettsperre gilt nur für die Sitzung
Dasselbe wie bei den Bettnamen, und aus demselben Grund: eine gesperrte
Platte heißt „hier bin ich fertig, das Anordnen soll sie in Ruhe
lassen" — das gilt für dieses Projekt und nicht für alle künftigen. Wer
die App neu startet, muss ein frisches Bett vorfinden.

Zu ändern zusammen mit den Namen: `lockedBeds` und `bedNames` in
`SlicerModel` liegen heute in den App-Einstellungen und überleben damit
den Neustart. Beide gehören in den Sitzungszustand — oder mit dem
Projekt ins 3MF, wenn sie überhaupt überdauern sollen. Auf Android
dasselbe prüfen.

### „Objects" steht doppelt (aus dem Umbau von heute)
Beim Aufklappen von *Objekte* erscheint die Überschrift zweimal. Grund
ist der Umbau der Seitenleiste auf gestapelte Bereiche: `bereich(_:)` in
`AdvancedWorkspaceView` zeichnet jetzt die Überschrift außen, und die
Blöcke darunter bringen ihre eigene noch mit
(`abschnitt(...)` am Anfang von `objektliste`, vermutlich ebenso in
`profilblock`, `bearbeitenBlock` und `werkzeugeBlock`).

Fix: die inneren Überschriften entfernen, nicht die äußeren — die
äußeren tragen den Aufklapp-Pfeil und die Kennung `inspektor.<name>`,
an der die UI-Tests hängen. Alle vier Blöcke durchsehen, nicht nur den
gemeldeten.

### Die Objektliste zeigt nur das aktive Bett
`model.objects` ist immer die Liste des aktiven Betts — der Kern hält je
Bett ein eigenes `Model` (`s->bed_models`). Wer drei Betten bestückt hat,
sieht in der Liste nur ein Drittel seiner Arbeit und kann nicht
erkennen, was auf den anderen liegt.

Gewünscht: alle Objekte, nach Bett gruppiert, mit dem aktiven
hervorgehoben — und ein Antippen springt auf das jeweilige Bett. Das ist
dieselbe Baustelle wie „mehrere Betten auf einem Schirm" (Punkt 4) und
gehört mit ihr zusammen gedacht: dort wird die Darstellung räumlich,
hier in der Liste.

Was fehlt: eine ABI-Funktion, die Objekte eines **bestimmten** Betts
liefert. Heute gibt es nur `psm_bed_object_count(s, i)` für die Anzahl.
Entweder um ein `psm_bed_object_at(s, bed, index, …)` erweitern oder die
vorhandenen Objektfunktionen um einen Bettindex ergänzen — Ersteres ist
weniger invasiv.

### Die Objektleiste im Advanced Mode sitzt falsch
Aus meinem eigenen Einbau von heute — `SimpleObjectBarView`, eingehängt
in `AdvancedWorkspaceView` über der Platte. Drei Fehler:

1. **Sie läuft über die Seitenleiste.** Sie bekommt die volle Breite,
   obwohl rechts das Band steht. Der Schichtregler löst dasselbe
   Problem ein paar Zeilen darüber richtig: er rechnet
   `seiteOffen && !schmal` in seinen `padding(.trailing)` ein. Dieselbe
   Rechnung übernehmen.
2. **Sie ist länger als nötig.** Sie ist als `ScrollView(.horizontal)`
   über die ganze Breite gebaut; sie sollte nur so breit sein wie ihre
   Knöpfe (`fixedSize(horizontal: true, vertical: false)` und zentriert
   über dem Objekt).
3. **„Zurück" gehört im Advanced Mode nicht hinein.** Im Einfachen Modus
   ist es der einzige Weg, die Auswahl zu lösen; im Advanced Mode gibt
   es dafür die Objektliste und das Antippen daneben. Am besten als
   Parameter (`zeigtZurueck: Bool = true`), damit der Einfache Modus
   unberührt bleibt.

Dazu: **„Hinlegen" versteht niemand.** Der Nutzer fragt, wofür der Knopf
da ist. Er legt die größte ebene Fläche nach unten (`layFlat` →
`psm_model_lay_flat_auto`). Ein Wort dafür finden, das das sagt — und
sobald die Flächenanzeige steht (Punkt 1 der Liste), gehören beide
Hinlegen-Wege ohnehin zusammen betrachtet.

## Nach dem Zusammenfuehren gemeldet (Stand 97114d6)

### Die Bettkarten sind zu schmal fuer ihren Inhalt
Foto vom iPad: In der Bettleiste steht statt des Namens nur "Be...", und
"0 objects" bricht ueber drei Zeilen um. Vier Elemente - Name,
Objektzahl, Sperrsymbol, Loeschsymbol - passen in dieser Kartenbreite
nicht nebeneinander, und der Text weicht nach unten aus, statt sich zu
verkleinern.

Zu aendern in der Bettleiste von AdvancedWorkspaceView, seit dem Umbau
mit psm_bed_metadata:

* lineLimit(1) und minimumScaleFactor(0.75) auf beide Textzeilen - so
  macht es die Werkzeugschiene fuer ihre Beschriftungen bereits.
* Karte breiter oder die Objektzahl kuerzer: nur die Zahl statt
  "0 objects", die Einheit steht schon in der Ueberschrift "Beds".
* Sperr- und Loeschsymbol gehoeren eher in ein Kontextmenue der Karte
  als dauerhaft hinein - sie kosten die Breite, die der Name braucht.

Auf einem iPhone wird es noch enger: die Leiste scrollt waagerecht, die
Karten haben aber feste Breite.

### Die Betten stehen weiterhin nicht nebeneinander
Der Wunsch aus Punkt 4 der Liste oben ist damit offen: alle Betten auf
einem Schirm, wie in der PC-Fassung, mit den Knoepfen oben zum
Umschalten und Namen ueber den Betten. Codex hat die Auswahl und das
Anordnen je Bett gebaut (psm_arrange_bed, psm_bed_metadata) - die
raeumliche Darstellung im Viewport fehlt aber weiter. Der Viewport
zeichnet nach wie vor nur das aktive Bett.

Was dafuer noch fehlt, steht unveraendert in Punkt 4: Geometrie und
Objekte je Bett versetzt zeichnen, Treffererkennung mit Versatz, und
eine ABI-Funktion Welt nach Bildschirm fuer die Namen.

### Selbsttest auf dem iPad: elf gruen, einer rot
Kein Absturz mehr, und der rote Schritt nennt seinen Grund - die
Protokollbruecke arbeitet.

Fehlgeschlagen ist "G-Code schreiben" mit:

    The file could not be opened because it is not in the correct
    format. - slic3r: flatten_configbundle_hierarchy: The preset
    Prusament Resin Model Transparent Clear @0.1 SL1S inherits an
    unknown preset "*legacy_slow*"

Das ist kein Fehler am Export, sondern ein kaputtes SLA-Profil in den
mitgelieferten Ressourcen: das Resin-Profil erbt von einer Vorlage
*legacy_slow*, die im Buendel fehlt. PrusaSlicer bricht beim Aufloesen
der Vererbung ab, und das trifft jeden Schritt, der danach die
Konfiguration braucht.

Zwei Ansaetze, beide in build/scripts/stage-resources.sh:

* PrusaResearchSLA wird unvollstaendig kopiert - die Vorlage
  *legacy_slow* steht in der Vendor-Datei, wird aber weggeschnitten.
* Oder das SLA-Buendel gehoert gar nicht mitgeliefert, solange es keine
  SLA-Unterstuetzung gibt. Dann faellt der Fehler von selbst weg.

Zum Vergleich die gruenen Werte vom Geraet, sie sind die erste echte
Messung auf Hardware: Schneiden 0,1 s, Projekt sichern und wieder laden
7 kB, Bemalen 2 Facetten, mehrfarbig T1 46 mm3 und T2 3002 mm3,
Lasttest mit 25 Koerpern 1,1 s. Aufgabe 30 - Lasttest auf dem Geraet -
ist damit beantwortet: das iPad packt es muehelos.

### Regression: die Bettleiste erscheint im Easy Mode
Sie gehoert dort nicht hin. Der Einfache Modus arbeitet bewusst auf
einem Bett - mehrere Betten sind ein Werkzeug des Expertenmodus, und
die Leiste hat im Easy Mode weder Platz noch Zweck. Dazu kommt der
Umbruchfehler aus dem Abschnitt oben, der hier genauso auftritt.

Entstanden ist es beim Umbau der Bettauswahl (psm_bed_metadata,
psm_arrange_bed): die Leiste wurde offenbar in eine gemeinsame Stelle
gezogen, die beide Modi zeichnen. Zu pruefen ist SimpleModeView - dort
darf sie nicht erscheinen - und ob die Bettwahl im Easy Mode ueberhaupt
noch einen Weg hat oder stillschweigend auf Bett 1 festliegt.

Reihenfolge fuer die naechste Sitzung, weil beides zusammenhaengt:
zuerst die Leiste aus dem Easy Mode nehmen, dann die Karten im Advanced
Mode auf eine Breite bringen, in der ihr Text steht.

## Nachtsitzung 6. August, direkt vom Geraet gemeldet

Vier Punkte, keiner davon umgesetzt - nur die ersten drei oben (Bettleiste,
Bettkarten, Arrange-Geste) sind diese Sitzung schon gefixt und committet
(`04b2c3d`).

### Filamente werden in der Filament-Uebersicht nicht uebernommen
Gemeldet, noch nicht untersucht. Verdacht: `MaterialAuswahlView` waehlt
ueber `model.selectPreset(.filament, name)` - zu pruefen ist, ob die
Auswahl pro Werkzeug (T1-T8) tatsaechlich im Kern ankommt oder nur der
zuletzt aktive Slot uebernommen wird. `standardwerteSetzen()` in
`SlicerModel.swift` setzt beim Druckerwechsel ohnehin auf Prusament PLA
zurueck - das koennte der Effekt sein, den der Nutzer als "nicht
uebernommen" sieht, wenn er vorher ein anderes Profil je Werkzeug
gewaehlt hatte.

### Keine Profilsuche in den Einstellungen
Weiterhin offen, siehe Abschnitt "Profilauswahl in die Einstellungsseiten"
weiter oben - unveraendert seit der letzten Uebergabe. Bausteine sind da:
`model.presetNames(_:)`, `model.selectPreset(_:_:)`,
`MaterialAuswahlView` als Vorlage fuer Suche und Filter.

### Hochkant skaliert falsch
Foto vom iPhone in Portrait: die Oberflaeche wirkt wie fuer Querformat
gebaut und nicht neu einsortiert - Werkzeugleisten und Panels liegen
verschoben. `ResponsiveLayoutUITests.swift` (aus dieser Sitzung von
Codex) deckt das offenbar nicht ab, obwohl es Punkt "Kleineres" nahesteht.
Zu pruefen: `ps.windowSize` / `psScale` bei Rotation, und ob die
Bildschirme case-basiert auf `schmal`/`kompakt` reagieren oder auf eine
angenommene Seitenlage.

### Der Viewer friert nach "On face" ein
Nutzer meldet: nach dem Werkzeug "On face" (Objekt an eine angetippte
Flaeche ausrichten, `psm_model_align_to_face` o.ae.) reagiert der
Viewport nicht mehr - der Rest der App bleibt bedienbar, es ist also kein
App-Absturz, sondern der Viewport-Thread oder ein GL-Zustand haengt.
Zeitlich nah an `386f317 fix: richte getroffene Instanz auf Flaeche aus`
aus dieser Sitzung - dort ansetzen. Noch nicht reproduziert, keine
Codestelle bestaetigt.

## Feedback vom Nutzer, 6. August nachts (noch nicht umgesetzt)

Praezisierungen zu den vier Befunden weiter oben:

* **Hochkant skaliert falsch**: bisher nur auf dem **iPad** getestet, das
  iPhone stand noch nicht auf der Werkbank. Der automatisierte Testlauf
  dieser Sitzung bestaetigt zusaetzlich einen konkreten iPhone-Fehler:
  `PreviewUITests.testPhoneVerwendetBottomSheetMitBeidseitigemRange`
  schlaegt fehl - im Vorschau-Bottom-Sheet auf dem Telefon fehlt der
  untere Schichtregler (`vorschau.layer.unten`). Das ist vermutlich nur
  ein Teil des gemeldeten Skalierungsproblems, aber der erste bestaetigte
  Teil.
* **Variable Schichthoehe**: der Punkt braucht zusaetzlich **adaptive
  Schichthoehe** (automatische Berechnung nach Kontur, nicht nur die
  manuelle Kurve). Bisher nicht im Umfang.

Neue Punkte:

### Bettleiste: Funktion behalten, altes Aussehen zurueck
Das Sperren/Umbenennen als Funktion ist gewollt, aber optisch war die
Fassung vor Codex' Umbau (`a557d08`) besser: eine **einzeilige,
waagerecht scrollende Reihe schmaler Kapseln** (`bettleiste` in der
damaligen `AdvancedWorkspaceView.swift`, ca. 38pt hoch, Name + Objektzahl
in einer Zeile, Schloss nur als Icon wenn gesperrt, Umbenennen/Sperren
per **Kontextmenue via langem Druck** statt dauerhaft sichtbarer
Mini-Knoepfe). Die heutige `BedSelector.swift` baut stattdessen ein
Kartenraster mit 64pt hohen Karten und eigener Ueberschriftenzeile - das
ist der Sprung, der als "blockiert" empfunden wurde. Ziel: die
kompakte Kapselreihe zurueckbauen, Sperren/Umbenennen/Loeschen bleiben
erreichbar (Kontextmenue statt Mini-Knoepfe), Arrange-Verhalten
(Tipp/Halten) bleibt wie eben gebaut.

### Weitere Hersteller-Profile mit Bettmodellen
Sind schon vorbereitet: `build/scripts/stage-resources.sh` kennt
`PSM_VENDORS=all` (36 Hersteller statt nur Prusa, ca. 41 statt 6 MB) -
Bettmodelle/-texturen liegen je Hersteller-Unterordner bei
(`profiles/<Hersteller>/*_bed.stl` etc.), soweit PrusaSlicer sie
mitliefert. Fuer die naechste IPA aktiviert.

### Filamente: Spulendesign
Die Karte in `MaterialAuswahlView.swift` zeigt aktuell eine simple
Kapsel-Grafik (`spule(_:)`) statt eines echten Spulenbilds. Gewuenscht:
ein Design, das wirklich wie eine Filamentspule aussieht, nicht nur ein
Farbklecks in Kapselform.

### Advanced-Kopfzeile: grosse Umstrukturierung
Mehrere Punkte zusammen, weil sie dieselbe Leiste betreffen:

1. Neben "Print Settings" liegt ein Zahnrad fuer App-Einstellungen - der
   Punkt "App Settings" steht aber schon oben in der Leiste. App
   Settings soll **unten links** stehen, nicht oben.
2. Der obere Punkt "View" kann weg (redundant, siehe Beschreibung des
   Nutzers "der ist ueber").
3. Der obere Punkt "Printers" soll ebenfalls **unten links** neben den
   Einstellungen stehen, nicht oben in der Leiste.
4. "Volumes" links ist in seiner Funktion unklar - zu pruefen, was es
   tut und ob es so bleiben soll oder umbenannt/entfernt gehoert.
5. Rotate/Scale/Move gehoeren nicht fest oben ins Bild, sondern in die
   Leiste, die beim Anklicken eines Objekts erscheint (die
   Objektleiste) - dort aber mit kleineren Symbolen, und die ganze
   Leiste soll direkt ueber dem angeklickten Objekt schweben statt fix
   oben zu stehen.

### Slice-now-Popup ersetzen
Nach dem Schneiden soll statt des aktuellen Popups **"Export G-Code"**
und ein eigener **"Send to Printer"**-Knopf erscheinen, letzterer sendet
direkt an PrusaLink.

### Allgemeiner Feinschliff Easy Mode
Symbole sollen optisch besser zusammenpassen, Menues platzsparender und
klarer strukturiert werden, am Vorbild EasyPrint (siehe bereits
vorhandene Referenz in `docs/superpowers/specs/
2026-07-31-easy-advanced-ui-design.md` und die Kommentare in
`MaterialAuswahlView.swift`).

**Reihenfolge, mit der ich das angehe** (Bugs zuerst, dann Features):
1. Viewer-Freeze nach "On face"
2. Filamente nicht uebernommen (erst reproduzieren)
3. iPhone-Vorschau-Regler (jetzt per Test bestaetigt)
4. Kaputtes SLA-Profil
5. Bettleiste optisch zurueckbauen (Kapselreihe, Funktion behalten)
6. Advanced-Kopfzeile umbauen (App Settings/Printers nach unten links,
   View weg, Objektleiste mit Rotate/Scale/Move schwebend am Objekt)
7. Slice-Popup durch Export G-Code / Send to Printer ersetzen
8. Filament-Spulendesign
9. Restliche Feature-Liste wie zuletzt besprochen (Flaechen beim
   Hinlegen, adaptive + variable Schichthoehe, Pinseloptionen,
   Mehrbett-Ansicht im Viewport, Profilsuche, Vorschau-Farben)

## Nachtsitzung 6. August, umgesetzt und geprüft

Diese Commits sind auf `main`, mit dem vollen Testlauf gegengeprueft
(einzeln beschrieben, keine Sammel-Uebersicht noetig - siehe `git log`):

* SLA-Vendor-Buendel raus (kaputtes Preset, ausser Scope, `f57627f`)
* Bettleiste als Kapselreihe wie vor dem Umbau, Sperren im Kontextmenue (`c4402bb`)
* Arrange: Tipp = alle Betten, Halten = Panel (bereits in der vorigen Runde, `04b2c3d`)
* iPhone-Vorschau scrollbar, unterer Schichtregler nicht mehr abgeschnitten (`12f5b52`)
* Extruderbank: Materialwahl landet jetzt am angetippten Werkzeug T1-T8, nicht mehr immer im allgemeinen Preset (`6df838b`)
* Werkzeugschiene "Volumes" ruft jetzt wirklich splitVolumes auf statt split zu wiederholen (`2eb3d87`)
* Profilsuche in den Einstellungen, mit Suchfeld (`2aa9dff`)
* Fremdherstellerprofile aktiviert (PSM_VENDORS=all fuer die naechste IPA)
* "View"-Knopf aus der Werkzeugleiste entfernt (redundant zu "3D" in der ansichtsleiste)

## Funktionsaudit: was im Core-Wrapper existiert, aber nirgends aufgerufen wird

Auf Bitte durchsucht (`grep` ueber alle `.swift`-Aufrufe ausserhalb von
`ios/PSMobile/Core/`). Kein TODO/FIXME/Platzhalter-Kommentar im ganzen
iOS- und Kern-Baum gefunden - der einzige echte Treffer war der
Volumes-Bug oben. Diese drei Funktionen sind vorhanden und funktionsfaehig,
aber ohne Oberflaeche:

* **`filamentVendors()` / `setFilamentVendors(_:)`** (`PsmCoreSetup.swift`):
  ein Herstellerfilter fuer die Filamentliste liegt fertig im Kern, seit
  heute Nacht mit 34 statt einem Hersteller besonders relevant - ohne
  Filter wird `MaterialAuswahlView` mit sehr vielen Eintraegen unhandlich.
  Naechster Schritt: eine Kapselreihe wie `typknoepfe`/`farbpunkte` in
  `MaterialAuswahlView.swift`.
* **`presetCompatible(_:at:)` / `showsIncompatiblePresets`**
  (`PsmCoreSetup.swift`): keine Liste (Drucker-, Filament-, Print-Settings-
  Auswahl) filtert nach Kompatibilitaet mit dem aktiven Drucker. Man kann
  heute ein Filament waehlen, das der Drucker gar nicht unterstuetzt.
* **`clearCustomGcode()`** (`PsmCoreProject.swift`): `CustomGcodeView`
  kann Marken nur hinzufuegen, es gibt keinen "alle leeren"-Knopf.

Keines davon wurde diese Nacht angefasst - Risiko/Zeit-Abwaegung bei einer
Deadline, nicht vergessen.

## G-Code-Vorschau zeigt nie den G-Code (untersucht, nicht gefixt)

Mit einem eigens gerechneten Kegel (viele Facetten, 299 Schichten,
`Testkoerper.kegel`, nur fuer diese Untersuchung, Debug-Start
`-psm-load-kegel`) im Simulator nachvollzogen: nach dem Schneiden und
Oeffnen der Vorschau zeigt der Viewport weiterhin das massive Modell,
nie die Werkzeugwege - bei jedem Schichtregler-Stand und aus jedem
Blickwinkel (Screenshots liegen der Nachricht an den Nutzer bei). Die
rechte Karte selbst ist korrekt: Statistik, Schichtbereich, Merkmal-
und Extruder-Chips stimmen mit dem echten Schnittergebnis ueberein -
nur der 3D-Viewport dahinter zeichnet nicht um.

Eingegrenzt, nicht behoben:

* Die Kette Swift -> Kern ist auf den ersten Blick korrekt:
  `AdvancedWorkspaceView` setzt `viewportMode: vorschau ? .preview :
  .editor`, `ViewportView.updateUIView` ruft bei `vorschauBetreten`
  `vp.loadPreview()` und danach `vp.mode = .preview`.
  `psm_viewport_load_preview` (`psm_viewport.cpp:2029`) laedt das
  Ergebnis, setzt `gcode_loaded = true` und gibt `true` zurueck -
  nichts davon schlaegt sichtbar fehl (kein Eintrag in `PsmLog`).
* `psm_viewport_render` (Zeile 1236) prueft `mode == PSM_VIEW_PREVIEW
  && gcode_loaded` und zeichnet dann `gcode_viewer.render(...)` statt
  der Meshes. Ein testweise eingebauter Log-Zaehler in genau dieser
  Zeile zeigte ueber den gesamten Testlauf (Schneiden, Vorschau
  oeffnen, Schichtregler auf 1-299 ziehen, Ansicht "Top") nur **einen
  einzigen Render-Aufruf insgesamt**, und der noch mit `mode=0
  gcode_loaded=0` - also aus der Editor-Phase, vor dem Umschalten.
  Nach dem Wechsel in die Vorschau feuerte der Renderaufruf nie wieder.
* `PSMGLView.requestRender()` markiert nur `brauchtBild = true`; ein
  laufender `CADisplayLink` soll das naechste Bild zeichnen
  (`ViewportView.swift:323-338`). Ob der Display-Link im
  XCUITest-Simulator zuverlaessig weiterlaeuft, nachdem die
  Automation-Session die App als "idle" einstuft, ist die
  wahrscheinlichste Erklaerung - und genau der Punkt, an dem eine
  Ferndiagnose ohne echtes Geraet unsicher wird. Es ist offen, ob das
  ein reines Simulator-/XCUITest-Artefakt ist oder auch auf einem
  echten iPad auftritt.

**Naechster Schritt, mit einem Geraet statt dem Simulator**: denselben
Kegel-Testkoerper laden (Debug-Argument bleibt drin,
`-psm-load-kegel`), schneiden, Vorschau oeffnen, direkt beobachten ob
der Viewport ueberhaupt neu zeichnet. Zeichnet er auf dem Geraet auch
nicht um, liegt es nicht am CADisplayLink/Simulator, sondern tiefer -
dann als naechstes pruefen, ob `vp.mode` tatsaechlich `.preview`
erreicht (z. B. testweise `psm_viewport_get_mode` direkt nach dem
Setzen abfragen) oder ob dort schon der Rueckstand entsteht.

Bewusst nicht blind angefasst: derselbe Codebereich (Viewport-Rendering
nach einem Gizmo-Wechsel) steht schon unter Verdacht fuer den separat
gemeldeten Freeze nach "On face" - zwei ungetestete Aenderungen an
derselben heiklen Stelle in einer Nacht waeren nicht mehr auseinander-
zuhalten, wenn etwas schiefgeht.

