# Android Project Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Android-Projektablauf um sicheres 3MF-Speichern, gruppiertes Undo/Redo und einen touchfähigen Objekt-/Volumenbaum mit Extruderzuweisung erweitern.

**Architecture:** Die C-ABI bleibt die fachliche Grenze. Der Core hält mobile Betten getrennt und übersetzt sie nur für 3MF-Dateien in PrusaSlicers virtuelle Bettlandschaft. Historieneinträge bestehen aus begrenzten Modell-/Bett-Snapshots; Android gruppiert Gesten und mehrteilige Aktionen. Compose zeigt diese Funktionen direkt über dem Bett und in der Seitenleiste.

**Tech Stack:** C++17, PrusaSlicer/libslic3r 2.9.6, C-ABI/JNI, Kotlin 2.1, Jetpack Compose, Android Storage Access Framework, OpenGL ES, x86_64-Android-Emulator.

## Global Constraints

- Android wird zuerst fertiggestellt; iOS beginnt erst nach dem Android-Gerätegate.
- Mehrbett bleibt eine direkte Auswahl einzelner Betten und keine scrollbare Desktop-Bettlandschaft.
- Eine importierte 3MF fragt zwischen reiner Geometrie und vollständigem Projekt.
- Vollständiger Projektimport wählt die eingebettete Druckerkonfiguration automatisch.
- Exportierte Projekte enthalten weder Druckerzugänge noch ausführbare Post-Processing-Skripte.
- Ein laufender Slice arbeitet weiter auf seinem unveränderlichen Snapshot; Undo/Redo selbst ist währenddessen gesperrt.
- Die bereits vorhandenen uncommitted Nutzeränderungen werden weder verworfen noch ungefragt gemeinsam committed.

---

### Task 1: Vollständigen Mehrbett-3MF-Roundtrip bauen

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Consumes: `psm_session::bed_models`, `psm_session::config`, `Slic3r::s_multiple_beds`
- Produces: `psm_result psm_project_save_3mf(psm_session*, const char*)`, `PsmCore.saveProject(path: String)`

- [x] **Step 1: Fehlschlagenden Roundtrip-Vertrag definieren**

```cpp
require(psm_project_save_3mf(session, roundtrip.c_str()) == PSM_OK,
        "save 3mf roundtrip");
require(psm_project_load_3mf(reopened, roundtrip.c_str(), &info) == PSM_OK,
        "reopen 3mf roundtrip");
require(info.bed_count == 2 &&
        psm_bed_object_count(reopened, 0) == 1 &&
        psm_bed_object_count(reopened, 1) == 1,
        "roundtrip retains direct bed membership");
```

- [x] **Step 2: Mobile Bettkoordinaten für den Export zusammenführen**

`merge_project_beds(psm_session*)` kopiert Objekte und Materialien,
verschiebt jede Instanz mit `get_bed_translation(bed_index)` und trägt
die Instanz über `set_instance_bed` in PrusaSlicers Mehrbettkarte ein.
Das sichtbare Session-Modell bleibt unverändert.

- [x] **Step 3: Projektkonfiguration sicher speichern**

```cpp
Slic3r::DynamicPrintConfig export_config = s->config;
export_config.erase("print_host");
export_config.erase("printhost_apikey");
export_config.erase("printhost_cafile");
if (auto *post = export_config.option<Slic3r::ConfigOptionStrings>(
        "post_process", false);
    post != nullptr)
    post->values.clear();
```

- [x] **Step 4: Roundtrip auf Android ausführen**

Run:

```bash
adb shell "LD_LIBRARY_PATH=/data/local/tmp/psmobile-tests-x86_64 \
  /data/local/tmp/psmobile-tests-x86_64/psm_contract_tests \
  /data/local/tmp/psmobile-tests-x86_64/data \
  /data/local/tmp/psmobile-tests-x86_64/resources \
  /data/local/tmp/psmobile-tests-x86_64/wuerfel20.stl \
  /data/local/tmp/psmobile-tests-x86_64/psmobile-multibed.3mf"
```

Expected: `PASS: psm_contract_tests`.

---

### Task 2: Gruppiertes Undo und Redo im Core verankern

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_session.hpp`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `android/app/src/main/java/de/psmobile/ui/SceneView.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Produces: `psm_history_begin/end`, `psm_history_undo/redo`, Zähler und Beschriftungen
- Android mapping: `PsmCore.HistoryState`, `SlicerService.undo()`, `SlicerService.redo()`

- [x] **Step 1: Gruppierungsvertrag testen**

```cpp
psm_history_clear(session);
psm_history_begin(session, "Kombinierte Transformation");
psm_model_set_position(session, id, x + 5.f, y, z);
psm_model_set_rotation(session, id, 0.f, 0.f, half_pi);
psm_history_end(session);
require(psm_history_undo_count(session) == 1,
        "grouped actions create one undo entry");
```

- [x] **Step 2: Begrenzte Snapshots implementieren**

`HistorySnapshot` kopiert alle mobilen `Model`-Betten, aktives Bett und
Beschriftung. `HISTORY_LIMIT` ist 20. Eine neue Änderung leert Redo;
Projektimport beginnt mit einer leeren Historie.

- [x] **Step 3: Alle Modellmutationen vor der Änderung checkpointen**

