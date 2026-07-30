# Android Desktop-Parität ohne ZIP-Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alle für Android vorgesehenen, im Funktionsvergleich noch offenen PrusaSlicer-Abläufe außer ZIP-Import in einem touch- und tabletfreundlichen Beta-Build bereitstellen und automatisiert prüfen.

**Architecture:** Fachliche Änderungen bleiben in der C-ABI über `libslic3r`; JNI überträgt nur kleine Werte und Android-Dateiabläufe laufen über das Storage Access Framework. Der Viewport liefert exakte Flächentreffer für Flachlegen, Bemalen und Messen. Compose trennt Auswahl, Geometriewerkzeuge, Oberflächenwerkzeuge und Projekteinstellungen in kleine, touchfähige Bedienflächen.

**Tech Stack:** C++17, PrusaSlicer/libslic3r 2.9.6, C-ABI/JNI, Kotlin 2.1, Jetpack Compose, OpenGL ES, Android Storage Access Framework, x86_64-Emulator und arm64-v8a-APK.

## Global Constraints

- Android wird zuerst fertiggestellt; iOS beginnt erst nach einem stabilen Android-Gerätegate.
- ZIP-Archivimport ist ausdrücklich nicht Bestandteil dieses Pakets.
- Mehrbett bleibt eine direkte Auswahl und keine scrollbare Desktop-Bettlandschaft.
- Eine 3MF fragt weiterhin zwischen Geometrie- und Projektimport; Projektimport wählt die passende Druckerkonfiguration.
- Touch-Ziele sind mindestens 48 dp groß; Tablet-Ansichten halten Viewport und Inspektor gleichzeitig sichtbar.
- Bestehende Nutzeränderungen im Arbeitsbaum werden weder verworfen noch ungefragt committed.
- PrusaLink-Hardware und Prusa Connect OAuth werden nur dann als geprüft markiert, wenn echte Hardware beziehungsweise Zugangsdaten verfügbar sind.

---

### Task 1: C-ABI für offene Modell- und Dateiwerkzeuge

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Produces: `psm_model_split_objects`, `psm_model_split_volumes`, `psm_model_cut_z`, `psm_model_simplify`
- Produces: Primitive-, Modifier-, Mal-, variable Schichthöhen-, Custom-G-Code- und Wipe-Tower-Funktionen
- Produces: STL/OBJ-Export, STL-Reparatur sowie ASCII-/Binär-G-Code-Konvertierung

- [x] **Step 1: ABI-Signaturen und Kotlin-Wrapper ergänzen**

```kotlin
fun cutObject(id: Int, z: Float, keepUpper: Boolean, keepLower: Boolean): IntArray =
    nativeCutObject(handle, id, z, keepUpper, keepLower)
```

- [x] **Step 2: Funktionen mit den vorhandenen libslic3r-Algorithmen implementieren**

```cpp
Slic3r::its_quadric_edge_collapse(mesh.its, target_triangle_count);
Slic3r::store_stl(path, merged_mesh, true);
```

- [ ] **Step 3: Vertragstests für jede Mutation und jeden Dateityp schreiben**

```cpp
require(psm_model_cut_z(session, id, 10.f, 1, 1, ids, 8, &count) == PSM_OK &&
        count == 2, "cut keeps upper and lower half");
require(psm_export_plate_stl(session, stl.c_str()) == PSM_OK,
        "plate STL export");
```

- [ ] **Step 4: x86_64-Native-Build ausführen**

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
```

Expected: `psmobile_core`, `psm_contract_tests` und `psm_testcli` werden ohne Compiler- oder Linkfehler erzeugt.

---

### Task 2: Exakte Flächentreffer und Mehrfachauswahl

**Files:**

- Modify: `viewport/include/psm_viewport.h`
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmViewport.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SceneView.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/SelectionStateTest.kt`

**Interfaces:**

- Produces: `PsmViewport.setSelections(ids, primaryId)`
- Produces: `PsmViewport.surfacePick(x, y): SurfaceHit?`
- Produces: stabile `selectedIds`- und `primaryId`-Zustände über Bettwechsel und Objektmutationen

- [x] **Step 1: Viewport-C-ABI für mehrere Markierungen und Dreieckstreffer ergänzen**

```cpp
psm_surface_hit hit{};
require(psm_viewport_pick_surface(viewport, x, y, &hit) == PSM_OK,
        "surface pick returns transformed facet");
```

- [ ] **Step 2: Kotlin-Viewport-Brücke und SceneView aktualisieren**

```kotlin
viewport.setSelections(selectedIds, primaryId)
val hit = viewport.surfacePick(x, y)
```

