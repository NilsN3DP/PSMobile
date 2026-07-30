# Stopp-Punkte

Stand: 2026-07-30. Diese Datei enthält nur Punkte, die eine Entscheidung,
externe Voraussetzung oder einen echten Test brauchen. Der
Funktionsstatus steht in `docs/feature-matrix.json`, die Desktop-Lücken
in `docs/10-funktionsvergleich.md`.

## Externe Blocker

### Android-UI-Gerätegate

Beide ABIs sind mit NDK r27c gebaut, fingerprint-geprüft und im
Debug-APK enthalten. Der C-ABI-Vertragstest lief auf dem
x86_64-Emulator einschließlich Slicing, Stale-Schutz, Undo/Redo,
Teil-Extruder und Mehrbett-Speichern/Wiederöffnen.

Der Compose-Smoke-Test lief anschließend mit einer seitengleich
installierten Testvariante, ohne die anders signierte vorhandene App
oder deren Daten zu löschen. App-Start, natives Laden, beide
3MF-Entscheidungswege, projektlokale Druckerprofilwahl, zwei direkt
sichtbare Bettchips, Projekt-Speichern/Wiederöffnen, Undo/Redo sowie
Objekt-/Volumenbaum mit MMU3-Teilzuweisung sind bestanden.

Ein normaler Slice aus der Compose-UI bis zum fertigen G-Code ist
ebenfalls bestanden. Währenddessen meldete Android den Service als
Foreground-Service mit einer Notification-Aktion. Offen bleiben auf
dem Emulator Gradrotation, Stale-Meldung, Vorschau/Teilen, Screen-off
und der tatsächlich über die Notification ausgelöste Abbruch. Die
komplette Matrix muss außerdem auf einem kleinen und einem großen
arm64-Gerät laufen.

### PrusaLink-Hardware

API-Key, HTTP-Digest, HTTP-Freigabe, Zielauswahl und Remote-Dateiname
sind implementiert. Keiner dieser neuen Wege ist bereits gegen echte
Druckerhardware verifiziert.

### Prusa Connect

Prusa Connect benötigt eine eigene OAuth2-/PKCE-Client-ID. Die
PrusaSlicer-ID mit dem Redirect `prusaslicer://login` darf nicht
übernommen werden. Das bleibt eine Anfrage an Prusa.

### Release und Name

„PSMobile“ ist ein Arbeitstitel. Vor Veröffentlichung fehlen außerdem
`LICENSE`, `THIRD-PARTY-NOTICES`, Quellcodeangebot,
Datenschutzinformation und die Store-/AGPL-Entscheidung.

## Entschiedene Projektregeln

### Projekt speichern

Die Projektleiste speichert immer ein vollständiges 3MF-Projekt mit
allen belegten mobilen Betten. Projektlokale Konfigurationen werden
eingebettet. Druckerzugänge und Post-Processing bleiben dauerhaft aus
der exportierten Datei entfernt.

### Multicolor

Filamente und Farben je Extruder sowie die Extruderzuweisung je Objekt
und druckbarem Volumen sind vorhanden. Vor den Malwerkzeugen fehlen noch
Objektfarbe, Wipe-Flags und Erzeugungsdialoge für Modifier.

### Spezialwerkzeuge

Empfohlene Reihenfolge nach dem Gerätegate:

1. Clipboard-Einfügen, Mehrfachauswahl und Dirty-Profilzustand.
2. Flach legen, schneiden, vereinfachen.
3. Bettform, Wischmatrix und G-Code-Ersetzungen.
4. Support-/Naht-/Fuzzy-/MMU-Painting.
5. Messen, Text und SVG.

## Bewusst später

- separater Android-Slicer-Prozess: erst mit serialisierbarem Auftrag;
- iOS: erst nach bestandenem Android-Gerätegate;
- Prusa Connect: erst mit eigener Client-ID;
- SLA und vollständige Desktop-Menüparität: nicht Teil von Android v1.
