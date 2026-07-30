# PSMobile – Roadmap

Stand: 2026-07-30. Der Ausführungsfokus ist **Android zuerst**. iOS
beginnt erst, wenn Import, Bearbeitung, Slicing, Vorschau und Ausgabe
auf Android stabil laufen.

Der belastbare Einzelstatus steht in `docs/feature-matrix.json`.
Checkboxen hier beschreiben Produktetappen; sie ersetzen keine
Build-, Geräte- oder Hardwarebelege.

## A0 – Android-Fundament

Ergebnis: Der aktuelle Core lässt sich für beide Android-ABIs
reproduzierbar bauen, testen und ohne alte `.so` in ein APK übernehmen.

- [x] PrusaSlicer 2.9.6 und mobile Abhängigkeitsauswahl
- [x] C-ABI und JNI-Grenze
- [x] Native-Quellfingerprint und hartes Staging-Gate
- [x] C-ABI-Vertragstest angelegt
- [x] `arm64-v8a` und `x86_64` mit aktuellem Quellstand neu bauen
- [x] Vertragstest auf einem x86_64-Android-Emulator ausführen
- [x] APK mit beiden aktuellen ABIs assemblieren

Der C-ABI-Test umfasst echtes Slicing, Stale-Schutz und eine generierte
Zwei-Bett-3MF. Eine seitengleiche Testvariante wurde zusätzlich auf dem
x86_64-Emulator installiert; die vorhandene anders signierte App und
ihre Daten blieben dabei unangetastet.

## A1 – Stabiler Slice-Lebenszyklus

Ergebnis: UI, Viewport und Slice-Worker teilen kein veränderliches
Jobmodell; alter G-Code kann nach Änderungen nicht exportiert oder
versendet werden.

- [x] Grad/Radiant an der Android-/C-Grenze getrennt
- [x] Modell- und Konfigurationssnapshot je Slice
- [x] Design- und Ergebnisrevision mit `STALE`
- [x] Started Foreground-Service mit sofortiger Notification
- [x] Abbruchaktion in der Notification
- [ ] Änderung während Slice auf Emulator und Gerät prüfen
- [ ] Screen-off, Activity-Neustart und Notification-Abbruch prüfen
- [ ] separaten Slicer-Prozess als eigenen Folgeplan entwerfen

Der separate Prozess gehört nicht in diese Etappe: Dafür muss zuerst
ein vollständiger Slice-Auftrag serialisierbar sein.

## A2 – Projekte und mehrere Betten

Ergebnis: Eine 3MF verhält sich eindeutig wie Geometrie oder wie ein
Projekt, und Mehrbettprojekte sind auf Touchgeräten direkt bedienbar.

- [x] 3MF-Dialog „Nur 3D-Objekte“ / „Als Projekt“
- [x] Projektkonfiguration über denselben PresetBundle-Pfad wie Desktop
- [x] passendes Drucker- und Druckprofil automatisch aktivieren
- [x] abweichende Konfiguration als projektlokales Profil behalten
- [x] Post-Processing-Skripte aus fremden Projekten entfernen
- [x] Mehrbettprojekt in getrennte mobile Betten zerlegen
- [x] direkte Bettchips statt Scrollen durch eine Bettlandschaft
- [x] Objekt gezielt auf ein anderes Bett verschieben
- [ ] Fixture: installiertes exaktes Druckerprofil
- [x] Fixture: abweichendes/projektlokales Druckerprofil
- [x] Fixture: Mehrbett plus fremdes Post-Processing
- [ ] Fixture: Mehrbett plus Custom-G-Code/Wipe-Tower
- [x] Projekt wieder als 3MF speichern und mobilen Roundtrip prüfen
- [ ] komplexen Custom-G-Code-/Wipe-Tower-Roundtrip mit Desktop prüfen

## A3 – Bearbeitung und Profile

Ergebnis: Der alltägliche FFF-Ablauf benötigt keinen Desktop.

