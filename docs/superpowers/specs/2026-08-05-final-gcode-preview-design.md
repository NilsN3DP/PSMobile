# Final-GCode-Vorschau auf iOS

## Ziel

Die iOS-Vorschau zeigt ausschließlich Daten aus PrusaSlicers endgültiger
G-Code-Verarbeitung. Der beim Export erzeugte `GCodeProcessorResult` bleibt
session-lokal erhalten und ist die gemeinsame Quelle für Renderer, Filter,
Layergrenzen und Kennzahlen.

## Architektur

Der Slice-Worker erzeugt weiterhin mit `Print::export_gcode` den finalen
`GCodeProcessorResult`. Bei unveränderter `design_revision` veröffentlicht er
ihn zusammen mit G-Code-Pfad und Statistiken unter `result_mtx`. Bei einer
neuen Revision ist der Snapshot nicht mehr abrufbar. Ein neuer Slice räumt den
alten Snapshot vor dem Start weg.

Das additive C-ABI liefert einen versionierten `psm_preview_snapshot` sowie
indexierte Layer-, Extruder- und Feature-Rollen-Datensätze. Zeit, Filament,
Layer-Z-Unter-/Obergrenzen und die vorhandenen Rollen/Extruder stammen aus den
finalen Moves und Prusa-Statistiken. Es gibt keinen eigenen G-Code-Parser.

Der Viewport übernimmt denselben gespeicherten `GCodeProcessorResult` in
PrusaSlicers `libvgcode::convert(result, tool_colors, color_print_colors,
viewer)`. Er bietet Feature-/Extruderansicht sowie die Sichtbarkeit tatsächlich
vorhandener Rollen und Extruder an. Der Layerbereich ist beidseitig begrenzt.

## iOS-Oberfläche

Swift modelliert den Snapshot ohne Ersatzwerte. Kann der Snapshot nicht
abgerufen werden, schließt die Vorschau. Eine gemeinsame Preview-Karte zeigt
Zeit, Filament und Z-Spanne, echte Rollen- und Extruderfilter sowie einen
beidseitigen Touch-Layerregler. Auf dem iPad liegt sie als kompakte Seitenkarte
über dem Viewport, auf dem iPhone als Bottom-Sheet. Ein klar beschrifteter
Editor-Knopf beendet die Vorschau; Editor-Layer- und Paint-Overlay bleiben in
der Vorschau verborgen.

## Fehler- und Aktualitätsvertrag

- Vor einem fertigen Slice ist kein Snapshot vorhanden.
- Nach jeder Designrevision ist der Snapshot stale und wird weder angezeigt
  noch geladen.
- Ein stale gewordenes Ergebnis wird auch bei Slice-Abschluss nicht
  veröffentlicht.
- Ein Ladefehler führt zurück in den Editor und zeigt keine erfundenen Werte.

## Tests

Der Core-Vertrag beweist nach einem realen Slice finale Moves, Rollen,
Extruder, Zeit/Filament und monotone Layer-Z-Grenzen. Vor dem Slice und nach
einer Revision ist der Snapshot nicht verfügbar. Viewport-Vertragstests prüfen
Range-Clamping und Rollen-/Extruderfilter ohne GL-Parserersatz. Swift-Tests
prüfen den beidseitigen Range-State. iOS-UI-Tests prüfen iPad-Seitenkarte,
iPhone-Bottom-Sheet, Filter, Stats, Range und den Rückweg in den Editor.
