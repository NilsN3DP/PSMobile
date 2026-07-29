# Fehler und Bedienprobleme

Stand: 2026-07-29, erster systematischer Durchgang.

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

### B1 · Druckbett ist ein flaches Vieleck
PrusaSlicer liefert echte Bettmodelle (`mk4_bed.stl`, `mini_bed.stl`,
`coreone_bed.stl` …) und 18 Betttexturen mit. Die STLs sind bereits in
den Assets, **die Texturen loescht `stage-resources.sh` aber weg**
(`find … -name '*.svg' -delete`). Der Viewport zeichnet stattdessen ein
Vieleck aus `bed_shape`.

*Ansatz*: Texturen nicht mehr loeschen; im Viewport `bed_model` und
`bed_texture` aus dem Druckerprofil laden und statt des Vielecks
zeichnen. Das ist uebernehmbare Information - siehe E-12.

### B2 · Kein Weg zurueck in die Druckerauswahl
`SlicerService.reopenSetup()` existiert, ist aber an keine Schaltflaeche
gehaengt. Wer einen zweiten Drucker hat, kommt nicht mehr an die Auswahl.

### B3 · Senden geht immer an den ersten Drucker
`linkPrinters.first()` ist fest verdrahtet. Bei mehreren Geraeten fehlt
die Auswahl.

### B4 · Schalter "nur eingerichtete Drucker" filtert nichts
Wird gespeichert, aber `refreshPresets()` wertet ihn nicht aus.

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

### B6 · Geaenderte Einstellungen sind nicht als geaendert erkennbar
PrusaSlicer haengt "(modified)" an den Profilnamen, sobald ein Wert
abweicht. Bei uns weicht die Konfiguration still vom benannten Profil ab.

### B7 · Skalieren auf Groesse ignoriert die Drehung
`psm_model_scale_to_fit` misst `raw_mesh_bounding_box`, also ungedreht.
Nach einer Drehung ist das Ergebnis falsch.

### B8 · Einstellungen waehrend des Slicens
Werte lassen sich aendern, waehrend ein Job laeuft. `Print::apply` hat die
Konfiguration da schon uebernommen - die Aenderung wirkt still erst beim
naechsten Lauf.

---

## C - Politur

### C1 · Digest-Herausforderungen nicht threadsicher
`PrusaLink.challenges` ist eine gewoehnliche `mutableMapOf`, wird aber aus
IO-Coroutinen gelesen und geschrieben.

### C2 · Zugangsdaten unverschluesselt
API-Schluessel und Passwort liegen in gewoehnlichen SharedPreferences.
Vor einer Veroeffentlichung auf EncryptedSharedPreferences umstellen.

### C3 · Undo, Redo, Ausschneiden, Einfuegen, Layer-Editing
Die Werkzeuge sind sichtbar, aber dauerhaft inaktiv. Entweder umsetzen
oder ausblenden - dauerhaft graue Knoepfe sind schlechter als keine.

### C4 · Objektliste ohne Baum
Nur Name und Groesse. PrusaSlicer zeigt Volumen, Modifikatoren und
Einstellungen je Objekt.

---

## Was geprueft wurde und in Ordnung ist

- Datei mit Muellinhalt: sauber abgefangen, Fehlermeldung erscheint,
  kein Absturz.
- Leere Datei: kein Absturz.
- Zweimal hintereinander slicen: kein Absturz mehr, seit
  `teardown_print()` die Callbacks vor der Freigabe entschaerft.
- Import mit Umlauten im Dateinamen: laeuft.

## Noch nicht geprueft

- Grosse Modelle, Speicherdruck, Verhalten unter dem Low-Memory-Killer.
- Drehen des Geraets und Wechsel in den Hintergrund waehrend des Slicens.
- Alle 247 Parameter einzeln aendern und pruefen, ob sie wirken.
- **Alles rund um PrusaLink gegen ein echtes Geraet.**
