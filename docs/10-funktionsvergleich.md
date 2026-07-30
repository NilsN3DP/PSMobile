# Funktionsvergleich PrusaSlicer 2.9.6 ↔ PSMobile

Stand: 2026-07-30. Android ist der einzige aktuelle
Implementierungsfokus. iOS wird erst portiert, wenn der Android-Ablauf
stabil und auf Geräten geprüft ist.

Die maschinenlesbare Wahrheitsquelle ist
`docs/feature-matrix.json`. `build/scripts/feature-report.py --check`
prüft Status und Evidenzpfade. Dabei bedeutet `coded` ausdrücklich
nicht, dass die Funktion bereits in einem aktuellen APK oder auf einem
Gerät lief.

## Aktuell umgesetzt

- FFF-Slicing über PrusaSlicer/libslic3r und eine plattformneutrale C-ABI.
- Android-Viewport mit Bett, Modell, Auswahl, Kamera und
  G-Code-Vorschau samt Layerbereich; diese älteren Pfade liefen bereits
  auf einem Android-Gerät.
- Verschieben per Touch sowie Position, Drehung und Skalierung über
  Zahlenfelder. Android zeigt Grad, die C-ABI bleibt bei Radiant.
- Auf Bett legen, auf Bettgröße einpassen, spiegeln, duplizieren,
  Instanzzahl und Auto-Arrange.
- Druck-, Filament- und Druckereinstellungen mit 317 von 370
  FFF-relevanten `PrintConfig`-Optionen.
- Schnelleinstellungen für Schichthöhe, Fülldichte, Supports und Brim.
- Filament und Farbe je Extruder.
- Aufklappbarer Objekt-/Volumenbaum mit Volumentyp, Dreieckszahl und
  Extruderzuweisung je Objekt oder druckbarem Teil.
- Begrenztes Undo/Redo für Objekt-, Volumen- und Bettaktionen; eine
  komplette Touch-Geste oder kombinierte Zahlenaktion ist ein Schritt.
- Export/Teilen von G-Code und PrusaLink-Code für API-Key oder
  HTTP-Digest.
- Slicing als started Foreground-Service im App-Prozess.

Neu gebaut und im C-ABI-Vertragstest auf einem x86_64-Emulator geprüft:

- Slice-Snapshot und Designrevision: Änderungen können keinen alten
  G-Code mehr als aktuell erscheinen lassen.
- Abhängige Einstellungsfelder nutzen extrahierte
  PrusaSlicer-Toggle-Logik.
- Druckerzugänge liegen AES-GCM-verschlüsselt im Android Keystore und
  sind von Auto Backup ausgeschlossen.
- Bei mehreren PrusaLink-Druckern muss das Uploadziel gewählt werden.

Zusätzlich im Compose-UI-Smoke-Test auf demselben Emulator geprüft:

- 3MF fragt immer zwischen **Nur 3D-Objekte** und **Als Projekt**.
- Projektimport übernimmt Positionen, eingebettete FFF-Konfiguration
  und wählt das passende installierte Druckerprofil; bei Abweichungen
  bleibt die eingebettete Konfiguration als projektlokales Profil
  aktiv.
- Eingebettete Post-Processing-Skripte werden beim mobilen
  Projektimport entfernt.
- Mehrbettprojekte werden in einzelne mobile Betten zerlegt. Der Nutzer
  wählt `Bett 1`, `Bett 2` usw. direkt; es gibt keine große
  scrollbare Desktop-Bettlandschaft.
- Objekte können gezielt auf ein anderes Bett verschoben werden.
- Projekte können neu angelegt, als 3MF gespeichert und über
  **Speichern unter** an Android-Dokumentanbieter ausgegeben werden.
- Eine gespeicherte Zwei-Bett-3MF wurde im Emulator geschlossen und
  wieder geöffnet; Objektzahlen und direkte Bettzuordnung blieben
  erhalten.
- Undo und Redo wurden in der Compose-Werkzeugleiste mit einer
  Duplizieraktion vorwärts und rückwärts geprüft.
- In einem MMU3-Profil wurde ein Modellvolumen über den Baum sichtbar
  auf `Extruder 3` gesetzt.

## Parameter

`build/scripts/gap-report.py` misst direkt gegen
`libslic3r/PrintConfig.cpp`:

| Kategorie | Anzahl |
| --- | ---: |
| FFF-relevant | 370 |
| in PSMobile erreichbar | 317 |
| fehlend | 53 |
| davon Messartefakte/interne/veraltete/unsichtbare Werte | 31 |
| echte Spezialdialog-Lücken | 18 |
| gehört an den Objektbaum | 4 |

Die 18 Dialogwerte betreffen Bettform/-textur/-modell,
Wischvolumenmatrix, Ramming, G-Code-Ersetzungen,
Profilkompatibilitäten sowie die vollständige Physical-Printer-
Konfiguration. PSMobiles eigene PrusaLink-Verwaltung ersetzt davon nur
den lokalen Standardfall; OctoPrint, Repetier, FlashAir, AstroBox, MKS,
Zertifikatsdateien und Prusa Connect sind nicht abgedeckt.

Die vier Objektwerte sind `extruder`, `extruder_colour`,
`wipe_into_infill` und `wipe_into_objects`. `extruder` ist jetzt über
den Objekt-/Volumenbaum erreichbar. Eigene Objektfarbe sowie
„In Infill/Objekte wischen“ fehlen noch; deshalb ist die vollständige
Multicolor-Parität trotz funktionierender Teilzuweisung noch nicht
erreicht.

## Modellwerkzeuge

Der Desktop deklariert 13 relevante Gizmo-Typen. PSMobile hat
numerisches Skalieren und Drehen sowie zusätzlich mobiles
Touch-Verschieben. Noch nicht umgesetzt sind:

