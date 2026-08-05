# iOS Advanced Stabilisierung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Advanced-Arbeitsbereich ist auf dem iPad zuverlässig bedienbar: Auswahl öffnet den Objektinspektor, die Seitenleiste hat keine doppelten Titel, die Objektleiste überlagert sie nicht, Arrange wirkt auf das richtige Bett und Touch-Gesten entsprechen dem vereinbarten Modell.

**Architecture:** Die Reparatur bleibt zweischichtig: SwiftUI entscheidet nur über Sichtbarkeit und Layout, der C++-Viewport entscheidet über Picking und Gesten. Der Arrange-Fehler wird im Kern durch einen testbaren Bettbereich behoben; SwiftUI ruft nur die passende Variante auf. Kein neuer UI-Zustand wird global gespeichert.

**Tech Stack:** SwiftUI, XCTest/XCUITest, C++17, PrusaSlicer 2.9.6, CMake/Xcode.

## Global Constraints

- iOS ist der führende und auf dem iPad testbare Stand.
- Kommentare erklären auf Deutsch die Ursache bzw. Entscheidung.
- Bettnamen und Bettsperren bleiben Sitzungsdaten, nicht UserDefaults.
- Tests prüfen Verhalten; ein grüner Build ohne ausgeführte Tests gilt nicht als Nachweis.
- Nach einem abgeschlossenen Block: Journal, Commit, GitHub-Push und IPA nach OneDrive.

---

### Task 1: Baseline und Fehlerklassifikation

**Files:**

- Inspect: `ios/PSMobileUITests/AdvancedWorkflowUITests.swift`
- Inspect: `ios/PSMobileUITests/LayerProfileUITests.swift`
- Inspect: `ios/PSMobileUITests/ResponsiveLayoutUITests.swift`
- Inspect: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Inspect: `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`

**Interfaces:**

- Consumes: Startargumente `-psm-start-advanced` und `-psm-load-cube`.
- Produces: Reproduzierbare Testliste mit Ursache je Ausfall.

- [ ] **Step 1: Volle iOS-Suite ausführen**

Run: `xcodebuild -project PSMobile.xcodeproj -scheme PSMobile -destination 'platform=iOS Simulator,id=C4687F59-5167-4B0E-A255-16EA02F65B41' test`

Expected: Jede Testklasse meldet `Executed N tests`; Fehler werden nicht durch `TEST SUCCEEDED` verdeckt.

- [ ] **Step 2: Auswahlpfad gegen den semantischen Test prüfen**

Der Test tippt `advanced.objekt.<id>` und erwartet danach mindestens `advanced.scale.prozent`, `advanced.drehen.rechts` und `advanced.einpassen`. Prüfen, ob die Liste auf den Edit-Reiter wechselt oder der Inspector nur außerhalb des sichtbaren Bereichs liegt.

- [ ] **Step 3: Ausfälle als Produktfehler oder Testisolation einordnen**

Ein Test darf nur angepasst werden, wenn er einen Zustand anderer Tests erbt. Bei echter fehlender Erreichbarkeit wird Produktionscode geändert.

### Task 2: Advanced-Seitenleiste und Objektleiste

**Files:**

- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`
- Modify: `ios/PSMobile/Screens/SimpleObjectBarView.swift`
- Modify: `ios/PSMobileUITests/AdvancedWorkflowUITests.swift`
- Modify: `ios/PSMobileUITests/ResponsiveLayoutUITests.swift`

**Interfaces:**

- Consumes: `SlicerModel.selectedId`, `seiteOffen`, `psScale`.
- Produces: `SimpleObjectBarView(zeigtZurueck: Bool)`; Advanced rendert sie nur über dem freien Viewportbereich.

- [ ] **Step 1: Je einen fehlschlagenden UI-Test für Titel und Objektleiste schreiben**

Der Test prüft, dass `inspektor.objekte` genau eine sichtbare Überschrift liefert, und dass die Objektleiste im Advanced kein `simple.objekt.zurueck` anbietet und rechts vor der Seitenleiste endet.

- [ ] **Step 2: Den Red-Lauf ansehen**

Run: `xcodebuild ... -only-testing:PSMobileUITests/AdvancedWorkflowUITests -only-testing:PSMobileUITests/ResponsiveLayoutUITests test`

Expected: Der neue Test schlägt gegen die doppelte/überbreite Fassung fehl.

- [ ] **Step 3: Minimal reparieren**

Die äußeren `bereich(_:)`-Titel bleiben die einzigen Titel. Die Objektleiste erhält den Parameter `zeigtZurueck`, verwendet ihre Inhaltsbreite und erhält im Advanced den rechten Abstand der offenen Seitenleiste. Der Simple Mode behält seine Rücksetzen-Aktion.

- [ ] **Step 4: Zieltests grün ausführen**

Run: derselbe `xcodebuild`-Befehl.

Expected: Jede ausgeführte Klasse meldet `0 failures`.

### Task 3: Auswahl, Rotate, Einpassen und Schichthöhe stabilisieren

**Files:**

- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Modify: `ios/PSMobile/Screens/AdvancedObjectInspectorView.swift`
- Modify: `ios/PSMobile/Screens/LayerProfileView.swift`
- Modify: `ios/PSMobile/SlicerModel.swift`
- Modify: `ios/PSMobileUITests/AdvancedWorkflowUITests.swift`
- Modify: `ios/PSMobileUITests/LayerProfileUITests.swift`

**Interfaces:**

- Consumes: `model.select(_:)`, `model.objects`, `model.layerProfile(_:)`.
- Produces: Ein Objektlisten-Tap führt deterministisch in den sichtbaren Bearbeitenbereich; `advanced.schichten` wird nach einer Auswahl zuverlässig erreichbar.

- [ ] **Step 1: Bestehende sechs roten Tests einzeln ausführen**

Run: `xcodebuild ... -only-testing:PSMobileUITests/AdvancedWorkflowUITests -only-testing:PSMobileUITests/LayerProfileUITests test`

Expected: Die aktuelle Auswahl-/Inspector-Kette schlägt reproduzierbar fehl.

- [ ] **Step 2: Sichtbarkeit des Inspector-Blocks mit einem UI-Test festlegen**

Nach Antippen eines Objekts wird der Bearbeitenbereich aktiv, die Auswahlzeile bleibt erreichbar und die Transformationsfelder sind im Hierarchiebaum vorhanden und treffbar.

- [ ] **Step 3: Minimalen Auswahl-/Scroll-Zustand implementieren**

Nur wenn der Inspector außerhalb der sichtbaren Scrollfläche liegt, wird gezielt dorthin gescrollt oder die Struktur so umgeordnet, dass Transformations- und Schichten-Einstiege oben erreichbar sind. Drehung und Einpassen bleiben Kernaufrufe, keine Spiegelwerte im SwiftUI-Zustand.

- [ ] **Step 4: Alle sieben Tests grün ausführen**

Run: derselbe `xcodebuild`-Befehl.

Expected: 0 failures, inklusive Wertänderung nach 90° und Einpassen.

### Task 4: Arrange, Fingerziehen und Gizmo-Trefferzone

**Files:**

- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/include/psmobile_core.h` only if a current-bed Arrange-Parameter fehlt
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `viewport/include/psm_viewport.h` only if ein messbarer Pick-Helfer fehlt
- Modify: `ios/PSMobile/Viewport/ViewportView.swift`
- Modify: `ios/PSMobileUITests/ViewportUITests.swift`
- Test: core/viewport test target according to existing CMake tests

**Interfaces:**

- Consumes: aktives Bett, `ArrangeSettings`, Viewport-Gizmo-Positionen in Bildschirmkoordinaten.
- Produces: Arrange ändert nur das gewünschte Bett; Objektziehen ohne aktiven Move-Gizmo; mit Move-Gizmo nur achsweises Ziehen über getestete Bildschirm-Pickzonen.

- [ ] **Step 1: Kern-/Viewport-Tests vor dem Code ergänzen**

Ein Arrange-Test legt Objekte auf zwei Betten an und beweist, dass ein Arrange des zweiten Betts weder Bett 1 verschiebt noch das aktive Bett wechselt. Ein Gizmo-Test misst Treffer bei definierten Pixelabständen um eine Achse.

- [ ] **Step 2: Red-Läufe erfassen**

Run: vorhandener Core-/Viewport-Testbefehl plus `ViewportUITests`.

Expected: Der Arrange-Test zeigt die Bett-0-Mutation; der Pick-Test dokumentiert die zu kleine zoomabhängige Trefferzone.

- [ ] **Step 3: Kern und Touch-Weiche minimal korrigieren**

`psm_arrange` darf keine Instanzen auf Bett 0 umhängen. In `touchesMoved` setzt nur ein Touch ohne aktiven Move-Gizmo `dragObject`; der aktive Move-Gizmo akzeptiert ausschließlich die viewportseitig gepickte Achse. Die Pick-Hülle wird in Pixeln und mit Mindestbreite gerechnet.

- [ ] **Step 4: Wiederholung und Gegenfälle testen**

Run: alle neuen Core-/Viewport-Tests und `ViewportUITests`.

Expected: Objektziehen, Orbit ohne Objekt und Achsziehen haben jeweils getrennte, grüne Fälle.

### Task 5: Abschluss des Blocks

**Files:**

- Modify: `docs/arbeitsjournal.md`
- Generate: `C:/Users/Nils/OneDrive/PSMobile/PSMobile-YYYY-MM-DD-advanced-stabilisierung.ipa`

- [ ] **Step 1: Vollständige iOS-Suite erneut ausführen**

Run: vollständiger `xcodebuild ... test`-Befehl ohne `test-without-building`.

Expected: Jede Klasse hat ausgeführte Tests, kein Fehler und kein hängenbleibender Selbsttest.

- [ ] **Step 2: Gerätetaugliche IPA erzeugen**

Run: `bash build/scripts/package-ipa.sh` auf dem Mac und Kopie in den OneDrive-Auslieferungsordner mit Datum und Blockname.

- [ ] **Step 3: Journal, Commit und GitHub-Push**

Das Journal nennt Tests, offene Hardware-Risiken und IPA-Datei. Danach nur die Dateien dieses Blocks committen und den Branch nach `NilsN3DP/PSMobile` pushen.
