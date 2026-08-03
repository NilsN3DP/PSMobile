# Funktionsvergleich PrusaSlicer 2.9.6 ↔ PSMobile

Stand: 2026-08-03. Android bleibt der Referenzpfad fuer reale
Geraete- und Druckerabnahmen. iOS ist jedoch kein Platzhalter mehr:
eine native SwiftUI-App mit C-ABI-Kern, Simple Mode, 3MF-Projekten,
Mehrbett, Vorschau und Einstellungsseiten wird auf dem Mac aktiv gebaut
und per Simulator getestet. Der belastbare Integrationsnachweis steht in
`release/ios-integration-inventory-2026-08-03.md`.

Der Abgleich einer von aussen zusammengetragenen Restliste gegen den
tatsaechlichen Quellstand steht in `docs/11-abgleich-2026-08-02.md`.

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
- Flach legen, schneiden, vereinfachen, Objekte/Volumen teilen,
  Modifier hinzufügen, variable Schichthöhen sowie Text und SVG prägen.
- Supports, Naht, Fuzzy Skin und MMU-Farbe pro Fläche bemalen; messen.
- Druck-, Filament- und Druckereinstellungen mit 317 Optionen in den
  Tabs sowie 13 weiteren Werten über Spezialdialoge und Objektwerkzeuge.
- Schnelleinstellungen für Schichthöhe, Fülldichte, Supports und Brim.
- Filament und Farbe je Extruder.
- Aufklappbarer Objekt-/Volumenbaum mit Volumentyp, Dreieckszahl und
  Extruderzuweisung je Objekt oder druckbarem Teil.
- Begrenztes Undo/Redo für Objekt-, Volumen- und Bettaktionen; eine
  komplette Touch-Geste oder kombinierte Zahlenaktion ist ein Schritt.
- Export/Teilen von G-Code und PrusaLink-Code für API-Key oder
  HTTP-Digest.
- Druckbett als STL oder OBJ exportieren, STL reparieren und G-Code
  zwischen ASCII und Binary umwandeln.
- Mehrfachauswahl mit Alles auswählen, Auswahl aufheben und Auswahl
  löschen; die Objekt-Zwischenablage bleibt bettübergreifend.
- Easy Mode für den geführten Druckablauf, Advanced-Assistent für die
  drei Profilarten und sperrbare Druckbetten.
- Slicing als started Foreground-Service im App-Prozess.

## iOS-Stand

- Native SwiftUI-App mit dem gleichen C-ABI-FFF-Kern, iPad- und
  iPhone-Unterstuetzung, Startmodus und Ersteinrichtung.
- Simple Mode, 3MF-Projektablauf, Mehrbett, Undo/Redo, Objektleiste,
  Slice-Zusammenfassung und echte G-Code-Vorschau sind im Simulator
  getestet.
- Advanced-Arbeitsflaeche und die dynamischen Print-, Filament- und
  Printer-Settings sind vorhanden, erreichen aber noch nicht die
  vollstaendige Android-Advanced-Bedienung.
- Offen bleiben iOS-INXD/MMU-ColorMix, PrusaLink samt sicherem
  Profilupdate, der vollständige Advanced-/Sprach-/Rotation-Audit und
  die iPad-Abnahme mit realem Drucker. Diese Punkte stehen einzeln in
  `feature-matrix.json`; sie duerfen nicht mehr hinter einem pauschalen
  "iOS spaeter" verschwinden.

### EasyPrint-Responsive-Smoke (2026-07-31)

Die EasyPrint-Oberfläche wurde auf einem x86_64-Emulator in Compact
Portrait, Medium und Expanded bedient. Der Compact-Einstieg zeigt einen
dunklen Root, Modellimport-Einstieg, Profil-Suchen und den festen
Preview/Print-Dock; Medium verwendet für die Toolbar-Auswahl ein
Modalsheet, Expanded einen persistenten rechten Kontextbereich. Der
vollständige Befund einschließlich des offenen Rotationsfehlers beim
Suchtext steht in
`.superpowers/sdd/2026-07-31-easyprint-responsive-shell/task-5-report.md`.

Bewusste Unterschiede zur Web-Referenz bleiben: kein übernommenes
Prusa-Branding, keine Cloud-Spuleninventur und kein separater
Easy-3D-Renderer.

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
| in `tabs.json` erreichbar | 317 |
| über Spezialdialog erreichbar | 9 |
| am Objekt erreichbar | 4 |
| insgesamt in PSMobile erreichbar | 330 |
| nicht in `tabs.json` | 53 |
| davon Messartefakte/interne/veraltete/unsichtbare Werte | 31 |
| echte Dialog-Lücken | 9 |
| gehört an den Objektbaum | 4 |