- [ ] **Step 3: Suche, Langdruck-Mehrfachauswahl und Auswahlbefehle bauen**

```kotlin
val visible = objects.filter { query.isBlank() ||
    it.name.contains(query, ignoreCase = true) }
```

- [ ] **Step 4: Sammelaktionen als einen Undo-Schritt ausführen**

```kotlin
core.beginHistory("Auswahl löschen")
try { ids.forEach(core::removeObject) } finally { core.endHistory() }
```

- [ ] **Step 5: Auswahlzustand mit JVM-Tests prüfen**

Run:

```bash
./gradlew testDebugUnitTest --tests "*SelectionStateTest"
```

Expected: Auswahl bleibt nach Suche konsistent; gelöschte IDs verschwinden; der Primärwert fällt auf eine vorhandene ID zurück.

---

### Task 3: Geometrie, Modifier und variable Schichthöhen

**Files:**

- Create: `android/app/src/main/java/de/psmobile/ui/GeometryTools.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Consumes: die Modellfunktionen aus Task 1 und Flächentreffer aus Task 2
- Produces: Schneiden, Vereinfachen, Teile/Volumen trennen, Fläche flachlegen
- Produces: Negativvolumen, Modifier, Support-Blocker und Support-Erzwinger
- Produces: editierbares Profil `(z, layerHeight)` pro Objekt

- [ ] **Step 1: Werkzeugdialoge mit validierten Zahlenwerten erstellen**

```kotlin
require(cutHeight.isFinite() && cutHeight in minZ..maxZ)
require(targetPercent in 1f..100f)
```

- [ ] **Step 2: Flachlegen als Oberflächenmodus anbinden**

```kotlin
surfaceHit?.let { service.layOnFacet(it.objectId, it.nx, it.ny, it.nz) }
```

- [ ] **Step 3: Modifier-Auswahl über große Typkarten anbieten**

```kotlin
service.addPrimitiveVolume(id, Primitive.BOX, VolumeRole.SUPPORT_BLOCKER,
    width, depth, height)
```

- [ ] **Step 4: Schichthöhenprofil sortiert und begrenzt speichern**

```kotlin
val points = rows.map { it.z to it.height }.sortedBy { it.first }
service.setLayerProfile(id, points)
```

- [ ] **Step 5: Geometriewerkzeuge im Core-Vertragstest ausführen**

Expected: jede Aktion verändert Dreiecks-/Objekt-/Volumenzahlen korrekt und Undo stellt den Ausgangszustand wieder her.

---

### Task 4: Bemalen, Messen, Text und SVG

**Files:**

- Create: `android/app/src/main/java/de/psmobile/ui/SurfaceTools.kt`
- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SceneView.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Produces: Supports-, Naht-, Fuzzy-Skin- und MMU-Facettenmarkierung
- Produces: Distanzmessung zwischen zwei transformierten Oberflächenpunkten
- Produces: Text- und SVG-Geometrie als druckbares, negatives oder Modifier-Volumen

- [ ] **Step 1: Malmodus mit Typ, Wert und Touch-Radius anbinden**

```kotlin
service.paintFacet(hit.objectId, hit.volumeIndex, hit.facetIndex,
    paintKind, paintValue)
```

- [ ] **Step 2: Löschen und vollständiges Zurücksetzen je Maltyp anbieten**

```kotlin
service.clearPaint(primaryId, PaintKind.SEAM)
```

- [ ] **Step 3: Zwei-Punkt-Messung ohne Modellmutation implementieren**

```kotlin
val distance = sqrt((b.x-a.x).pow(2) + (b.y-a.y).pow(2) + (b.z-a.z).pow(2))
```

- [ ] **Step 4: Text über eine mitgelieferte Schrift in Mesh-Konturen umwandeln**

```cpp
return psm_model_add_text_volume(session, id, utf8, font_path,
                                 size_mm, depth_mm, role, out_index);
```

- [ ] **Step 5: SVG über SAF auswählen und als geprägtes Volumen einfügen**

```kotlin
registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(onSvgSelected)
}
```

- [ ] **Step 6: Projekt-Roundtrip der vier Annotationstypen und Prägevorgaben testen**

Expected: Speichern und erneutes Öffnen der 3MF erhält Facettenmarkierungen und hinzugefügte Volumen.

---

### Task 5: Spezialdialoge und Objekt-Multicolor

**Files:**

- Create: `android/app/src/main/java/de/psmobile/ui/SpecialSettingsDialogs.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `android/app/src/test/java/de/psmobile/ui/SpecialValueCodecTest.kt`

