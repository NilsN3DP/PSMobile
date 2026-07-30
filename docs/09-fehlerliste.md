# Fehler und Bedienprobleme

Stand: 2026-07-30. „BEHOBEN (coded)“ bedeutet, dass die Korrektur im
Quellcode vorliegt; bei Native-Änderungen steht der aktuelle
Android-Link- und Gerätetest noch aus.

Gefundenes wird hier gesammelt, **nicht sofort alles behoben**. Erst
sammeln, dann nach Schwere abarbeiten. Wer einen Punkt erledigt, hakt ihn
ab und traegt den Commit ein.

Legende: **A** = macht die App unbrauchbar oder verliert Daten,
**B** = stoert im Alltag deutlich, **C** = Politur.

---

## A - Muss vor jedem ernsthaften Test weg

### A1 · Eingehende Datei holt die Ansicht nicht zurueck — BEHOBEN
Behoben im Loop-Durchlauf 01:20. Der Navigationszustand liegt jetzt als
`SlicerService.Screen` im Service statt in einem lokalen `remember`, und
`loadModel` ruft `showBed()`. Am Geraet nachgeprueft.

**Ursprungsbefund, gemessen.** Ein Modell per Teilen oder Oeffnen zu schicken, waehrend
der Drucker- oder Einstellungsbildschirm offen ist, laedt das Modell
unsichtbar im Hintergrund. Der Nutzer sieht weiter die alte Seite und
haelt die App fuer kaputt.

*Ort*: `MainActivity.handleIncomingIntent` setzt keinen Zustand zurueck;
`SlicerScreen` haelt `showPrinters` und `settingsTab` in lokalem
`remember`.
*Ansatz*: Beide Zustaende in den Service heben oder beim Import ein
Ereignis senden, das die Oberflaeche auf das Bett zurueckstellt.

### A5 · Modell wird doppelt importiert — BEHOBEN
Beim Nachpruefen von A1 aufgefallen: Ein eingehender VIEW-Intent erzeugte
mit dem Standard-Startmodus eine **zweite** Activity-Instanz. Deren
`onServiceConnected` importierte das Modell ein zweites Mal - auf dem
Bett lagen zwei identische Wuerfel.

Behoben durch `android:launchMode="singleTask"` und eine Sperre, die
denselben Intent nicht zweimal auswertet. Am Geraet nachgeprueft: nur
noch ein Objekt, und der Emulator meldet, dass der Intent an die
bestehende Instanz zugestellt wurde.

### A2 · Einstellungen fragen pro Bild ueber JNI ab — BEHOBEN
Behoben im Loop-Durchlauf 01:45. Die Metadaten werden jetzt einmal je
Seite, Stufe und Profilrevision in einem `remember` geholt statt bei
jeder Neuzeichnung.

*Ursprungsbefund*: `SettingsScreen` rief `core.configMeta(key)` fuer
jeden Parameter direkt in der Komposition auf - bei 40 sichtbaren
Parametern 40 JNI-Aufrufe je Bild.

### A3 · Werte veralten beim Profilwechsel — BEHOBEN
Behoben im Loop-Durchlauf 01:45. `SlicerService.configRevision` steigt
bei jedem Profilwechsel und jeder Neuinstallation; die Zeilen haengen
ihren zwischengespeicherten Wert daran auf.

Am Geraet nachgeprueft: Wechsel von "0.20mm SPEED @MK4S 0.4" auf
"0.10mm FAST DETAIL @MK4S 0.4" laesst die Schichthoehe von 0,2 auf 0,1
mitziehen. Vorher blieb 0,2 stehen.

*Ursprungsbefund*: `SettingRow` hielt den Wert in `remember(meta.key)`
und schrieb den alten Wert beim naechsten Antippen in die neue
Konfiguration zurueck.

### A4 · Sprache wird nicht gemerkt — BEHOBEN
Behoben im Loop-Durchlauf 04:45. Die Ersteinrichtung meldet die Wahl
ueber `onLanguageChange` nach oben, MainActivity schreibt sie nach
`SlicerService.uiLanguage`.

Am Geraet nachgeprueft: Deutsch in der Ersteinrichtung gewaehlt,
eingerichtet, App beendet und neu gestartet - die Seitenleiste zeigt
"DRUCKER / DRUCKEINSTELLUNGEN / FILAMENT" statt Englisch.

*Ursprungsbefund*: `PsUi.setLanguage` wurde gerufen, die Wahl aber nie
gespeichert.

