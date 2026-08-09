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

## Fix Round 1 – Core-Metadaten und echte Instanzzahlen

Die Review-Befunde waren berechtigt: Android hatte Namen als `Bett N`
synthetisiert, Locks in `SharedPreferences` gespiegelt und Objektzahl als
Instanzzahl ausgegeben. Der Fix erweitert das C-ABI gezielt von 8 auf 9 um
`psm_bed_instance_count`. Android-JNI/PsmCore transportieren Core-Name,
Core-Lock, Objekt- und echte Gesamtinstanzzahl; Swift liest dieselbe neue
Funktion. Androids alter `bedLocks:<project>`-Key wird beim Öffnen nur
entfernt und niemals über importierte Core-Metadaten geschrieben.

`AndroidBedStripActions` ist die echte, von Simple und Advanced gemeinsam
verwendete Aktionsgrenze. Verhaltenstests mit einem In-Memory-Core-Port
prüfen Add/Select/Rename/Lock/Remove, finales und nichtleeres Bett,
unterschiedliche Objekt-/Instanzzahlen, Locked/Empty/Available-Arrange und
Moduswechsel ohne zweiten Aktivindex. Simple zeigt dieselbe beobachtbare
Tool-Meldung wie Advanced. Der alte Source-Text-Test wurde entfernt.

### RED/GREEN

- RED 1: `BedStripAdapterTest` kompilierte zunächst nicht, weil
  `AndroidBedSnapshot`/`AndroidBedStripAdapter` fehlten.
- RED 2: der erweiterte Test kompilierte zunächst nicht, weil
  `AndroidBedStripActions`/`AndroidBedPort` fehlten.
- Core-RED-Vertrag: ABI 9 und `psm_bed_instance_count` fehlten; die Fixture
  unterscheidet 1 Objekt/12 Instanzen und 1 Objekt/0 Instanzen.
- iOS-UI-RED: Empty-Arrange war in Advanced nicht erreichbar, weil der
  Toolbar-Knopf deaktiviert war. Er bleibt nun bedienbar und das gemeinsame
  Panel zeigt die Contract-Erklärung.

### Fix-Verifikation

- Tatsächliche Windows-Laufzeit: `openjdk version "21.0.10" 2026-01-20`
  aus Android Studios JBR. Die Java-/Kotlin-Kompatibilitätsziele bleiben 17.
- `gradlew.bat :shared:allTests :app:testProductionDebugUnitTest
  :app:testPreviewDebugUnitTest :app:assembleProductionDebug
  :app:assemblePreviewDebug` – BUILD SUCCESSFUL, 125 Tasks.
- Frische Mac-Worktree `psmobile-task5-fix1`, Quell-HEAD `f2ab035be10f86fa17cb3a2a350a35d73bd39a0d`;
  K/N-Framework, 325-Step-Core und Simulator-`psm_contract_tests` waren grün.
- XcodeGen sowie Simulator-Builds `PSMobile` und `PSMobilePreview` auf
  iPhone 17 Pro/iOS 26.2 – `BUILD SUCCEEDED`.
- Der neue deterministische `BedModeStateTests` lief auf dem Simulator grün
  und prüft den von beiden `BedSelector`-Hosts verwendeten Produktionsadapter
  über Simple→Advanced→Simple. Der direkte XCUI-Navigationsfall wurde
  entfernt, weil SwiftUI `Menu`/offscreen Scroll-Children unter iOS 26 nicht
  stabil in den Automation-Baum exportiert; dies ersetzt keinen Produktcheck.
- `MultiBedArrangeUITests` – 5 ausführbare Tests grün (die drei bisherigen
  plus Core-Rename und sichtbare Empty-Erklärung).
- Mac-Hauptcheckout pre/post: HEAD `e1e74a062f140e1b0882f149a0e115317a535a64`,
  114 Statuszeilen, SHA-256 `73fcc11096f86c147a58d7937490b2bec455b8e0077c5fda4e77002b55889685`.

### Fix Round 2

- JNI-Bednamen verwenden jetzt explizite UTF-16/UTF-8-Konvertierung; dadurch
  bleiben ergänzende Unicode-Zeichen (z. B. `🛠️`) erhalten und feste 128-Byte-
  C-ABI-Namen werden nur an gültigen UTF-8-Grenzen gekürzt. Der Android-
  Produktionstest `BedStripAdapterTest.supplementary unicode bed names remain
  intact through production snapshot` ist grün.
- `gradlew.bat :app:testProductionDebugUnitTest --tests
  de.psmobile.ui.BedStripAdapterTest --no-daemon` – BUILD SUCCESSFUL unter
  JBR 21.0.10 (JVM-Ziel 17).
- ABI9-Native-Artefakte konnten in diesem Windows-Worktree nicht nachgewiesen
  werden: `build-out/core-arm64-v8a`/`core-x86_64` und
  `android/app/src/main/jniLibsFixed` enthalten keine `.so`/Fingerprints;
  Docker ist auf dem Host nicht verfügbar. Der bestehende `stage-native.sh`
  bleibt der deterministische Gatekeeper (ABI/API/Fingerprint), daher wurden
  keine alten Bibliotheken übernommen und kein Runtime-Smoke-Test behauptet.
