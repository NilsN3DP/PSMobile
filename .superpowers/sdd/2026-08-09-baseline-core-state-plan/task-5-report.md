# Task 5 – gemeinsame Mehrbett-Darstellung

## Ergebnis

`BedStripContract` liegt einmal im echten KMP-Modul `android/shared` und
exportiert als Teil von `PSMShared.framework` nach Swift. Android Compose und
SwiftUI lesen daraus dieselben stabilen Bett-IDs, Namen, Reihenfolge,
Aktiv-Markierung und Aktionen. Die jeweiligen Kernadapter (`SlicerService`
beziehungsweise `SlicerModel`/`PsmCore`) behalten die alleinige Hoheit über
Mutation und aktives Bett.

Android Simple nutzt nun denselben `BedSelector` wie Advanced; die lokale
Chip-Leiste und die weitergereichte aktive Indexkopie aus `SimpleModelSheet`
sind entfernt. iOS Simple zeigt denselben Selector und das Arrange-Panel wie
Advanced; der Viewport zeichnet dabei Mehrbett mit dem Kern-Aktivbett. Ein
gesperrtes oder leeres Bett zeigt beim Arrange eine nachvollziehbare Erklärung
statt den Befehl stumm auszublenden.

## Test-first-Evidenz

Der ursprüngliche RED-Lauf für die fehlenden `BedStrip*`-Typen wurde vor der
Übernahme protokolliert. In der Übernahmerunde kamen zwei gezielte RED-Fälle
hinzu:

- `BedStripContractTest.anInstancedBedCannotBeRemovedEvenWhenItsObjectCountIsStale`
  scheiterte zunächst in `:shared:allTests` (132 Tests, ein Fehler), weil ein
  Bett mit Instanzen aber einem veralteten Objektzähler entfernbar war. Die
  Regel verlangt jetzt für `canRemove` und `remove` beide Zähler bei null.
- `SimpleBedContractIntegrationTest` scheiterte zunächst, weil Simple keinen
  gemeinsamen Selector und weiterhin lokale Bettchips besaß. Nach der
  Umstellung lief der gezielte Production-Debug-Test grün.

Der erste relevante iOS-XCTest-Lauf fand anschließend ein echtes
Integrationsproblem: ein gesperrtes Bett deaktivierte Arrange, sodass keine
erklärende Rückmeldung entstehen konnte. Das Panel verwendet nun die aus
`PSMShared` exportierte `ArrangeAvailability` für eine direkte Locked-/Empty-
Meldung. Ein Swift-Compilerlauf verlangte zusätzlich einen `default`-Fall für
das Kotlin-exportierte Enum; der fokussierte XCTest-Lauf ist danach grün.

## Verifikation

- Windows/Java 17 (Android-Studio-JBR):
  `:shared:allTests :app:testProductionDebugUnitTest :app:testPreviewDebugUnitTest :app:assembleProductionDebug :app:assemblePreviewDebug` – erfolgreich.
- Frische, abgehängte Mac-Worktree
  `/Volumes/Macintosh_HD/Users/user289137/psmobile-task5-bedstrip`, exakt von
  `155f78c2bdfc8aad3523fc5c8ccbb3e0e9f9bf0e` plus Task-Diff; der schmutzige
  Mac-Hauptcheckout blieb unangetastet.
- Kotlin/Native:
  `:shared:linkDebugFrameworkIosSimulatorArm64` – erfolgreich. Der generierte
  Framework-Header enthält `PSMSBedStripContract`, `PSMSBedInput`,
  `PSMSBedStripState`, `PSMSBedStripItem`, `PSMSBedStripMutation` und
  `PSMSArrangeAvailability`.
- Simulator-Core: `PASS: psm_contract_tests` mit erzeugten Mehrbett- und
  CORE-One-Fixtures.
- XcodeGen sowie Simulator-Builds der Schemes `PSMobile` und
  `PSMobilePreview` – erfolgreich.
- `PSMobileUITests/MultiBedArrangeUITests` – 3 Tests, 0 Fehler.

Die für den frischen Mac-Build benötigten PrusaSlicer-, iOS-Dependency- und
Resource-Artefakte wurden nur als Symlinks zu vorhandenen Caches eingebunden;
kein Quellstand im Mac-Hauptcheckout wurde geändert.