**Damit ist die Stufe A vollstaendig abgearbeitet.**

---

## B - Stoert im Alltag

### B1 · Druckbett ist ein flaches Vieleck — BEHOBEN
Der Viewport lädt `bed_model` und `bed_texture` aus dem aktiven
Druckerprofil und rendert PrusaSlicers Bettmodell samt Textur. Die
Umsetzung wurde bereits auf dem Android-Gerät geprüft; siehe
`docs/08-loop-protokoll.md` und die Commits `e52a4a1`/`e76a7c2`.

### B9 · Wiedergeoeffnete Druckerauswahl ist leer — BEHOBEN
Behoben im Loop-Durchlauf 05:45. SetupScreen nimmt eine Vorauswahl
entgegen, MainActivity reicht installedPrinters() durch.

Am Geraet nachgeprueft: Nach Einrichtung mit MK4S und 0.4er Duese zeigt
die wiedergeoeffnete Auswahl "1 / 35", die MK4S ist angehakt und
aufgeklappt, die 0.4er Duese markiert.

*Ursprungsbefund:*
Beim Nachpruefen von B2 aufgefallen: Oeffnet man die Druckerauswahl
erneut, steht sie auf "0 / 35" und nichts ist angehakt - obwohl Drucker
installiert sind. Wer nur eine Duesengroesse ergaenzen will, muss seine
gesamte bisherige Auswahl aus dem Gedaechtnis wiederherstellen.

*Ansatz*: `SetupScreen` mit `PrinterStore`-artiger Vorbelegung starten.
Die Auswahl liegt bereits in den App-Einstellungen unter `printers`;
`SlicerService.installedPrinters()` liefert sie. Nur als Startwert von
`selected` durchreichen.

### B2 · Kein Weg zurueck in die Druckerauswahl — BEHOBEN
Behoben im Loop-Durchlauf 05:15. Die Druckerverwaltung hat jetzt den
Eintrag "Druckermodelle aendern" mit der Schaltflaeche "Auswahl oeffnen",
die `SlicerService.reopenSetup()` ruft.

Am Geraet nachgeprueft: Die Auswahl geht auf, das Protokoll meldet
"37 Druckermodelle gefunden". Dabei fiel B9 auf.

*Ursprungsbefund*: `reopenSetup()` existierte, war aber an keine
Schaltflaeche gehaengt - nach der Ersteinrichtung kam man nie wieder an
die Auswahl heran.

### B3 · Senden geht immer an den ersten Drucker — BEHOBEN (built)
Bei mehreren Geräten öffnet der Senden-Knopf jetzt eine explizite
Zielauswahl. `linkPrinters.first()` wird nicht mehr als stilles Ziel
verwendet. Der Hardwaretest mit zwei Druckern steht aus.

### B4 · Schalter "nur eingerichtete Drucker" filtert nichts — BEHOBEN (built)
`refreshPresets()` wertet den gespeicherten Schalter jetzt aus und
zeigt nur Profile, die einem eingerichteten PrusaLink-Gerät zugeordnet
sind. Das aktuell aktive Profil bleibt sichtbar, damit die Auswahl beim
Einschalten nicht leer wird. Beim Verlassen der Druckerverwaltung wird
die Liste sofort neu aufgebaut.

### B5 · Alter G-Code ueberlebt das Leeren des Bettes — BEHOBEN
Behoben im Loop-Durchlauf 04:45. `psm_session_clear` loescht die Datei
und leert den Pfad; `SlicerService.clearBed` setzt zusaetzlich
`lastGcode` und die Sendemeldung zurueck.

Am Geraet nachgeprueft: Wuerfel geslict (100 Layer, 3,7 g), dann Bett
geleert. Danach sind Objekt, Statistik und der Knopf "G-Code
exportieren" verschwunden, "Jetzt slicen" ist ausgegraut, und
`last.gcode` ist von der Platte geloescht.

*Ursprungsbefund*: Nach dem Leeren bot die Oberflaeche weiter Export und
Senden an - mit dem G-Code des vorherigen Modells. Das haette einen
falschen Druck ausloesen koennen.

### B6 · Geaenderte Einstellungen sind nicht als geaendert erkennbar — BEHOBEN (emulator_tested)
Die Profilfelder zeigen Anzahl und Zustand ungespeicherter Änderungen.
Beim Profilwechsel listet ein Dialog die geänderten Werte und bietet
„auf Ziel übertragen“, „verwerfen“ und „speichern unter“ an. Im
Emulator wurde `fill_density: 15% → 25%` auf ein anderes Druckprofil
übertragen und anschließend korrekt als weiterhin geändert angezeigt.

