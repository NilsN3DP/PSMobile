# PSMobile - Architektur

Stand: 2026-07-28

## Schichten

```
+---------------------------+   +---------------------------+
|  android/  Kotlin+Compose |   |  ios/  Swift+SwiftUI      |   nativ, 2x
|  - Toolbar, Objektliste   |   |  - gleiche Struktur       |
|  - Einstellungs-Sheets    |   |  - Files/Share/Pencil     |
|  - Files/Share/S-Pen      |   |                           |
+------------+--------------+   +-------------+-------------+
             |  JNI                           |  Bridging-Header
             v                                v
+-------------------------------------------------------------+
|  core/   psmobile_core.h  (reines C-ABI)                     |   1x C++
|  Session, Modell-IO, Presets, Slice-Job, Export              |
+-------------------------------------------------------------+
|  viewport/   GLES-Renderer (C++), Kamera, Picking, Gesten    |   1x C++
|  nutzt resources/shaders/ES/ aus PrusaSlicer                 |
+-------------------------------------------------------------+
|  external/PrusaSlicer   libslic3r, libnest2d, libvgcode      |   unveraendert
+-------------------------------------------------------------+
|  Cross-gebaute Dependencies (Boost, TBB, Eigen, CGAL, ...)   |
+-------------------------------------------------------------+
```

Regel: **Alles, was nicht Darstellung ist, gehoert unter das C-ABI.**
Jede Zeile Logik oberhalb wird zweimal geschrieben.

## Verzeichnisse

| Pfad | Inhalt |
| --- | --- |
| `core/include/psmobile_core.h` | die einzige oeffentliche Grenze |
| `core/src/` | C-ABI-Implementierung gegen libslic3r |
| `viewport/` | plattformneutraler GLES-Renderer |
| `android/` | Gradle-Projekt, Compose-UI, JNI |
| `ios/` | Xcode-Projekt, SwiftUI, Swift-Bridge |
| `build/docker/` | Build-Image mit Android-NDK |
| `build/scripts/` | Cross-Build-Skripte (Deps, Core, App) |
| `cmake/toolchains/` | Android- und iOS-Toolchain-Dateien |
| `patches/` | Patches gegen PrusaSlicer 2.9.6 |
| `external/PrusaSlicer` | Upstream-Checkout, Tag `version_2.9.6` |

## Threading- und Prozessmodell

- **UI-Thread**: nur Compose/SwiftUI. Nie blockieren.
- **Render-Thread**: eigener GL-Kontext, feste Frameloop, liest einen
  doppelt gepufferten Szenenzustand.
- **Slice-Job**: eigener Thread im Kern; TBB parallelisiert darin.
  Fortschritt und Abbruch laufen ueber Callbacks im C-ABI.
- **Android, aktuelle Phase**: Slicing läuft als started und bound
  Foreground-Service im App-Prozess. Modell und Konfiguration werden
  für jeden Job unter einem Lock kopiert; der Worker arbeitet
  ausschließlich auf diesem Snapshot. Ein eigener
  `android:process=":slicer"` folgt erst mit einem serialisierbaren
  Auftrag und AIDL/Messenger – ein lokaler Binder und ein nativer
  Session-Zeiger sind nicht prozessübergreifend.
- **iOS**: kein zweiter Prozess moeglich. Stattdessen frueh
  `os_proc_available_memory()` pruefen und beim Import warnen bzw.
  dezimieren.

## Speicherstrategie

Das groesste Laufzeitrisiko ist der Arbeitsspeicher, nicht die Rechenzeit.

1. **Import-Dezimierung**: Meshes ueber einer Dreiecksschwelle werden fuer
   die *Anzeige* dezimiert; das Original bleibt fuer das Slicing auf Platte
   und wird erst im Slice-Job geladen.
2. **G-Code streamt**: Export schreibt direkt in eine Datei, nie erst
   komplett in den RAM.
3. **Preview laedt gestaffelt**: `libvgcode` bekommt nur den sichtbaren
   Layerbereich.
4. **Harte Obergrenzen** je Geraeteklasse, mit ehrlicher Meldung statt
   Absturz.

## Remote-Slicing (optional, ab M7)

Fuer grosse Modelle kann der Slice-Job an einen PC oder Server im LAN
abgegeben werden. Dasselbe C-ABI, andere Implementierung dahinter.
Passt zum vorhandenen Projekt `PrusaFarm-PrusaLink-Control`.

## Was aus der Desktop-GUI nach unten gezogen werden muss

Diese Logik sitzt im Desktop-`src/slic3r`, gehoert aber fachlich in den
Kern und muss beim Bauen von `core/` mitwandern:

| Thema | Desktop-Ort (Referenz) | Ziel |
| --- | --- | --- |
| PrusaLink/Connect-Upload | `slic3r/Utils/PrintHost.*`, `PrusaConnect.*` | nativ (E-09) |
| Arrange-Aufruf | `slic3r/GUI/Jobs/ArrangeJob.*` | `core/` |
| G-Code-Preview-Daten | `slic3r/GUI/GCodeViewer.*` + `libvgcode` | `viewport/` |
| Gizmo-Logik (spaeter) | `slic3r/GUI/Gizmos/*` | `viewport/` |

Diese Tabelle ist die eigentliche Portierungsliste. Sie ist deutlich
kuerzer als 166k LOC - der Rest der Desktop-GUI ist wxWidgets-Layout,
das ersatzlos entfaellt.

**Korrektur 2026-07-28**: Die erste Fassung dieser Tabelle nannte auch
`PresetBundle` und `Preset` als zu portieren. Das war falsch - beide
liegen in PrusaSlicer 2.9.6 bereits in `libslic3r/` und sind
wx-frei. Die gesamte Profilverwaltung kommt also ohne Zutun mit.
Ebenso ist `PrintConfig` bereits datengetrieben, sodass sich die
Experten-UI daraus erzeugen laesst.
