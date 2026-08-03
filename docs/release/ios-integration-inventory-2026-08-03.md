# iOS-Integrationsinventar — 2026-08-03

## Integrationsgrenze

Der Arbeitsbranch `codex/ios-parity-beta-rc` enthaelt den Mac-Stand
`e8a5f03` auf Basis der sechs zuvor lokalen Commits `7f2b0ef` bis
`1f3f8b1`. Der Commit umfasst die iOS-App, den C-ABI-Swift-Host,
Viewport, Xcode-Projekt, XCTest/XCUITest, iOS-Ressourcen, das gemeinsame
Kotlin-Modul und nur die Android-/CMake-/Build-Aenderungen, die dieses
Modul oder den iOS-Build einbinden.

Nicht uebernommen wurde die geloeschte temporaere Datei
`.git-commit-msg.tmp`. Der vorherige Windows-`main`-Arbeitsbaum mit
separater PrusaLink-Arbeit blieb unveraendert.

## Frische Simulatorabnahme

Am 2026-08-03 auf `FF738-I`:

```text
Xcode 26.3 (Build 17C529)
Destination: iPad Pro 13-inch (M5), iOS 26.3.1
xcodebuild -project PSMobile.xcodeproj -scheme PSMobile ... test
Executed 27 tests, with 0 failures in 243.421 seconds
** TEST SUCCEEDED **
```

Die Suite deckt Setup, Simple Mode, Settings, Projekte, Objektleiste,
Klonen/Schneiden, Slice, Vorschau, G-Code-Layer, Drehen und Zoomen ab.
Sie ist kein Ersatz fuer das iPad-, Speicher- und echte-PrusaLink-Gate.

## Ressourcenintegritaet

`ios/Resources/psresources` und `ios/Resources/psui` sind unveraenderte
Prusa-Profil-, Shader-, SVG- und Sprachressourcen. Sie enthalten bereits
im Original Trailing-Whitespace. Deshalb wird `git diff --check` fuer
eigenen Swift/Kotlin/C++/Build-Code strikt ausgefuehrt, waehrend diese
importierten Ressourcen und die zugehoerigen Quellpatches explizit aus
diesem Whitespace-Gate ausgeschlossen bleiben. Inhaltliche oder
formatierende Aenderungen an den Vendor-Dateien erfolgen nur mit einer
eigenen Quellenaktualisierung.

## Verbleibende Integrationsarbeit

Die naechsten Features und ihr Testvertrag stehen in
`docs/superpowers/plans/2026-08-03-ios-parity-and-beta-rc.md`:

1. INDX/MMU-Positionen und ColorMix.
2. PrusaLink, Keychain und sichere Profilupdates.
3. Vollstaendige Advanced-, Sprach- und Rotationsparitaet.
4. Android- und iOS-Geraete-/Drucker-Regressionen.
