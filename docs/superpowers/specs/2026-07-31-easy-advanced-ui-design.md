# Easy Mode und Advanced-Usability – Design-Spezifikation

**Stand:** 2026-07-31  
**Plattformreihenfolge:** Android zuerst, iOS erst nach einem stabilen Android-Gate  
**Bewusster Ausschluss:** ZIP-Import bleibt außerhalb dieses Vorhabens.

## Ziel

PSMobile erhält einen leicht verständlichen Easy Mode nach dem visuellen und
bedienlogischen Vorbild von Prusa EasyPrint. Der bestehende Advanced Mode
behält seine Struktur und Funktionen, wird aber touch-, tablet- und
handyfreundlicher. Beide Modi greifen auf denselben nativen Slicer-Kern,
Profile und Projekte zu.

## Start und Modi

Die Startseite bietet drei direkte Einstiege:

1. **Easy Mode** – reduzierte Druckvorbereitung.
2. **Advanced Mode** – direkte bestehende Slicer-Arbeitsfläche.
3. **Advanced-Assistent** – geführtes Nachladen und Verwalten von Printer-,
   Filament- und Print-Settings-Profilen.

Der Assistent ist zusätzlich jederzeit aus der Advanced-Übersicht,
Printer-, Filament- und Print-Settings-Seiten sowie aus kompatibilitäts- oder
fehlprofilbezogenen Warnungen erreichbar.

## Easy Mode

Der Easy Mode übernimmt die gezeigte EasyPrint-Struktur, ohne Prusa-Branding
oder Cloud-Abhängigkeit zu kopieren:

- kompakte Kopfzeile mit Projects, Printer, Material, Supports, Print
  Settings, Preview und Print;
- Printer-Auswahl als Modellkarten aus den eingerichteten Druckern;
- Düse nicht in der Druckerliste, sondern in der Slice-Übersicht;
- PrusaLink darf die Düse vorausfüllen, eine manuelle Auswahl bleibt möglich;
- Material-/Filamentauswahl mit Suche, Materialchips, Farbe, zuletzt benutzt
  und lokalen Spulen;
- bebilderte, kuratierte Auswahl für Supports und Adhesion;
- begrenzte Print-Settings-Auswahl statt der vollständigen Parameterliste;
- große Vorschau, Arrange, grundlegende Transformationsaktionen und ein
  primärer **Print**-Button;
- Handy: Vollbildseiten/Bottom-Sheets; Tablet: breite Karten- oder
  Seitentafeln.

## Advanced Mode

Die vorhandene Advanced-Struktur bleibt erhalten. Die Usability-Schicht
ergänzt:

- dauerhaft sichtbare Hauptpunkte **Printer**, **Filament** und
  **Print Settings**;
- Profil- und Druckersuche mit Kompatibilitätsfiltern;
- größere Touch-Ziele, klare Zurück-/Schließen-Aktionen und responsive
  Overlay-/Seitpanel-Layouts;
- sichtbaren Kontext für Modell, Düse, Filament, Print Settings und aktive
  Platte;
- direkt erreichbare Aktionen für Alles auswählen, Auswahl aufheben,
  Löschen, Gruppieren und Objektverwaltung;
- den Advanced-Assistenten an allen im Start-/Warnungsfluss definierten
  Einstiegspunkten.

## Drucker-, Düse- und Profilmodell

Printer werden überall als Modell geführt, nicht als Duplikate pro Düse. Die
Düsenauswahl erfolgt in der Slice-Übersicht. Daraus wird die passende
technische Printer-Profilvariante intern abgeleitet. Ein von PrusaLink
gemeldeter Düsendurchmesser wird als Vorschlag verwendet und darf geändert
werden. Der Advanced-Assistent kann fehlende Printer-, Filament- und
Print-Settings-Profile nachladen/importieren und speichert die Auswahl lokal.

## Wizard-Zustand

