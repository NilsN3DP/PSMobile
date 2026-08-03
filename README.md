# PSMobile

> **Wer an diesem Projekt arbeitet - Mensch oder Agent - liest zuerst
> [docs/arbeitsjournal.md](docs/arbeitsjournal.md) und schreibt nach
> jedem fertigen Schritt hinein.** Dort steht, wer gerade woran ist, was
> zuletzt schiefging und was man nicht zweimal bauen muss.


PrusaSlicer-Kern auf Mobilgeräten, mit einer Oberfläche, die für Touch
und Stift gebaut ist statt für Maus und Tastatur.

Stand: 2026-07-30 – Android-first. iOS folgt erst nach einem stabilen,
auf Geräten geprüften Android-Ablauf.

> **Klarstellung**: PSMobile ist kein Port der PrusaSlicer-Anwendung,
> sondern ein neuer mobiler Client um den PrusaSlicer-Kern (`libslic3r`).
> Die Desktop-GUI ist wxWidgets-basiert und kann technisch nicht auf
> Mobilgeraete mitkommen. Siehe `docs/entscheidungen.md`, E-01.
>
> PSMobile stammt **nicht** von Prusa Research.

## Aktueller Android-Stand

Der durchgängige Projektablauf ist im x86_64-Emulator geprüft:
3MF wahlweise als Geometrie oder Projekt öffnen, Druckerprofil
übernehmen, mehrere Betten direkt wählen, bearbeiten, Undo/Redo,
Objekt-/Volumenextruder zuweisen, als vollständige 3MF speichern und
mit denselben Bettzuordnungen wieder öffnen. Reale arm64-Geräte- und
PrusaLink-Hardwaretests bleiben das nächste Release-Gate.

## Dokumente

| Datei | Inhalt |
| --- | --- |
| `docs/01-brainstorming.md` | Machbarkeitsanalyse mit gemessenen Zahlen |
| `docs/02-architektur.md` | Schichten, Verzeichnisse, Threading, Speicher |
| `docs/03-roadmap.md` | Meilensteine M0 bis M9 mit pruefbaren Ergebnissen |
| `docs/04-lizenz-und-store.md` | AGPL und App-Store-Konflikt |
| `docs/05-ui-konzept-touch-stift.md` | Gesten, Layout, Stiftkonzept |
| `docs/10-funktionsvergleich.md` | Aktueller Vergleich mit PrusaSlicer Desktop |
| `docs/feature-matrix.json` | Maschinenlesbarer Status mit Evidenz und Blockern |
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
build/scripts/run-core-tests.sh # C-ABI-Vertragstest auf Android
build/scripts/stage-native.sh   # nur Bibliotheken mit aktuellem Fingerprint
```

Der aktuelle Stand lässt sich zusätzlich prüfen mit:

```bash
python build/scripts/feature-report.py --check
python -m unittest discover -s build/scripts/tests -p 'test_*.py'
./android/gradlew -p android testDebugUnitTest
```

iOS ist derzeit nur ein Scaffold. Die Portierung startet nach dem
Android-Gerätegate, siehe `ios/README.md`.

## Basis

- PrusaSlicer `version_2.9.6` (Upstream, `prusa3d/PrusaSlicer`)
- Lizenz: AGPL-3.0, wie der Kern

## Verwandte Projekte

- `PrusaSlicer-BumpMesh-Projektfamilie` - Desktop-Fork, gleiche Codebasis
- `PrusaFarm-PrusaLink-Control` - moegliche Gegenstelle fuer Remote-Slicing
