# Bericht: Variable Schichthöhe am Modell

## Ergebnis

Ein gespeichertes variables Schichthöhenprofil wird im Editor jetzt auf
dem primär ausgewählten `ModelObject` dargestellt. Die Darstellung
verwendet PrusaSlicers vorhandene Pipeline:

- `PrintObject::slicing_parameters`
- `generate_object_layers`
- `generate_layer_height_texture`
- den Shader `variable_layer_height`

SwiftUI zeichnet keine Ersatzfärbung über das Modell. Es zeigt nur eine
zugängliche Legende mit den vom aktiven GL-Viewport gemeldeten Grenzen
„Fein“ und „Grob“ und der Kennung `viewport.schichthoehen`. Die
G-Code-Vorschau bleibt davon getrennt. Fehlt Shader oder Textur, wird
auch die Legende nicht gemeldet.

Beim Wechsel der primären Auswahl wird die Rendertextur neu aufgebaut.
Nach Löschen des Profils entstehen keine Renderdaten mehr und die
Viewport-Markierung verschwindet.

## Architektur

`psm_layer_profile_render.cpp` bildet die gemeinsame CPU-Seite für
Vertrag und OpenGL. Dadurch testet der C-Vertrag dieselben Profildaten,
die später an den Shader gehen, statt eine zweite Testimplementierung.

Die Profilhöhe wird aus `max_z - min_z` bestimmt. Die bereits in
Bettkoordinaten aufgebauten VBO-Positionen werden für die Abtastung um
`min_z` zurückverschoben. Das ist wichtig für angehobene oder anderweitig
transformierte Objekte.

Der Renderer hält genau eine Profiltextur für die primäre Auswahl.
Andere Objekte behalten ihre Extruderfarbe. Da ein Schichthöhenprofil in
PrusaSlicer am `ModelObject` liegt, teilen dessen Instanzen dieselbe
Profilfärbung.

Auf iOS begrenzt `GL_APPLE_texture_max_level` die GLES2-Textur wie bei
PrusaSlicer auf zwei Stufen. Level 1 enthält Prusas eigene streifenfreie
Detailstufe; der hohe Bias im unveränderten Shader landet deshalb exakt
dort statt in einer künstlichen 1×1-Endstufe.

## Test-first-Nachweis

### RED

1. Der erweiterte C-Vertrag ließ sich kompilieren, scheiterte beim Linken
   am noch fehlenden Symbol `_psm_viewport_layer_visualization_info`.
2. Der neue iOS-UI-Test lief und scheiterte, weil
   `viewport.schichthoehen` nach „Übernehmen“ nicht existierte.
3. Für den Löschpfad scheiterte derselbe Test anschließend am noch
   fehlenden Selektor `schichten.abbrechen`.

### GREEN

Auf dem iPad-Simulator
`412DBC17-6A94-4425-97D1-B3C28D632DD7`:

```text
LayerProfileUITests: 4 Tests, 0 Fehler
PreviewUITests:      2 Tests, 0 Fehler
ViewportUITests:     7 Tests, 0 Fehler
Gesamt:             13 Tests, 0 Fehler
** TEST SUCCEEDED **
```

Der neue Test
`testAnwendenMarkiertDasModellUndZuruecksetzenEntferntDieMarkierung`
prüft:

1. Profilstützstelle mit `0,10 mm` anlegen,
2. „Übernehmen“,
3. sichtbare/zugängliche Viewport-Markierung mit `0,10 mm`,
4. Profil zurücksetzen,
5. Markierung verschwindet.

Nach der Abschlussreview wurde dieser Test erneut gegen die an den
aktiven GL-Viewport gekoppelte Markierung ausgeführt:

```text
1 Test, 0 Fehler, 20.411 s
** TEST SUCCEEDED **
```

Der C-Vertrag prüft außerdem Texturbreite, Texturhöhe, belegte Zellen,
Objekthöhe, minimale/maximale Schichthöhe und den deaktivierten Zustand
nach dem Löschen.

## Behobener Fehler während GREEN

Der erste Renderer-Lauf stürzte im Texturgenerator ab. Ursache war eine
falsche Höhenbasis: `object.max_z()` ergab im Fixture `180 mm`, obwohl
das Objekt selbst nur `10 mm` hoch war. Zusammen mit einer zu kleinen
`256 × 256`-Textur lief die zweite Detailstufe über den Puffer.

Die Korrektur verwendet:

- `object.max_z() - object.min_z()` als Objekthöhe,
- PrusaSlicers originale Kapazität `1024 × 1024`,
- die Verschiebung um `-object.min_z()` im Shaderraum.

Beide neuen C++-Übersetzungseinheiten wurden für den arm64-iOS-Simulator
kompiliert. Der vollständige bestehende Vertragsprozess erreicht und
besteht die neuen Renderer-Assertions, endet danach aber an
repositoryfremden fehlenden Fixtures
`psmobile-multibed.3mf` beziehungsweise installierten Profilfixtures.
Diese Dateien fehlen auch im bestehenden Mac-Checkout und gehören nicht
zu diesem Task.

## Geänderte Bereiche

- gemeinsamer PrusaSlicer-Profiltextur-Builder und C-Diagnosevertrag
- GLES-Viewport mit `variable_layer_height` für die primäre Auswahl
- aktive GL-Textur-Rückmeldung an UIKit/SwiftUI
- Profillegende in einfachem und erweitertem Editor
- UI- und Core-Vertragstests
- CMake-Quellenliste