Der Wizard ist ein wiederaufnehmbarer, projektbezogener Ablauf. Ein Nutzer
kann ihn verlassen und später an derselben Stelle fortsetzen. Jeder Schritt
kann einzeln erneut geöffnet werden, insbesondere Filament, Düse und Print
Settings. Fehlende oder inkompatible Profile verlinken direkt zum passenden
Wizard-Schritt.

## Plattensperre

Jede mobile Platte besitzt einen persistenten `locked`-Status.

- Schloss-Symbol und Status erscheinen im Bett-/Platten-Selector und in der
  Arbeitsfläche.
- Eine gesperrte Platte bleibt auswähl-, betracht- und exportierbar.
- Hinzufügen, Löschen, Leeren, Verschieben, Transformieren, Arrange und
  Objektbearbeitung sind gesperrt.
- Ein Bearbeitungsversuch zeigt eine kurze, konkrete Entsperr-Anweisung.
- Entsperren erfolgt über das Schloss und ist undo-fähig.
- Der Status wird in der PSMobile-Projektmetadatenstruktur gespeichert und
  beim 3MF-Roundtrip wiederhergestellt. Desktop-PrusaSlicer ignoriert die
  Zusatzmetadaten.

## Desktop-Lücken als priorisierte Erweiterungen

### Priorität A – grundlegender Advanced-Workflow

- vorhandenen G-Code öffnen und nur in der Vorschau anzeigen;
- Gruppieren/Entgruppieren sowie Objekt aus-/einblenden und sperren;
- sichtbare Auswahlbefehle (alles, nichts, Auswahl löschen);
- Printer-, Filament- und Print-Settings-Import sowie Profilvergleich;
- Arrange mit Abstand, Rotationsregeln und verständlicher Kollisionsmeldung;
- sequentielles Drucken;
- Layer-Editing und Farbwechsel in der Vorschau;
- physische Printer-Profile, Upload-Warteschlange und erneutes Senden;
- Plattensperre wie oben beschrieben.

### Priorität B – wichtige Desktop-Komfortfunktionen

- Config-/Config-Bundle-Export;
- Plattenexport einschließlich optionaler Supports;
- Toolpath-OBJ-Export;
- SD-/USB-Ausgabe, sofern Android-Zugriff und Zielgerät vorhanden sind;
- klarere Warnungen für Überhang, Haftung, Kollisionen und fehlende Profile;
- Suche und Filter in allen Profil- und Projektlisten.

### Bewusst nicht Teil dieses Vorhabens

ZIP-Archivimport, STEP, SLA-Slicing, Desktop-Fensterverwaltung,
Tastaturkürzel und sonstige desktopgebundene Menü-/Systemfunktionen.

## Datenfluss und Fehlerverhalten

Easy Mode und Advanced Mode verwenden dieselbe Session, Projektstruktur,
Profilauflösung und Slice-Revision. UI-Reduktion darf keine zweite
Profilquelle erzeugen. Profilwechsel, Düsenwechsel oder ein Entsperren
erhöhen die Design-Revision und machen alten G-Code ungültig. Bei fehlendem
Profil, inkompatibler Düse, nicht erreichbarem PrusaLink oder zu hohem
Speicherbedarf wird die nächste konkrete Aktion angezeigt; kein stiller
Fallback und kein veralteter G-Code bleiben aktiv.

## Verifikation

- Compose-Unit-Tests für Moduswahl, Wizard-Fortsetzung, Profilfilter und
  Plattensperr-Reducer;
- Core-Vertragstest für persistente Plattensperren, Profil-/Düsenauflösung
  und stale Slice;
- UI-Smoke-Test auf Handy- und Tablet-Größe für beide Modi;
- Emulator-Test für Suche, Wizard-Einstiege, Moduswechsel, gespeicherte
  Plattensperre und Wiederöffnung;
- ARM64-Gerätetest für Profil-/PrusaLink-Auflösung, Slice, Preview, Export,
  Notification-Abbruch und Speichergrenzen;
- aktualisierte Desktop-Paritätsmatrix mit getrennten Statuswerten für
  coded, built, emulator_tested und device_tested.

