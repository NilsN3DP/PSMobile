# PSMobile - iOS

Stand: 2026-07-28. Vorbereitet, aber noch nicht gebaut - dafuer braucht es
einen Mac.

## Was hier schon liegt

| Datei | Zweck |
| --- | --- |
| `../cmake/toolchains/ios.cmake` | CMake-Toolchain, nutzt CMakes eingebaute iOS-Unterstuetzung |
| `../build/scripts/build-ios.sh` | Deps- und Kern-Build, Gegenstueck zu den Android-Skripten |
| `PSMobile/Core/PsmCore.swift` | Swift-Huelle um das C-ABI |
| `PSMobile/SlicerModel.swift` | Zustandshalter, Gegenstueck zu `SlicerService` auf Android |
| `PSMobile/PSMobileApp.swift` | SwiftUI-Einstieg und Oberflaeche |
| `PSMobile/Support/PSMobile-Bridging-Header.h` | macht `psmobile_core.h` fuer Swift sichtbar |
| `project.yml` | XcodeGen-Spezifikation statt eingechecktem `.xcodeproj` |

## Erste Schritte auf dem Mac

```bash
# 1. Werkzeuge
xcode-select --install
brew install cmake ninja xcodegen

# 2. Quellen holen (falls das Repo frisch geklont wurde)
build/scripts/bootstrap.sh          # holt nur PrusaSlicer, Docker-Teil schlaegt fehl - das ist ok

# 3. Patches anwenden
git -C external/PrusaSlicer apply ../../patches/*.patch

# 4. Dependencies und Kern bauen (dauert lange, wie unter Android)
PSM_IOS_PLATFORM=OS64 build/scripts/build-ios.sh all

# 5. Ressourcen ins Bundle legen
build/scripts/stage-resources.sh ios

# 6. Xcode-Projekt erzeugen und oeffnen
cd ios && xcodegen generate && open PSMobile.xcodeproj
```

## Was erfahrungsgemaess noch Arbeit macht

- **GMP/MPFR**: bauen per autotools. Die Toolchain setzt `TOOLCHAIN_PREFIX`,
  aber auf macOS heissen die Werkzeuge anders als unter Linux. Falls
  `configure` stolpert, ist das die erste Stelle zum Nachsehen -
  vergleiche `build/scripts/mk-autotools-wrappers.sh`.
- **`-fno-aligned-allocation`**: setzt libslic3r fuer Apple-Ziele. Bei
  Deployment-Target 15.0 ist das nicht mehr noetig und kann stoeren.
- **Simulator vs. Geraet**: Deps muessen fuer jede Plattform getrennt
  gebaut werden (`OS64` bzw. `SIMULATORARM64`). Ein XCFramework, das
  beides buendelt, kommt in M8.

## Wichtig vor jeder Veroeffentlichung

PrusaSlicer steht unter AGPL-3.0. Die Auslieferung ueber den iOS App Store
ist rechtlich ungeklaert - siehe `../docs/04-lizenz-und-store.md`.
Bis dahin gilt: bauen und lokal testen ja, veroeffentlichen nein.
