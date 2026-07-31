# Beta-RC: Easy/Advanced UI und Desktop-Parität – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Einen testbaren Android-Beta-RC mit EasyPrint-ähnlichem Easy Mode, besser erreichbarem Advanced Mode, wiederaufnehmbarem Advanced-Assistenten, persistenten Plattensperren und priorisierten grundlegenden Desktop-Funktionen liefern.

**Architecture:** Easy und Advanced teilen dieselbe `SlicerService`-Session, native Profilauflösung und Projekt-/Slice-Revision. Der Easy Mode ist eine kuratierte Compose-Oberfläche; Advanced behält die bestehende Screen-Struktur und erhält nur gemeinsame Navigation, Suche, Touch- und Wizard-Komponenten. Plattensperren liegen in der mobilen Projektmetadatenebene und werden beim 3MF-Roundtrip erhalten.

**Tech Stack:** Kotlin 2.x, Jetpack Compose Material 3, Android SAF, Kotlin Coroutines, C++ PrusaSlicer/libslic3r, C-ABI/JNI, Gradle, x86_64-Emulator und ARM64-Gerät.

## Global Constraints

- Android zuerst; iOS bleibt nach dem stabilen Android-Gate.
- ZIP-Import bleibt ausgeschlossen.
- Printer wird als Modell geführt; Nozzle wird in der Slice-Übersicht gewählt.
- Easy Mode zeigt nur eine kuratierte Einstellungs-Whitelist.
- Advanced behält seine bestehende Informationsarchitektur.
- Jeder neue Zustand erhält einen reproduzierbaren Unit-, Core- oder UI-Test.
- Kein alter G-Code darf nach Projekt-, Profil-, Düsenauswahl oder Plattenänderung als aktuell gelten.

---

### Task 1: RC-Grundlage und Planstatus

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/AppMode.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/AppModeTest.kt`

**Interfaces:**
- Produces `enum class AppMode { EASY, ADVANCED }` and a persisted mode preference.
- `SlicerService.Screen` gains explicit start/mode/wizard screens without duplicating the native session.

- [x] **Step 1: Write failing mode tests** for default mode, persisted mode and explicit start actions.
- [x] **Step 2: Run `:app:testDebugUnitTest --tests '*AppModeTest'` and observe the expected missing-type failure.**
- [x] **Step 3: Implement `AppMode`, preference storage and a start router with the three actions Easy Mode, Advanced Mode and Advanced-Assistent.**
- [x] **Step 4: Add the mode switch to the existing UI without changing the existing Advanced screen content.**
- [x] **Step 5: Run the focused test and then `:app:testDebugUnitTest`.**

### Task 2: Persistente Plattensperre

**Files:**
- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_session.hpp`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `core/test/psm_contract_tests.cpp`
- Test: `android/app/src/test/java/de/psmobile/ui/BedLockTest.kt`

**Interfaces:**
- Adds `psm_bed_locked_get`, `psm_bed_locked_set` and matching Kotlin methods.
- A locked bed rejects mutation with `PSM_ERR_BUSY` and a localized error.

- [ ] **Step 1: Add failing Core contract assertions for lock/unlock and mutation rejection.**
- [ ] **Step 2: Run the contract test and confirm the missing ABI symbols fail.**
- [ ] **Step 3: Add the per-bed lock state and C ABI/JNI bridge.**
- [x] **Step 4: Guard add/remove/clear/move/object mutation/arrange paths and expose lock state to Compose.**
- [x] **Step 5: Add lock icon, disabled actions and unlock affordance to bed chips and the active-bed header.**
- [ ] **Step 6: Extend the mobile 3MF metadata roundtrip and test reopening preserves locks.**
- [x] **Step 7: Run native contract and JVM tests.**