Abgedeckt sind Import, Neu, Bett anlegen/entfernen/leeren, Bettwechsel
eines Objekts, Entfernen, Transformieren, Spiegeln, Instanzen,
Duplizieren, Anordnen sowie Objekt-/Volumenextruder.

- [x] **Step 4: Touch-Gesten gruppieren**

`SceneView` öffnet bei `ACTION_DOWN` eine Transaktion und beendet sie
bei `ACTION_UP` oder `ACTION_CANCEL`. Viewport-Mutationen lösen darin
höchstens einen Snapshot aus; reine Kameragesten erzeugen keinen.

- [x] **Step 5: Werkzeugleiste im Emulator prüfen**

Expected sequence: Duplizieren ändert `Bett 1 · 1` auf `Bett 1 · 2`,
Undo zurück auf `Bett 1 · 1`, Redo wieder auf `Bett 1 · 2`.

---

### Task 3: Objekt-/Volumenbaum und Teil-Extruder bereitstellen

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Produces: `psm_model_volume_count`, `psm_model_volume_info`,
  `psm_model_extruder_get/set`, `psm_model_volume_extruder_set`
- Android models: `PsmCore.VolumeType`, `PsmCore.VolumeInfo`

- [x] **Step 1: Volumenvertrag testen**

```cpp
require(psm_model_volume_count(session, id) == 1, "one model volume");
psm_volume_info volume{};
require(psm_model_volume_info(session, id, 0, &volume) == PSM_OK &&
        volume.type == PSM_VOLUME_MODEL_PART &&
        volume.triangle_count == 12,
        "volume metadata");
```

- [x] **Step 2: Extruder-Vererbung implementieren**

Wert `0` entfernt die lokale Option und bedeutet „Standard“ am Objekt
beziehungsweise „Vom Objekt“ am Volumen. Werte `1..N` sind explizite
Extruder. Nicht druckbare Modifier lehnen eine Extruderzuweisung ab.

- [x] **Step 3: Touchfähigen Baum rendern**

Die Objektzeile zeigt Name, Größe, Volumenzahl und optionalen
Objektextruder. Aufgeklappte Volumen zeigen Typ, Dreieckszahl und bei
druckbaren Teilen einen Extruder-Picker.

- [x] **Step 4: MMU3-Ablauf im Emulator prüfen**

Expected: Bei fünf Extrudern zeigt das Menü `Vom Objekt` und
`Extruder 1` bis `Extruder 5`; die Auswahl von Extruder 3 erscheint
anschließend als `E3` am Modellteil.

---

### Task 4: Projektaktionen über Android SAF zugänglich machen

**Files:**

- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`

**Interfaces:**

- Consumes: `PsmCore.saveProject(path)`
- Produces: `SlicerService.saveProjectFile()`, `CreateDocument("model/3mf")`

- [x] **Step 1: Projektleiste hinzufügen**

Die feste Leiste enthält `Neu`, `Speichern` und `Speichern unter`.
`Neu` zeigt bei Projektinhalt eine Verlustwarnung. Während eines
laufenden Slice sind die Aktionen deaktiviert.

- [x] **Step 2: Core-Datei über SAF streamen**

```kotlin
val source = svc.saveProjectFile()
contentResolver.openOutputStream(uri, "wt")!!.use { output ->
    source.inputStream().use { input -> input.copyTo(output) }
}
```

- [x] **Step 3: Reale Datei im Emulator prüfen**

Expected: DocumentsUI schreibt `PSMobile Test Cube.3mf`; nach
`Neu` und erneutem Projektimport erscheinen `Bett 1 · 2` und
`Bett 2 · 1`.

---

### Task 5: Build, Evidenz und APK abschließen

**Files:**

- Modify: `docs/feature-matrix.json`
- Modify: `docs/03-roadmap.md`
- Modify: `docs/07-stopp-punkte.md`
- Modify: `docs/09-fehlerliste.md`
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `docs/release/android-v1-gate.md`

- [x] **Step 1: Beide Android-ABIs bauen und fingerprinten**

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
ANDROID_ABI=arm64-v8a build/scripts/build-core.sh
build/scripts/stage-native.sh
```

Expected: beide ABIs werden ohne veraltete Bibliothek gestaged.

- [x] **Step 2: Android-Tests und APK bauen**

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: `BUILD SUCCESSFUL`; APK enthält arm64-v8a und x86_64.

- [x] **Step 3: Statusmatrix validieren**

```bash
python build/scripts/feature-report.py --check
python -m unittest discover -s build/scripts/tests -p "test_*.py"
```

Expected: Matrix und Reporttests bestehen.

- [ ] **Step 4: Physische Android-Geräte prüfen**

Auf einem arm64-Gerät mit 4–6 GB und einem mit mindestens 8 GB:
Projekt importieren, Bett wechseln, Volumen E3 zuweisen, speichern,
App schließen, wieder öffnen, Undo/Redo vor dem Speichern prüfen und
ein großes Modell slicen. Ergebnis mit Android-Version, RAM,
Native-Fingerprint und maximalem Speicher in
`docs/release/android-v1-gate.md` eintragen.

- [ ] **Step 5: Druckerhardware prüfen**

Je einen API-Key- und Digest-PrusaLink-Drucker testen: Probe, explizite
HTTP-Freigabe, Uploadziel bei zwei Druckern und tatsächlich
angekommener Dateiname. Erst danach die Matrix auf `hardware_tested`
setzen.
