# Bericht: iOS-Pinseloptionen

Stand: 2026-08-05

## Ergebnis

Der Advanced-Arbeitsbereich verwendet jetzt einen gemeinsamen
`PaintOptions`-Wert für Oberfläche, Core-Aufruf und Viewport. Der neue,
additive C-ABI-Einstieg reicht Objekt, getroffene Instanz, Volumen,
Facette, Weltposition und optional die vorherige Weltposition an
PrusaSlicers `TriangleSelector` weiter.

Implementiert sind:

- Brush mit Circle/Sphere sowie Capsule2D/Capsule3D während eines Zugs,
  Radius und optionaler echter Dreiecksteilung;
- Smart Fill für Support und MMU sowie Bucket Fill für MMU;
- Seam ausschließlich mit den von Prusa angebotenen Brush-Formen;
- Support-/Seam-Zustände, MMU-Extruderzustände, Radieren und tool-isoliertes
  Clear;
- transparente Annotation-Overlays und ein sichtbarer, nach Modus, Form
  und Radius abgeleiteter Viewport-Cursor;
- klar sichtbare aktive Optionen, Fill-Winkel, Scope
  „Objekt (alle Kopien)“ und Accessibility-Kennungen in SwiftUI.

Die alte `psm_model_paint_brush`-Funktion bleibt ABI-kompatibel und ist
nur noch ein dünner Sphere-Wrapper auf dem neuen Einstieg.

## Slicer- und Scope-Vertrag

Es gibt keine neue Facetten-Speicherlogik. Brush, Smart Fill und Bucket
Fill deserialisieren ausschließlich die jeweilige Prusa-Annotation in
einen `TriangleSelector`, wenden Prusas öffentliche Algorithmen an und
schreiben den Selector wieder in dieselbe `FacetsAnnotation`.

`instance_index` bestimmt nur die Transformation des tatsächlichen
Treffers und wird aus `psm_surface_hit` unverändert bis zum Core gereicht.
Die gespeicherte Annotation bleibt transparent volumebasiert und erscheint
deshalb korrekt auf allen Instanzen desselben Volumens. Der Viewport liest
für seinen Farbpass direkt diese Annotation; Swift und Renderer besitzen
keinen zweiten Fill- oder Facettenzustand.

## Testgetriebene Umsetzung

RED wurde zuerst mit neuen C-Vertragstests hergestellt. Die Übersetzung
brach erwartungsgemäß an den noch unbekannten Typen und Funktionen
`psm_paint_options`, `psm_paint_mode`, `psm_paint_shape` und
`psm_model_paint_apply` ab. Erst danach wurden C-ABI, Core, Viewport und
SwiftUI implementiert.

Die Core-Verträge prüfen:

- Optionsversion, ungültige Instanz und die Matrix der getroffenen
  zweiten Instanz;
- unterscheidbares Circle-/Sphere-Verhalten und den Capsule-Zug;
- Smart Fill und Bucket Fill mit erwarteten Facettenzahlen;
- zulässige Modi je Tool, getrennte Annotationen und tool-isoliertes
  Clear;
- 3MF-Export und Reimport von Support-, Seam- und MMU-Annotationen aus
  dem neuen Pfad.

Die XCUITests prüfen die sichtbaren und toolabhängigen Optionen, Scope,
den tatsächlichen Smart-/Bucket-Core-Pfad, die vom echten Viewport
gemeldete Cursor-/Annotation-Markierung sowie Clear.

## Verifikation auf macOS

Alle Läufe erfolgten in dem separaten, vom Baseline-Commit erzeugten
Mac-Arbeitsbaum `~/psmobile-paint-options-red`; der bestehende schmutzige
Mac-Hauptbaum wurde nicht verändert.

- iOS-Core und Viewport wurden neu übersetzt und in die App gelinkt:
  `** BUILD SUCCEEDED **`.
- Gelinkter Core-Vertragstest im iPad-Simulator:
  `PASS: psm_contract_tests`.
- Fokussierter UI-Lauf `PSMobileUITests/PaintUITests`: 3 Tests,
  0 Fehler. Nach der abschließenden Same-Instance-Guard für Capsule
  erneut: 3 Tests, 0 Fehler.
- Vollständige iOS-Suite: `** TEST SUCCEEDED **`; 6 Unit-Tests und
  69 UI-Tests, jeweils 0 Fehler. `SelbsttestUITests` ist darin
  ebenfalls erfolgreich.