Neun bisher als Lücke gezählte Werte sind über mobile Spezialdialoge
erreichbar: Bettform/-textur/-modell, Wischvolumenmatrix, Ramming,
G-Code-Ersetzungen und Profilkompatibilitäten. Offen bleiben neun Werte
der vollständigen Desktop-Physical-Printer-Konfiguration. PSMobiles
eigene PrusaLink-Verwaltung deckt den lokalen Standardfall ab, nicht
aber OctoPrint, Repetier, FlashAir, AstroBox, MKS,
Zertifikatsdateien oder Prusa Connect.

Die vier Objektwerte sind `extruder`, `extruder_colour`,
`wipe_into_infill` und `wipe_into_objects`. Der Extruder ist über den
Objekt-/Volumenbaum erreichbar; Objektfarbe und „In Infill/Objekte
wischen“ liegen im mobilen Modellwerkzeug. Damit zählen sie nicht mehr
als Einstellungs-Lücke.

## Modellwerkzeuge

Der Desktop deklariert 14 relevante Gizmo-Typen. PSMobile deckt davon
13 ab: Verschieben, Skalieren, Drehen, Flachlegen, Schneiden, Supports,
Naht, Fuzzy Skin, MMU-Farbe, Messen, Text-/SVG-Prägen und Vereinfachen.

Der vierzehnte, Aushöhlen, ist **keine Lücke, sondern liegt ausserhalb
des Zuschnitts**. `GLGizmoHollow::on_is_activable()` prüft als Erstes
`printer_technology() != ptSLA` und lehnt bei einem FDM-Drucker ab — das
Werkzeug ist am Desktop dort gar nicht aktivierbar. Fachlich ist das
schlüssig: Harz braucht Hohlräume samt Ablauflöchern gegen Saugwirkung
und Materialverbrauch, beim FDM übernimmt das die Füllung, die man auf
0 % stellt. Aushöhlen fällt damit unter dieselbe Entscheidung, die SLA
insgesamt ausschliesst.

Damit ist die Gizmo-Abdeckung für FDM **vollständig**.

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
| ZIP-Archivimport | bewusst ausgeschlossen |
| Platte als STL/OBJ exportieren | vorhanden (gebaut) |
| G-Code binär/ASCII konvertieren | vorhanden (gebaut) |
| beschädigte STL reparieren | vorhanden (gebaut) |

Der mobile Roundtrip ist umgesetzt: lokale Betten werden beim Speichern
in PrusaSlicers virtuelle Bettlandschaft zurückübersetzt, Zugangsdaten
und Post-Processing werden entfernt. Offen bleibt der zusätzliche
manuelle Vergleich komplexer Projekte mit Desktop-PrusaSlicer,
insbesondere Custom-G-Code und Wipe-Tower.

### Bewusste Ausschlüsse

ZIP-, STEP- und SLA-Import gehören nicht zum mobilen Umfang. Ebenso
sind Desktop-Fensterverwaltung (neue Instanz, Beenden, Desktop öffnen)
und eine hardwareabhängige Print-Host-Warteschlange keine
Mobile-Paritätsziele. Der Gap-Report kennzeichnet diese Befehle
ausdrücklich als `AUSGENOMMEN`, statt sie als Implementierungslücken zu
zählen.

## Bedienung und Zustandsverwaltung

Jetzt vorhanden sind Undo/Redo, eine projektinterne
Objekt-Zwischenablage über mehrere Druckbetten, Mehrfachauswahl sowie
der Objekt-/Volumenbaum mit Extruder je druckbarem Teil. „+ Kopie“ und
„− Kopie“ ändern Instanzen getrennt von Kopieren/Einfügen. Modifier,
Negativvolumen und Support-Blocker werden mit ihrem Typ angezeigt und
können als primitive Volumen hinzugefügt werden.

Weiterhin offen sind vollständige Tastatur- und Stiftbedienung sowie
die Geräteprüfung der neu gebauten Touch-Abläufe.

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
4. Die neu gebauten Modellwerkzeuge, Spezialdialoge, Easy Mode,
   Assistenten und Bett-Sperren auf realen Geräten prüfen.
5. Für die verbleibende Desktop-Lücke den Hollow-Pfad bewerten.
6. Physical-Printer-Dialoge nur dann erweitern, wenn ein unterstütztes
   Ziel über PrusaLink hinaus erforderlich wird.
7. iOS-INDX/ColorMix, PrusaLink/Profile-Updates und Advanced-UI gegen
   denselben Kern vervollstaendigen und danach mit einem iPad sowie einem
   echten PrusaLink-Drucker abnehmen.
