# PSMobile

PrusaSlicer-Kern auf Android und iOS, mit einer Oberflaeche, die fuer
Touch und Stift gebaut ist statt fuer Maus und Tastatur.

Stand: 2026-07-28 - Meilenstein M0

> **Klarstellung**: PSMobile ist kein Port der PrusaSlicer-Anwendung,
> sondern ein neuer mobiler Client um den PrusaSlicer-Kern (`libslic3r`).
> Die Desktop-GUI ist wxWidgets-basiert und kann technisch nicht auf
> Mobilgeraete mitkommen. Siehe `docs/entscheidungen.md`, E-01.
>
> PSMobile stammt **nicht** von Prusa Research.

## Dokumente

| Datei | Inhalt |
| --- | --- |
| `docs/01-brainstorming.md` | Machbarkeitsanalyse mit gemessenen Zahlen |
| `docs/02-architektur.md` | Schichten, Verzeichnisse, Threading, Speicher |
| `docs/03-roadmap.md` | Meilensteine M0 bis M9 mit pruefbaren Ergebnissen |
| `docs/04-lizenz-und-store.md` | AGPL und App-Store-Konflikt |
| `docs/05-ui-konzept-touch-stift.md` | Gesten, Layout, Stiftkonzept |
| `docs/entscheidungen.md` | ADRs - warum es so gebaut wird |
| `worklog/` | Sitzungsprotokolle |

## Aufbau

```
core/       C-ABI psmobile_core.h - die einzige Grenze zwischen App und Kern
viewport/   plattformneutraler OpenGL-ES-Renderer (C++)
android/    Kotlin + Jetpack Compose + JNI
ios/        Swift + SwiftUI + Bridging-Header
build/      Docker-Build-Image und Cross-Build-Skripte
cmake/      Toolchain-Dateien fuer Android und iOS
patches/    Patches gegen PrusaSlicer 2.9.6
external/   PrusaSlicer-Checkout (nicht eingecheckt)
```

## Bauen

Der Android-Cross-Build laeuft in Docker auf `localunraid`:

```bash
build/scripts/bootstrap.sh      # PrusaSlicer holen, Image bauen
build/scripts/build-deps.sh     # Dependencies fuer arm64-v8a
build/scripts/build-core.sh     # libslic3r + psmobile_core
```

iOS wird auf einem Mac mit Xcode gebaut, siehe `ios/README.md`.

## Basis

- PrusaSlicer `version_2.9.6` (Upstream, `prusa3d/PrusaSlicer`)
- Lizenz: AGPL-3.0, wie der Kern

## Verwandte Projekte

- `PrusaSlicer-BumpMesh-Projektfamilie` - Desktop-Fork, gleiche Codebasis
- `PrusaFarm-PrusaLink-Control` - moegliche Gegenstelle fuer Remote-Slicing
