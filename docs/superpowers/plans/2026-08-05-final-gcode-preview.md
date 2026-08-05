# Final G-Code Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die iOS-Vorschau rendert und erklärt ausschließlich PrusaSlicers final verarbeitetes G-Code-Ergebnis.

**Architecture:** Der Slice-Worker veröffentlicht einen revisionstreuen `GCodeProcessorResult`; Core-C-ABI und libvgcode-Viewport lesen denselben Snapshot. Swift zeigt dessen echte Layer, Rollen, Extruder und Kennzahlen in einer responsiven Preview-Karte.

**Tech Stack:** C++17, PrusaSlicer `GCodeProcessorResult`, libvgcode, C-ABI, SwiftUI, XCTest.

## Global Constraints

- Kein selbst erfundener G-Code-Parser und kein `Print`-Fallback.
- Stale-Snapshots sind nach jeder neuen Designrevision deaktiviert.
- Kommentare in Produktionscode und Tests sind deutsch.
- Implementierungsreihenfolge: Core, Viewport, iPad/iPhone-UI.
- Abschluss mit Task-only-Commit und `preview-final-report.md`.

---

### Task 1: Finalen Preview-Core-Vertrag veröffentlichen

**Files:**
- Modify: `core/test/psm_contract_tests.cpp`
- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_session.hpp`
- Modify: `core/src/psmobile_core.cpp`

**Interfaces:**
- Produces: `psm_preview_snapshot_get`, `psm_preview_layer_at`,
  `psm_preview_extruder_at`, `psm_preview_role_at`.

- [ ] Failing contract tests für leeren, finalen und stale Snapshot schreiben.
- [ ] Native Tests ausführen und den erwarteten API-/Vertragsfehler prüfen.
- [ ] Versionierte C-Strukturen und session-lokalen finalen Processor-Snapshot ergänzen.
- [ ] Snapshot aus finalen Moves und Statistiken ableiten und revisionstreu veröffentlichen.
- [ ] Native Tests erneut ausführen.

### Task 2: Viewport an finalen Snapshot und Filter binden

**Files:**
- Modify: `core/test/psm_contract_tests.cpp`
- Modify: `viewport/include/psm_viewport.h`
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `ios/PSMobile/Viewport/PsmViewport.swift`
- Modify: `ios/PSMobile/Viewport/ViewportView.swift`

**Interfaces:**
- Consumes: session-lokaler finaler `GCodeProcessorResult`.
- Produces: `psm_viewport_set_preview_view`,
  `psm_viewport_set_role_visible`, `psm_viewport_set_extruder_visible`,
  beidseitiger `setLayerRange`.

- [ ] Failing Range-/Filter-Vertragstests schreiben.
- [ ] Native Tests ausführen und den erwarteten Fehler prüfen.
- [ ] `libvgcode::convert(result, ...)` ohne `Print`-Fallback anbinden.
- [ ] Echte Farben, Rollen-/Extruderansicht und Filter an `Viewer` delegieren.
- [ ] Viewport- und Swift-Brücke ergänzen und Tests erneut ausführen.

### Task 3: Responsive Preview-Bedienung

**Files:**
- Create: `ios/PSMobile/Screens/FinalPreviewPanel.swift`
- Create: `ios/PSMobileTests/PreviewRangeTests.swift`
- Modify: `ios/PSMobile/Core/PsmCore.swift`
- Modify: `ios/PSMobile/SlicerModel.swift`
- Modify: `ios/PSMobile/Screens/AdvancedWorkspaceView.swift`
- Modify: `ios/PSMobile/Screens/SimpleModeView.swift`
- Modify: `ios/PSMobileUITests/PreviewUITests.swift`

**Interfaces:**
- Consumes: `PsmCore.PreviewSnapshot` und `PsmViewport`-Filter.
- Produces: `PreviewRange`, iPad-Seitenkarte, iPhone-Bottom-Sheet.

- [ ] Failing Swift-Range- und UI-Tests für Filter, Stats, Formfaktor und Editorweg schreiben.
- [ ] Tests auf dem Mac ausführen und erwartete Fehlschläge prüfen.
- [ ] Snapshot-Brücke, touchfähigen Doppelregler und gemeinsame Preview-Karte implementieren.
- [ ] Karte in Simple/Advanced responsiv einbetten und Preview bei stale Snapshot schließen.
- [ ] Swift-/UI-Tests erneut ausführen.

### Task 4: Verifikation und Bericht

**Files:**
- Create: `.superpowers/sdd/2026-08-05-ios-advanced-stabilisierung/preview-final-report.md`

- [ ] Core-Vertrag, iOS-Build, Unit- und UI-Tests frisch ausführen.
- [ ] Diff auf Task-Scope und fehlende Ersatzwerte/`Print`-Fallback prüfen.
- [ ] Ergebnisse und eventuelle Infrastrukturgrenzen im Bericht festhalten.
- [ ] Nur Preview-Dateien committen.