**Interfaces:**

- Produces: Bettform/-textur/-modell, Wischvolumenmatrix, Ramming, G-Code-Ersetzungen und Profilkompatibilitäten
- Produces: eigene Objektfarbe, `wipe_into_infill` und `wipe_into_objects`
- Produces: Custom-G-Code je Höhe und Wipe-Tower-Position/-Drehung

- [ ] **Step 1: Strukturwerte aus PrusaSlicer-Serialisierung lesen und schreiben**

```kotlin
service.setConfig("wiping_volumes_matrix", matrix.joinToString(","))
service.setConfig("gcode_substitutions", substitutions.flatten().joinToString("\n"))
```

- [ ] **Step 2: Bettpunkte auf einem touchfähigen Raster editieren**

```kotlin
require(points.size >= 3 && polygonArea(points) > 0.0)
```

- [ ] **Step 3: Wischmatrix und Ramming tabellarisch bearbeiten**

```kotlin
repeat(extruders) { from ->
    repeat(extruders) { to -> WipeCell(from, to, values[from * extruders + to]) }
}
```

- [ ] **Step 4: Custom-G-Code und Wipe-Tower-Position im Projektbereich anbieten**

```kotlin
service.setCustomGcodes(rows.sortedBy { it.printZ })
service.setWipeTower(x, y, rotationDegrees)
```

- [ ] **Step 5: Objektfarbe und Wischoptionen im Objektbaum anbieten**

Expected: Farbe und beide Schalter überleben Undo, 3MF-Speichern und erneuten Projektimport.

---

### Task 6: Dateiwerkzeuge ohne ZIP

**Files:**

- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/FileToolsDialog.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Test: `core/test/psm_contract_tests.cpp`

**Interfaces:**

- Produces: aktives Bett als STL oder OBJ über `CreateDocument`
- Produces: reparierte STL über Quell- und Ziel-Dokument
- Produces: G-Code ↔ BGCode über Quell- und Ziel-Dokument
- Excludes: ZIP-Import

- [ ] **Step 1: STL-/OBJ-Bettexport mit Dateitypauswahl erstellen**

```kotlin
val export = service.exportPlate(FileFormat.OBJ)
copyToDocument(export, outputUri)
```

- [ ] **Step 2: STL-Reparatur als expliziten Quell-Ziel-Ablauf erstellen**

```kotlin
service.repairStl(inputFile, outputFile)
```

- [ ] **Step 3: G-Code-Konvertierung anhand erkannter Eingabe anbieten**

```kotlin
val target = if (isBinaryGcode(source)) GcodeFormat.ASCII else GcodeFormat.BINARY
```

- [ ] **Step 4: Dateifehler ohne App-Abbruch anzeigen**

Expected: ungültige oder unlesbare Eingaben erzeugen eine Snackbar und hinterlassen Projekt und letzte Slice-Ausgabe unverändert.

---

### Task 7: Stabilitäts-, Emulator- und Release-Gate

**Files:**

- Modify: `docs/feature-matrix.json`
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `docs/release/android-v1-gate.md`
- Modify: `README.md`

**Interfaces:**

- Consumes: alle vorherigen Tasks
- Produces: x86_64-Emulatorbeleg, arm64-v8a-Bibliothek und installierbares Beta-APK

- [ ] **Step 1: Native Vertragstests auf dem Emulator ausführen**

```bash
adb shell /data/local/tmp/psmobile-tests-x86_64/psm_contract_tests
```

Expected: `PASS: psm_contract_tests`.

- [ ] **Step 2: JVM-Tests, Lint und Debug-APK bauen**

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Tablet-Smoke-Test automatisieren**

```bash
adb shell am instrument -w de.psmobile.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: Import, Mehrfachauswahl, Geometriewerkzeuge, Malen, Speichern, Export, Slice, Abbruch und Vorschau laufen ohne Prozessabsturz.

- [ ] **Step 4: Wiederholungs- und Hintergrundtest durchführen**

Expected: 20 Slice-Zyklen, Rotation während Slice, Notification-Abbruch und erneuter Slice bestehen; Logcat enthält keinen nativen Crash oder `FATAL EXCEPTION`.

- [ ] **Step 5: arm64-v8a bauen, beide ABIs stagen und Matrix aktualisieren**

```bash
ANDROID_ABI=arm64-v8a build/scripts/build-core.sh
build/scripts/stage-native.sh
python build/scripts/feature-report.py --check
```

Expected: APK enthält x86_64 und arm64-v8a; nur reale Hardware-/OAuth-Gates bleiben als extern offen markiert.