- an einer gewählten Fläche flach legen;
- schneiden und vereinfachen;
- Supports, Naht, Fuzzy Skin und MMU-Farbe bemalen;
- messen;
- Text und SVG prägen;
- der im Desktop-Enum geführte Hollow-Pfad.

„Aufs Bett legen“ ist nicht dasselbe wie „Flach legen“: Ersteres senkt
das Objekt nur auf Z=0, letzteres richtet eine ausgewählte Fläche aus.

## Projekt- und Dateiabläufe

| Ablauf | Stand |
| --- | --- |
| STL/OBJ/AMF/3MF als Geometrie importieren | vorhanden |
| 3MF als Projekt mit Konfiguration öffnen | Core und Compose-UI im Emulator geprüft |
| Drucker aus 3MF automatisch auswählen | projektlokales Profil im UI geprüft; exaktes installiertes Profil offen |
| Mehrere Betten direkt auswählen | Core und Bettchips im Emulator geprüft |
| Neues Projekt | Compose-UI im Emulator geprüft, mit Verlustwarnung |
| Projekt als 3MF speichern / Speichern unter | Core-Roundtrip und SAF-UI im Emulator geprüft |
| STEP | absichtlich im mobilen Build deaktiviert |
| ZIP-Archivimport | fehlt |
| Platte als STL/OBJ exportieren | fehlt |
| G-Code binär/ASCII konvertieren | fehlt |
| beschädigte STL reparieren | fehlt |

Der mobile Roundtrip ist umgesetzt: lokale Betten werden beim Speichern
in PrusaSlicers virtuelle Bettlandschaft zurückübersetzt, Zugangsdaten
und Post-Processing werden entfernt. Offen bleibt der zusätzliche
manuelle Vergleich komplexer Projekte mit Desktop-PrusaSlicer,
insbesondere Custom-G-Code und Wipe-Tower.

## Bedienung und Zustandsverwaltung

Jetzt vorhanden sind Undo/Redo, eine projektinterne
Objekt-Zwischenablage über mehrere Druckbetten sowie der
Objekt-/Volumenbaum mit Extruder je druckbarem Teil. „+ Kopie“ und
„− Kopie“ ändern Instanzen getrennt von Kopieren/Einfügen. Modifier,
Negativvolumen und Support-Blocker werden mit ihrem Typ angezeigt; ihre
Erzeugungs- und Bearbeitungsdialoge fehlen noch.

Weiterhin fehlend:

- Mehrfachauswahl;
- Suchen und „Alles auswählen/Auswahl aufheben“;
- vollständige Tastatur- und Stiftbedienung.

Profiländerungen werden mit Anzahl markiert. Beim Wechsel können die
Werte übertragen, verworfen oder unter eigenem Profilnamen gespeichert
werden.

Desktop-Menüs wie neue Instanz, Konfigurationsordner öffnen,
SD-Karte auswerfen oder Desktop-Fensterverwaltung sind keine
Mobile-Paritätsziele.

## Slicing, Speicher und Hintergrund

Der neue Core slict aus einer Modell-/Konfigurationskopie und verwirft
das Ergebnis, wenn sich das aktive Projekt währenddessen ändert. Der
Foreground-Service ist korrekt started und zeigt sofort eine
Benachrichtigung; er bleibt zunächst bewusst im UI-Prozess.

Im x86_64-Emulator bestanden ein 41-MB-3MF, 31 Sekunden Slicing bei
ausgeschaltetem Display, UI-Abbruch mit direktem Neustart, Vorschau,
Teilen und 20 aufeinanderfolgende Slice-Zyklen. Ein dabei
reproduzierter Nullzugriff nach erkanntem G-Code-Pfadkonflikt ist im
Core behoben und durch einen Vertragstest abgedeckt.

Noch offen sind:

- Änderung-während-Slice als vollständiger UI-Ablauf, Geräte-Rotation
  während des Slicens und der bediente Notification-Cancel-Test;
- gemessene Speichergrenzen und Low-Memory-Verhalten auf realen
  4–6-GB- und ≥8-GB-ARM-Geräten;
- gestaffeltes Laden bzw. Dezimierung großer Vorschauen;
- separater Slicer-Prozess samt serialisierbarem Auftrag und Recovery.

## Netzwerk und Auslieferung

PrusaLink ist implementiert, aber nicht gegen reale Druckerhardware
verifiziert. Prusa Connect braucht eine eigene OAuth-Client-ID.
Cleartext-HTTP ist nur nach ausdrücklicher Freigabe je Drucker erlaubt.

Vor einer öffentlichen Auslieferung fehlen außerdem Lizenzdatei,
Third-Party-Notices, Quellcodeangebot, Datenschutztext und die
Store-/AGPL-Entscheidung.

## Empfohlene Reihenfolge

1. Das Android-Gerätegate auf beiden arm64-Speicherklassen ausführen;
   Rotation, Notification-Abbruch und Low-Memory-Verhalten nachholen.
2. Die vorhandene Projektlokal-/Post-Processing-/Mehrbett-Fixture um
   ein exakt installiertes Druckerprofil ergänzen.
3. Custom-G-Code-/Wipe-Tower-Roundtrip gegen Desktop-PrusaSlicer
   ergänzen.
4. Mehrfachauswahl und Auswahlbefehle bauen; Clipboard und sichtbarer
   Dirty-Profilzustand sind vorhanden.
5. Flach legen, schneiden und vereinfachen.
6. Spezialdialoge für Bett, Wischmatrix und G-Code-Ersetzungen.
7. Malwerkzeuge, Messen und Prägen.
8. Erst danach den stabilen Funktionsumfang auf iOS portieren.
