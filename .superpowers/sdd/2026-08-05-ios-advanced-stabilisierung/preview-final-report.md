# Final-G-Code-Vorschau – Bericht

Stand: 2026-08-05

## Ergebnis

Die iOS-Vorschau hängt jetzt ausschließlich am unveränderten finalen
`Slic3r::GCodeProcessorResult`, den `Print::export_gcode()` erzeugt.
Der Core hält dieses Resultat revisionsgebunden als `shared_ptr`; der
Viewport übernimmt denselben unveränderlichen Datensatz und ruft den
offiziellen `libvgcode::convert(GCodeProcessorResult, ...)` auf.
Es gibt weder einen eigenen G-Code-Parser noch einen Rückfall auf
`convert(Print)`.

Die additive C-ABI v6 liefert:

- finale Move-, Layer-, Extruder- und Rollenanzahl,
- monotone Z-Grenzen und Zeit/Filament je finalem Layer,
- tatsächlich verwendete Extruder samt finaler Werkzeugfarbe,
- tatsächlich vorkommende Extrusionsrollen samt libvgcode-Farbe,
- finale Gesamtzeit sowie Filamentlänge und -gewicht.

Jede Design- oder Konfigurationsänderung verwirft Resultat und Snapshot
atomar. Ein veralteter Snapshot wird nicht mehr an Swift oder libvgcode
ausgegeben.

Auf iOS gibt es eine gemeinsame Vorschaukarte:

- iPhone: Bottom Sheet,
- iPad: kompakte Seitenkarte,
- beidseitiger Layerbereich,
- live neu summierte Zeit-, Meter-, Gramm- und Z-Werte,
- berührbare Feature- und Extruder-Chips mit echten Farben,
- sauberer Rückweg in den Editor.

Editor-Layer- und Paint-Overlays werden in der finalen Vorschau nicht
eingeblendet.

## TDD und Verifikation

RED:

- Der erweiterte Core-Vertrag ließ sich vor der Implementierung wegen
  der fehlenden Preview-Snapshot-Symbole erwartungsgemäß nicht
  übersetzen.
- Die dependency-freien Swift-Tests legten Range-Clamping und die
  Summierung ausschließlich ausgewählter finaler Layer fest.

GREEN auf dem separaten Mac-Arbeitsbaum
`~/psmobile-preview-final`:

- Simulator-Core: vollständiger Build `309/309`, inklusive
  `psm_contract_tests`, `psm_testcli` und
  `libpsmobile_core_all.a`.
- Inkrementeller Core-Rebuild nach der Z-Band-Korrektur: `4/4`.
- Core-Vertrag im iPhone-17-Pro-Simulator: alle neuen
  Final-Preview-Verträge bestanden; der Lauf erreichte anschließend
  erst den alten, unabhängigen 3MF-Projektimport.
- `xcodebuild`, Ziel iPhone 17 Pro / iOS 26.2:
  App und Tests übersetzt; komplette `PSMobileTests` erfolgreich,
  einschließlich beider `PreviewRangeTests`.
- UI-Smoke iPhone: erfolgreich in 21,0 s. Geprüft wurden echter
  libvgcode-Inhalt, sichtbare Änderung beim Verschieben der oberen
  Layergrenze, Live-Statistik, Rollenfilter, Extruderfilter und
  Rückkehr in den Editor.
- UI-Formfaktor iPhone: Bottom Sheet und beide Slider erfolgreich
  in 8,1 s.
- UI-Formfaktor iPad Pro 13": kompakte Seitenkarte und beide Slider
  erfolgreich in 10,9 s.
- `git diff --check`: keine Whitespace-Fehler.

Der vollständige alte `psm_contract_tests`-Lauf benötigt zusätzlich
`psmobile-multibed.3mf` und
`psmobile-installed-core-one.3mf`. Diese externen, nicht versionierten
Fixtures waren im sauberen Mac-Arbeitsbaum nicht vorhanden. Der Lauf
wurde daher mit absichtlich fehlendem Projektpfad ausgeführt und
bestand nach dem Slicing sämtliche neuen Preview-Verträge, bevor er
am erwarteten alten Projektimport mit „Datei nicht gefunden“ endete.

## Wichtige Dateien

- `core/include/psmobile_core.h`
- `core/src/psmobile_core.cpp`
- `core/src/psmobile_session.hpp`
- `viewport/src/psm_viewport.cpp`
- `ios/PSMobile/Core/PsmCore.swift`
- `ios/PSMobile/Viewport/ViewportView.swift`
- `ios/PSMobile/Screens/FinalPreviewPanel.swift`
- `ios/PSMobile/UI/PreviewRange.swift`
- `core/test/psm_contract_tests.cpp`
- `ios/PSMobileTests/PreviewRangeTests.swift`
- `ios/PSMobileUITests/PreviewUITests.swift`