### B7 · Skalieren auf Größe ignoriert die Drehung — BEHOBEN (coded)
`psm_model_scale_to_fit` verwendet nun die transformierte
Instanz-Bounding-Box und multipliziert den bestehenden Skalierungsfaktor.
Zusätzlich liegt „Aufs Bett einpassen“ vollständig im Core. Beides ist
im C-ABI-Vertragstest auf dem x86_64-Emulator abgedeckt; der UI-Test
steht aus.

### B8 · Einstellungen während des Slicens — BEHOBEN (coded)
Ein Slice arbeitet jetzt auf einem unveränderlichen Modell- und
Konfigurationssnapshot. Jede spätere Änderung erhöht die
Designrevision; das alte Ergebnis wird als `STALE` verworfen und kann
nicht exportiert oder gesendet werden. Der C-ABI-Ablauf ist auf dem
x86_64-Emulator geprüft; Service-/UI-Test steht aus.

---

## C - Politur

### C1 · Digest-Herausforderungen nicht threadsicher — BEHOBEN (built)
Challenges liegen in einer `ConcurrentHashMap`, der Nonce-Counter ist
atomar und unbekannte Algorithmen/qop werden abgelehnt. Parallelität und
Algorithmusauswahl sind durch JVM-Unit-Tests abgedeckt.

### C2 · Zugangsdaten unverschlüsselt — BEHOBEN (built)
API-Schlüssel und Passwörter liegen AES-256-GCM-verschlüsselt hinter
einem Android-Keystore-Schlüssel in getrennten Preferences. Diese Datei
ist von Cloud- und Device-Transfer-Backup ausgeschlossen. Der
Geräte-/Restore-Test steht aus.

### C3 · Undo und Redo — BEHOBEN (emulator_tested)
Beide Werkzeuge folgen einer begrenzten Core-Historie. Touch-Gesten und
kombinierte Aktionen werden gruppiert; Bett-, Objekt-, Volumen- und
Extruderänderungen sind abgedeckt. Die Objekt-Zwischenablage funktioniert
auch bettübergreifend; Mehrfachauswahl und Layer-Editing bleiben offen.

### C4 · Objektliste ohne Baum — BEHOBEN (emulator_tested)
Die Liste zeigt jetzt aufklappbare Volumen, Typ, Dreieckszahl und
Extruder je Objekt beziehungsweise druckbarem Teil. Erzeugungsdialoge
für Modifier und die übrigen Objektparameter bleiben offen.

---

## Was geprueft wurde und in Ordnung ist

- Datei mit Muellinhalt: sauber abgefangen, Fehlermeldung erscheint,
  kein Absturz.
- Leere Datei: kein Absturz.
- Zweimal hintereinander slicen: kein Absturz mehr, seit
  `teardown_print()` die Callbacks vor der Freigabe entschaerft.
- 20 Slices hintereinander: gleicher Prozess, 20 fertige G-Codes, kein
  Crash oder ANR.
- 41-MB-3MF im Hintergrund bei ausgeschaltetem Display: 600 Layer in
  31,0 s, danach Vorschau und Export verfügbar.
- Ein dabei reproduzierter SIGSEGV bei G-Code-Pfadkonflikten ist behoben:
  `Print::export_gcode` bekommt jetzt ein gültiges
  `GCodeProcessorResult`; ein überlappender Zwei-Objekt-Vertragstest
  deckt den früheren Nullzugriff ab.
- Abbruch eines großen Slices und direkter Neustart: beide Abläufe
  bestanden.
- 500 zufällige Touch-/Navigationsereignisse: kein Crash oder ANR.
- Import mit Umlauten im Dateinamen: laeuft.

## Noch nicht geprueft

- Verhalten unter echtem Low-Memory-Killer auf 4–6-GB-Hardware.
- Drehen des Geraets waehrend des Slicens.
- Alle 247 Parameter einzeln aendern und pruefen, ob sie wirken.
- **Alles rund um PrusaLink gegen ein echtes Geraet.**
- 3MF-Roundtrip mit Custom-G-Code/Wipe-Tower gegen Desktop-PrusaSlicer.
- Snapshot/Stale-Verhalten nach Änderung während eines laufenden Slice.