- [x] Verschieben, drehen, skalieren, spiegeln und auf Bett legen
- [x] transformierte Größe und „Aufs Bett einpassen“ im Core
- [x] Duplizieren, Instanzen und Auto-Arrange
- [x] 317 von 370 FFF-Parametern erreichbar
- [x] Abhängigkeitsregeln aus PrusaSlicer extrahiert
- [x] Filament und Farbe je Extruder
- [x] Undo/Redo mit gruppierten Touch- und Zahlenaktionen
- [x] Objekt-/Volumenbaum mit Modifier-Typanzeige
- [x] Extruderzuweisung je Objekt und druckbarem Teil
- [ ] Profilzustand sichtbar als geändert markieren
- [ ] Projekt-/Profiländerungen vollständig speichern und verwerfen
- [ ] Mehrfachauswahl

## A4 – Ausgabe und Drucker

Ergebnis: Export und lokaler Druckerversand sind sicher, explizit und
auf realer Hardware geprüft.

- [x] G-Code-Vorschau und Export/Teilen
- [x] PrusaLink API-Key und HTTP-Digest
- [x] Zielauswahl bei mehreren Druckern
- [x] HTTPS als Standard, HTTP nur nach Freigabe je Drucker
- [x] Credentials im Android Keystore und aus Backups ausgeschlossen
- [ ] API-Key-Drucker auf Hardware prüfen
- [ ] Digest-Drucker auf Hardware prüfen
- [ ] HTTP-Warnung und Uploadabbruch testen
- [ ] Prusa Connect erst nach eigener OAuth-Client-ID

## A5 – Android-v1-Gerätegate

Ergebnis: Ein dokumentiertes APK läuft auf einem kleinen und einem
großen arm64-Gerät sowie einem x86_64-Emulator.

- [x] Import beider 3MF-Modi und Mehrbett auf x86_64-Emulator
- [x] Rotation, Skalierung, Arrange und Bettwechsel im x86_64-Emulator
- [x] Slice, Preview und Export im x86_64-Emulator; Stale-UI noch offen
- [x] Hintergrund/Screen-off/UI-Abbruch im x86_64-Emulator
- [ ] 4–6-GB-Gerät und ≥8-GB-Gerät mit großen Modellen
- [ ] PrusaLink-Hardwaretests
- [x] Emulator-Ergebnisse und Fingerprint in `docs/release/android-v1-gate.md`

## A6 – Noch fehlende Desktop-Funktionen

Nach dem Stabilitätsgate, in dieser Reihenfolge:

1. Mehrfachauswahl und Auswahlbefehle (Clipboard-Einfügen ist umgesetzt).
2. Profil-Speichern/Verwerfen auf realer Hardware verifizieren.
3. Custom-G-Code-/Wipe-Tower-Roundtrip mit Desktop-PrusaSlicer.
4. Flach legen, schneiden und vereinfachen.
5. Bett-, Wischmatrix- und G-Code-Ersetzungsdialoge.
6. Supports, Naht, Fuzzy Skin und MMU-Farbe bemalen.
7. Messen sowie Text/SVG prägen.

Desktop-spezifische Fenster-, Instanz-, Ordner- und
SD-Karten-Menübefehle sind keine Mobile-Paritätsziele.

## I1 – iOS-Portierung

Beginnt erst nach A5:

- [ ] statische Core-Library und reproduzierbarer macOS-CI-Build
- [ ] Swift-Concurrency-Adapter für die C-ABI
- [ ] GL-/Metal-Viewport
- [ ] Dateidokumente und Background-Task-Verhalten
- [ ] Featureparität gegen dieselbe Matrix und dieselben 3MF-Fixtures

## Release

Vor öffentlicher Distribution bleiben unabhängig vom technischen Stand
offen:

- Root-`LICENSE` und `THIRD-PARTY-NOTICES`;
- AGPL-Quellcodeangebot und reproduzierbare Buildanleitung;
- Datenschutzinformation für lokale Druckerzugänge und Netzwerk;
- Apple-Store-/AGPL-Entscheidung;
- Produktname ohne irreführenden Prusa-Bezug.