### Task 3: Advanced-Assistent und Profil-Suche

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/ProfileSearch.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/AdvancedWizardScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/ProfileSearchTest.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/AdvancedWizardStateTest.kt`

**Interfaces:**
- `ProfileSearch.filter(query, selectedType, installedOnly)` returns deterministic profile rows.
- Wizard state stores current step, printer model, nozzle suggestion, filament and print settings per project.

- [ ] **Step 1: Write failing pure tests for profile filtering, compatibility and wizard resume.**
- [ ] **Step 2: Run focused tests and confirm failures.**
- [ ] **Step 3: Implement search/filter and a resumable wizard state in the service.**
- [ ] **Step 4: Implement Printer → Filament → Print Settings wizard pages; profile loading uses existing preset APIs.**
- [ ] **Step 5: Add entry points from start, Advanced overview, Printer/Filament/Print Settings and warnings.**
- [ ] **Step 6: Put nozzle selection in the slice summary and map it to the internal printer variant; PrusaLink remains a suggestion.**
- [ ] **Step 7: Run focused and full JVM tests.**

### Task 4: EasyPrint-ähnlicher Easy Mode

**Files:**
- Create: `android/app/src/main/java/de/psmobile/ui/EasyModeScreen.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/EasyModeState.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/theme/Theme.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/EasyModeStateTest.kt`

**Interfaces:**
- Easy Mode consumes the existing `SlicerService` flows and never creates a second core/session.
- Easy settings whitelist: Printer model, Nozzle, Filament, Print Settings, Supports, Adhesion, Arrange, Preview and Print.

- [ ] **Step 1: Add failing state tests for required selections, missing-profile warnings and print readiness.**
- [ ] **Step 2: Run focused tests and confirm failures.**
- [ ] **Step 3: Implement the Easy Mode header, project list, printer cards, material search, support/adhesion cards, print settings cards and preview/print actions.**
- [ ] **Step 4: Use full-screen pages on phones and width-constrained panels on tablets; keep all primary controls at least 48 dp.**
- [ ] **Step 5: Add a visible “Advanced öffnen” action and preserve project/mode state.**
- [ ] **Step 6: Run JVM tests and a Compose compile.**

### Task 5: Advanced-Erreichbarkeit und Touch-Politur

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/ObjectPanel.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/GeometryTools.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/ProjectTools.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/AdvancedNavigationTest.kt`

**Interfaces:**
- Advanced keeps its inspector model but adds direct Printer, Filament, Print Settings and Wizard navigation.

- [ ] **Step 1: Add failing reducer/navigation tests for direct sections, back/close and warning-to-wizard routing.**
- [ ] **Step 2: Run focused tests and confirm failures.**
- [ ] **Step 3: Add sticky section actions, consistent back/close buttons, search affordances and larger hit targets.**
- [ ] **Step 4: Make object selection actions (all/none/delete/copy/paste) visible and add clear selected-count feedback.**
- [ ] **Step 5: Validate portrait phone, landscape tablet and touch-only operation with UI dumps/screenshots.**

### Task 6: Desktop-Grundfunktionen für den RC

**Files:**
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/ProjectTools.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/GeometryTools.kt`
- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `core/test/psm_contract_tests.cpp`

**Interfaces:**
- Add G-code open/preview path separate from sliced-result state.
- Add object visibility/lock and group/ungroup APIs if the current model supports them without a destructive conversion.
- Arrange receives explicit gap/rotation settings; unsupported desktop-only menu commands stay excluded.

- [ ] **Step 1: Add failing contract tests for G-code preview import, object visibility/lock and arrange gap.**
- [ ] **Step 2: Run the contract tests and confirm the new paths fail.**
- [ ] **Step 3: Implement the smallest native/JNI bridge using existing PrusaSlicer model state.**
- [ ] **Step 4: Add UI actions and clear stale-result invalidation.**
- [ ] **Step 5: Run native tests and verify an imported G-code preview cannot be sent as a stale project result.**

### Task 7: Dokumentation, Matrix und RC-Verifikation

**Files:**
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `docs/feature-matrix.json`
- Modify: `docs/release/android-v1-gate.md`
- Modify: `README.md`
- Modify: `build/scripts/gap-report.py`
- Test: `build/scripts/tests/test_feature_reports.py`

- [ ] **Step 1: Update the gap classifier for implemented model tools, plate export, repair, conversion, selection and special dialogs.**
- [ ] **Step 2: Add matrix entries/evidence for Easy Mode, Advanced Wizard and bed locks.**
- [ ] **Step 3: Document explicitly excluded ZIP/STEP/SLA and remaining hardware gates.**
- [ ] **Step 4: Run Python report tests and regenerate the parity report.**
- [ ] **Step 5: Build arm64-v8a and x86_64 native libraries, stage both, and run the full contract test on the emulator.**
- [ ] **Step 6: Run `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and install the APK.**
- [ ] **Step 7: Execute UI smoke paths: start mode choice, Easy printer/material search, Advanced wizard, nozzle selection, bed lock persistence, 3MF import modes, slice/preview/export, cancel and rotation.**
- [ ] **Step 8: Produce `PSMobile-android-beta-rc.apk`, SHA-256, test log and a list of unresolved hardware-only gates.**
