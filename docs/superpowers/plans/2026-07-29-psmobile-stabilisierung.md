# PSMobile Stabilisierung vor weiterem Feature-Ausbau Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den vorhandenen Android-Prototypen so stabilisieren, dass Modelländerungen, Slicing, Vorschau und Versand korrekt zusammengehören, reproduzierbar getestet werden und der dokumentierte Projektstand wieder dem ausführbaren Stand entspricht.

**Architecture:** Die öffentliche C-ABI bleibt die einzige fachliche Grenze. Rotationen bleiben dort in Radiant; plattformspezifische Adapter übersetzen explizit in Grad. Jede Änderung an Modell oder Konfiguration erhöht eine zentrale Design-Revision. Ein Slice-Job arbeitet auf einem unveränderlichen Snapshot aus Modell und Konfiguration; sein Ergebnis trägt die Quell-Revision und darf nur exportiert oder versendet werden, wenn es noch aktuell ist. Android bleibt in dieser Phase bewusst ein Prozess, bekommt aber einen korrekt gestarteten Foreground-Service. Ein separater Slicer-Prozess wird erst geplant, nachdem ein serialisierbarer Slice-Auftrag existiert.

**Tech Stack:** C++17, PrusaSlicer/libslic3r 2.9.6, C-ABI/JNI, Kotlin 2.1, Jetpack Compose, Android API 26–35, OpenGL ES/libvgcode, Python-`unittest`, CTest-kompatible native Vertragstests.

## Global Constraints

- PrusaSlicer bleibt auf `version_2.9.6` fixiert. Änderungen an extrahierter Logik müssen durch Generatoren reproduzierbar sein.
- Die Regeln aus `docs/entscheidungen.md` gelten weiter, insbesondere E-02/E-03 (C-ABI als Grenze), E-08 (mobile Abhängigkeiten), E-09 (Netzwerk in der Plattformschicht) und E-12 (Daten/Logik extrahieren, nicht abschreiben).
- FDM ist der v1-Umfang. SLA, STEP, vollständige Desktop-Menüparität und neue Spezial-Gizmos sind nicht Teil dieses Plans.
- Die vorhandenen uncommitted Änderungen an Core, JNI und Settings gehören dem Nutzer. Vor Ausführung dieses Plans müssen sie auf einem eigenen Commit oder Branch gesichert werden; sie dürfen nicht verworfen oder überschrieben werden.
- Eine Funktion gilt nicht allein wegen vorhandenen Codes als fertig. Zulässige Status sind `coded`, `built`, `emulator_tested`, `device_tested`, `hardware_tested`, `blocked` und `not_started`.
- Kein G-Code darf nach einer Modell- oder Konfigurationsänderung stillschweigend als aktuell gelten.
- Keine native Session darf gleichzeitig ungeschützt vom UI-, GL- und Slice-Thread gelesen oder verändert werden.
- Credentials dürfen weder im Klartext-JSON noch in Android Auto Backup landen.
- Jeder Task beginnt mit einem fehlschlagenden Test oder einem reproduzierbaren Prüfskript und endet mit einem fokussierten Commit.

---

## Audit Summary

### Was bereits gut umgesetzt ist

- Die Grundentscheidung, `libslic3r` wiederzuverwenden und keine Desktop-GUI zu portieren, ist richtig.
- Android besitzt bereits einen brauchbaren vertikalen Ablauf: Import, Profile, Transformieren, Anordnen, Slicing, G-Code-Vorschau, Export und PrusaLink-Code.
- Die C-ABI hält Kotlin weitgehend von PrusaSlicer-Typen fern.
- Profile, Übersetzungen, Bettmodelle und Teile der Einstellungslogik werden aus PrusaSlicer übernommen statt manuell nachgebaut.
- Die Dokumente halten Risiken und offene Punkte ungewöhnlich ehrlich fest. Das Problem ist nicht fehlende Dokumentation, sondern dass mehrere Statusangaben inzwischen auseinanderlaufen.

### Kritische Abweichungen zwischen Dokumentation und Code

| Priorität | Befund | Beleg | Auswirkung |
|---|---|---|---|
| P0 | Android behandelt Radiant als Grad | `core/include/psmobile_core.h`, `ObjectPanel.kt`, `SlicerService.kt` | Zahlenfelder und 90°-Tasten setzen falsche Rotationen |
| P0 | UI, GL-Thread und Slice-Thread teilen dasselbe mutable Modell | `psmobile_session.hpp`, `psmobile_core.cpp`, `psm_viewport.cpp` | Datenrennen, Abstürze oder nicht reproduzierbarer G-Code |
| P0 | Ein alter Slice bleibt nach Änderungen exportier- und versendbar | `SlicerService.kt` und fehlende Design-Revision | Falscher G-Code kann an den Drucker gehen |
| P0 | Neue Toggle-Implementierung definiert `ToggleCollector` zweimal unterschiedlich | `psmobile_toggles.cpp`, `generated/psm_toggles.cpp` | ODR-Verletzung und undefiniertes C++-Verhalten |
| P0 | Toggle-Cache wird bei Stringwerten und Presetwechseln nicht sicher invalidiert | `psmobile_core.cpp`, `psmobile_presets.cpp` | Einstellungen können falsch aktiv/gesperrt erscheinen |
| P1 | Der Slicer-Service ist nur gebunden und nicht als gestarteter Service modelliert | `MainActivity.kt`, `SlicerService.kt` | Beim Unbind endet die garantierte Lebensdauer |
| P1 | Der dokumentierte separate Slicer-Prozess existiert nicht | `AndroidManifest.xml` | Low-Memory-Killer betrifft UI und Slicer gemeinsam |
| P1 | PrusaLink nutzt standardmäßig HTTP, targetSdk 35 blockiert Cleartext standardmäßig | `PrusaLink.kt`, `AndroidManifest.xml` | Verbindung zu typischen lokalen Druckern kann scheitern |
| P1 | Drucker-Credentials liegen in normalen SharedPreferences und werden standardmäßig gesichert | `PrinterStore.kt`, `AndroidManifest.xml` | unnötiges Credential-Risiko |
| P1 | Versand nimmt immer den ersten Drucker | `SlicerScreen.kt` | falsches Ziel bei mehreren Druckern |
| P1 | Native/APK-Staging überspringt fehlende ABIs | `stage-native.sh` | alte `.so` kann in eine neue APK geraten |
| P1 | Es gibt keine Regressionstests außer einem Smoke-Test-CLI | `core/test/psm_testcli.c` | zentrale Verträge sind ungeschützt |
| P2 | `docs/10-funktionsvergleich.md` und `gap-report.py` melden veraltete Fähigkeiten | `HABEN_WIR_GIZMOS = set()` | Fortschritt und Lücken sind nicht zuverlässig |
| P2 | iOS ist ein ungebautes Gerüst; CMake erzwingt `SHARED`, EGL und Android-only Viewport | `build-ios.sh`, `CMakeLists.txt`, iOS-UI | keine reale Plattformparität |
| P2 | Versprochene Display-Dezimierung und harte Speicherstrategie fehlen | Header-Kommentar und tatsächlicher Modellpfad | große Modelle bleiben ein ungemessener Risikobereich |

### Produktentscheidung für diesen Plan

PSMobile wird zunächst als **Android-first v1** stabilisiert. iOS bleibt ein dokumentierter Folge-Track. Ein paralleler Ausbau beider Plattformen würde derzeit dieselben ungeklärten Core-Verträge zweimal einbetonieren. Ebenso wird nicht einfach `android:process=":slicer"` ergänzt: Der lokale Binder und der native Session-Zeiger funktionieren nicht über Prozessgrenzen. Zuerst entsteht in diesem Plan der unveränderliche Slice-Auftrag; danach kann ein eigener Prozess auf Datei-/IPC-Basis sauber entworfen werden.

## Proposed File Structure

### Neue Dateien

- `core/src/psmobile_session_state.hpp` — zentrale Mutations-, Revisions- und Snapshot-Helfer.
- `core/src/psmobile_toggles.hpp` — einzige Deklaration des Toggle-Collectors.
- `core/test/psm_contract_tests.cpp` — native C-ABI-Vertragstests.
- `build/scripts/run-core-tests.sh` — Testbinary auf Emulator/Gerät ausführen.
- `build/scripts/native-fingerprint.py` — Fingerprint der nativen Quellen erzeugen und prüfen.
- `build/scripts/tests/test_extract_toggles.py` — Generator-Golden-Test.
- `build/scripts/tests/test_gap_report.py` — Statusreport- und Ausgabepfad-Test.
- `android/app/src/main/java/de/psmobile/core/Angles.kt` — einzige Grad/Radiant-Konvertierung für Android.
- `android/app/src/main/java/de/psmobile/ui/NumberCodec.kt` — locale-stabile Anzeige und Eingabe.
- `android/app/src/main/java/de/psmobile/net/SecretStore.kt` — Android-Keystore-basierte Credential-Ablage.
- `android/app/src/main/res/xml/backup_rules.xml` — Regeln bis Android 11.
- `android/app/src/main/res/xml/data_extraction_rules.xml` — Regeln ab Android 12.
- `android/app/src/test/java/de/psmobile/core/AnglesTest.kt`
- `android/app/src/test/java/de/psmobile/ui/NumberCodecTest.kt`
- `android/app/src/test/java/de/psmobile/net/DigestAuthTest.kt`
- `android/app/src/androidTest/java/de/psmobile/net/SecretStoreTest.kt`
- `docs/feature-matrix.json` — maschinenlesbarer Soll-/Ist-Stand mit Evidenz.
- `build/scripts/feature-report.py` — validiert und rendert die Matrix.

### Zu ändernde Hauptdateien

- `core/include/psmobile_core.h`
- `core/src/psmobile_session.hpp`
- `core/src/psmobile_core.cpp`
- `core/src/psmobile_presets.cpp`
- `core/src/psmobile_extruders.cpp`
- `core/src/psmobile_toggles.cpp`
- `core/src/generated/psm_toggles.cpp`
- `viewport/src/psm_viewport.cpp`
- `android/jni/psm_jni.cpp`
- `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- `android/app/src/main/java/de/psmobile/ui/ObjectPanel.kt`
- `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- `android/app/src/main/java/de/psmobile/net/PrinterStore.kt`
- `android/app/src/main/java/de/psmobile/net/PrusaLink.kt`
- `android/app/src/main/java/de/psmobile/net/DigestAuth.kt`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `CMakeLists.txt`
- `build/scripts/build-core.sh`
- `build/scripts/stage-native.sh`
- `build/scripts/gap-report.py`
- `build/scripts/extract-toggles.py`
- `docs/02-architektur.md`
- `docs/03-roadmap.md`
- `docs/07-stopp-punkte.md`
- `docs/09-fehlerliste.md`
- `docs/10-funktionsvergleich.md`

---

### Task 1: Reproduzierbare Test- und Build-Gates einführen

**Files:**

- Modify: `CMakeLists.txt`
- Create: `core/test/psm_contract_tests.cpp`
- Create: `build/scripts/run-core-tests.sh`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`
- Create: `build/scripts/native-fingerprint.py`
- Modify: `build/scripts/build-core.sh`
- Modify: `build/scripts/stage-native.sh`

- [ ] **Step 1: Native Vertragstests als zunächst fehlschlagendes Ziel hinzufügen**

`CMakeLists.txt` erhält neben dem Smoke-CLI ein echtes Testziel:

```cmake
option(PSM_BUILD_TESTS "C-ABI-Vertragstests bauen" ON)

if (PSM_BUILD_TESTS)
    add_executable(psm_contract_tests core/test/psm_contract_tests.cpp)
    target_link_libraries(psm_contract_tests PRIVATE psmobile_core)
    target_compile_features(psm_contract_tests PRIVATE cxx_std_17)
endif()
```

Der erste Test prüft nur stabile Grundverträge und `testdata/wuerfel20.stl`:

```cpp
#include "psmobile_core.h"

#include <cstdlib>
#include <iostream>

static void require(bool value, const char *message)
{
    if (!value) {
        std::cerr << "FAIL: " << message << '\n';
        std::exit(1);
    }
}

int main(int argc, char **argv)
{
    require(argc == 4, "usage: psm_contract_tests DATADIR RESDIR MODEL");
    require(psm_abi_version() == PSM_ABI_VERSION, "ABI version");

    psm_session *session = psm_session_create(argv[1], argv[2]);
    require(session != nullptr, "session create");

    psm_object_id id = PSM_INVALID_ID;
    size_t count = 0;
    require(psm_model_load(session, argv[3], &id, 1, &count) == PSM_OK,
            "load cube");
    require(count == 1 && id != PSM_INVALID_ID, "one object id");

    psm_object_info info{};
    require(psm_model_info(session, id, &info) == PSM_OK, "object info");
    require(info.triangle_count == 12, "cube has twelve triangles");

    psm_session_destroy(session);
    std::cout << "PASS: psm_contract_tests\n";
}
```

- [ ] **Step 2: Test lokal bauen und das fehlende Runner-Gate beobachten**

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
```

Expected: `psm_contract_tests` wird erzeugt; es gibt noch keinen standardisierten Weg, es auf dem Emulator auszuführen.

- [ ] **Step 3: Emulator-/Geräte-Runner implementieren**

`build/scripts/run-core-tests.sh` muss:

1. ABI und optional `ANDROID_SERIAL` akzeptieren.
2. `psm_contract_tests`, `libpsmobile_core.so`, Ressourcen und `testdata/wuerfel20.stl` in ein eindeutiges Verzeichnis unter `/data/local/tmp/psmobile-tests` kopieren.
3. Mit `LD_LIBRARY_PATH` ausführen.
4. Exit-Code und Ausgabe unverändert zurückgeben.
5. Das Geräteverzeichnis vor und nach dem Lauf gezielt entfernen.

Run:

```bash
build/scripts/run-core-tests.sh x86_64
```

Expected:

```text
PASS: psm_contract_tests
```

- [ ] **Step 4: Native Source-Fingerprints zuerst mit einem fehlschlagenden Staging-Test absichern**

`native-fingerprint.py` hasht sortiert:

- `CMakeLists.txt`
- `cmake/`
- `core/include/`
- `core/src/`
- `viewport/include/`
- `viewport/src/`
- `android/jni/`
- den Inhalt von `external/PrusaSlicer/.git/HEAD` und den aufgelösten Commit
- ABI, API-Level und Build-Typ

`build-core.sh` schreibt den Fingerprint nach `build-out/core-$ABI/psmobile.fingerprint`. `stage-native.sh` berechnet ihn erneut und bricht ab, wenn er fehlt oder abweicht. Ohne ABI-Argument müssen **beide** in Gradle konfigurierten ABIs vorhanden und aktuell sein; stilles Überspringen wird entfernt.

Run before implementation:

```bash
rm -f build-out/core-x86_64/psmobile.fingerprint
build/scripts/stage-native.sh x86_64
```

Expected: non-zero exit and `x86_64: Build-Fingerprint fehlt`.

Run after implementation:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/stage-native.sh x86_64
```

Expected: `.so` und Fingerprint werden gemeinsam gestaged.

- [ ] **Step 5: Kotlin-Testabhängigkeit ergänzen**

In `libs.versions.toml`:

```toml
junit = "4.13.2"

[libraries]
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

In `android/app/build.gradle.kts`:

```kotlin
dependencies {
    testImplementation(libs.junit)
}
```

Run:

```bash
./android/gradlew -p android testDebugUnitTest
```

Expected: Gradle erreicht den Testtask. Auf einem Windows-SMB-Arbeitsplatz muss der Projektcache und `app/build` auf ein lokal beschreibbares Verzeichnis zeigen; ein Rechtefehler auf `android/.gradle` oder `android/app/build` gilt als Build-Infrastrukturfehler und nicht als Testergebnis.

- [ ] **Step 6: Commit**

```bash
git add CMakeLists.txt core/test/psm_contract_tests.cpp build/scripts/run-core-tests.sh build/scripts/native-fingerprint.py build/scripts/build-core.sh build/scripts/stage-native.sh android/gradle/libs.versions.toml android/app/build.gradle.kts
git commit -m "test: add reproducible native and Android gates"
```

---

### Task 2: Grad/Radiant- und Locale-Fehler an der Android-Grenze beheben

**Files:**

- Create: `android/app/src/main/java/de/psmobile/core/Angles.kt`
- Create: `android/app/src/main/java/de/psmobile/ui/NumberCodec.kt`
- Create: `android/app/src/test/java/de/psmobile/core/AnglesTest.kt`
- Create: `android/app/src/test/java/de/psmobile/ui/NumberCodecTest.kt`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/ObjectPanel.kt`

- [ ] **Step 1: Fehlschlagende Einheitentests schreiben**

```kotlin
class AnglesTest {
    @Test fun radiansAndDegreesRoundTrip() {
        assertEquals(180f, Angles.radiansToDegrees(Math.PI.toFloat()), 0.0001f)
        assertEquals(Math.PI.toFloat(), Angles.degreesToRadians(180f), 0.0001f)
    }

    @Test fun normalizesNegativeAndLargeAngles() {
        assertEquals(270f, Angles.normalizeDegrees(-90f), 0.0001f)
        assertEquals(90f, Angles.normalizeDegrees(450f), 0.0001f)
    }
}
```

```kotlin
class NumberCodecTest {
    @Test fun outputIsLocaleIndependent() {
        assertEquals("12.5", NumberCodec.oneDecimal(12.5f))
    }

    @Test fun parserAcceptsDotAndComma() {
        assertEquals(12.5f, NumberCodec.parseFloat("12.5"))
        assertEquals(12.5f, NumberCodec.parseFloat("12,5"))
        assertEquals(-90f, NumberCodec.parseFloat("-90"))
    }
}
```

Run:

```bash
./android/gradlew -p android testDebugUnitTest --tests '*AnglesTest' --tests '*NumberCodecTest'
```

Expected: compilation fails because both helpers are missing.

- [ ] **Step 2: Konvertierung und Zahlenformat implementieren**

```kotlin
internal object Angles {
    fun radiansToDegrees(value: Float): Float =
        Math.toDegrees(value.toDouble()).toFloat()

    fun degreesToRadians(value: Float): Float =
        Math.toRadians(value.toDouble()).toFloat()

    fun normalizeDegrees(value: Float): Float =
        ((value % 360f) + 360f) % 360f
}
```

```kotlin
internal object NumberCodec {
    fun oneDecimal(value: Float): String =
        String.format(java.util.Locale.ROOT, "%.1f", value)

    fun parseFloat(value: String): Float? =
        value.trim().replace(',', '.').toFloatOrNull()
}
```

- [ ] **Step 3: `PsmCore`-API eindeutig benennen**

Die JNI-Paketdaten bleiben Radiant. Die öffentliche Kotlin-Seite wird dagegen explizit:

```kotlin
data class ObjectInfo(
    val id: Int,
    val name: String,
    val positionMm: Triple<Float, Float, Float>,
    val rotationDegrees: Triple<Float, Float, Float>,
    val scale: Triple<Float, Float, Float>,
    val sizeMm: Triple<Float, Float, Float>,
    val triangles: Int,
    val instances: Int,
    val outsideBed: Boolean,
)

fun setRotationDegrees(id: Int, x: Float, y: Float, z: Float) =
    check(nativeSetRotation(
        requireHandle(),
        id,
        Angles.degreesToRadians(x),
        Angles.degreesToRadians(y),
        Angles.degreesToRadians(z),
    ))
```

Beim Entpacken von `nativeObjectInfo` werden `v[3]..v[5]` genau einmal mit `radiansToDegrees` konvertiert. Die mehrdeutige Methode `setRotation` wird entfernt, damit neuer UI-Code nicht versehentlich Radiant übergibt.

- [ ] **Step 4: Service und UI auf Grad und locale-stabile Zahlen umstellen**

- `SlicerService.setRotationAxis` und `rotateBy` verwenden `rotationDegrees` und `setRotationDegrees`.
- `ObjectPanel` verwendet `NumberCodec.oneDecimal` und `NumberCodec.parseFloat`.
- `KeyboardType.Decimal` wird verwendet.
- Das Größenfeld zeigt `maxOf(sizeMm.first, sizeMm.second, sizeMm.third)`, weil sein Handler die längste Kante skaliert.

Run:

```bash
./android/gradlew -p android testDebugUnitTest
```

Expected: both test classes pass.

- [ ] **Step 5: Native Vertragserweiterung**

`psm_contract_tests.cpp` setzt `1.57079632679f` über `psm_model_set_rotation`, liest den Wert zurück und prüft, dass die C-ABI weiterhin Radiant liefert. So kann die Plattformgrenze später nicht still auf Grad umgedeutet werden.

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64
```

Expected: `PASS: psm_contract_tests`.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/de/psmobile/core/Angles.kt android/app/src/main/java/de/psmobile/core/PsmCore.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui/ObjectPanel.kt android/app/src/main/java/de/psmobile/ui/NumberCodec.kt android/app/src/test/java/de/psmobile/core/AnglesTest.kt android/app/src/test/java/de/psmobile/ui/NumberCodecTest.kt core/test/psm_contract_tests.cpp
git commit -m "fix: make Android rotation units explicit"
```

---

### Task 3: Eine zentrale Design-Revision und veraltete Slice-Ergebnisse einführen

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_session.hpp`
- Create: `core/src/psmobile_session_state.hpp`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/src/psmobile_presets.cpp`
- Modify: `core/src/psmobile_extruders.cpp`
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `core/test/psm_contract_tests.cpp`

- [ ] **Step 1: Fehlschlagenden Vertragstest für stale G-Code schreiben**

Der Test führt einen vollständigen Slice des Würfels aus, exportiert erfolgreich, ändert danach die Position und erwartet anschließend einen expliziten Fehler:

```cpp
psm_slice_result_info result{};
require(psm_slice_result_info_get(session, &result) == PSM_OK, "result info");
require(result.is_current == 1, "fresh result");

require(psm_model_set_position(session, id, 5.f, 0.f, 10.f) == PSM_OK,
        "move after slice");
require(psm_slice_result_info_get(session, &result) == PSM_OK, "stale info");
require(result.current_revision > result.source_revision, "revision advanced");
require(result.is_current == 0, "result is stale");
require(psm_gcode_export(session, export_path) == PSM_ERR_STALE_RESULT,
        "stale gcode cannot be exported");
```

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
```

Expected: compile fails because `psm_slice_result_info` and `PSM_ERR_STALE_RESULT` do not exist.

- [ ] **Step 2: C-ABI additiv erweitern**

Am Ende der Fehlercodes wird ergänzt:

```c
PSM_ERR_STALE_RESULT = -11
```

Die Ergebnisabfrage wird additiv eingeführt:

```c
typedef struct {
    uint64_t source_revision;
    uint64_t current_revision;
    int32_t  is_current;
} psm_slice_result_info;

PSM_API psm_result psm_slice_result_info_get(
    psm_session *s,
    psm_slice_result_info *out);
```

Die ABI-Version bleibt bei additiven Symbolen kompatibel. Zusätzlich entsteht ein Symbol-Check im Build-Gate, damit APK und Kotlin nicht gegen eine ältere `.so` laufen.

- [ ] **Step 3: Eine einzige Mutationsfunktion verwenden**

`psmobile_session_state.hpp` stellt für alle Übersetzungseinheiten bereit:

```cpp
inline void psm_mark_design_changed(psm_session &session)
{
    ++session.design_revision;
    session.last_error.clear();
}

inline void psm_mark_config_changed(psm_session &session)
{
    ++session.config_revision;
    psm_mark_design_changed(session);
}
```

`psm_session` erhält:

```cpp
uint64_t design_revision = 0;
uint64_t completed_slice_revision = 0;
```

Alle erfolgreichen Mutationen rufen genau eine der beiden Funktionen auf:

- Session leeren
- Modell laden/entfernen
- Position, Rotation, Skalierung, Spiegelung, Instanzen
- Anordnen und auf Bett ablegen
- alle Viewport-Gizmo-Mutationen
- `psm_config_set`, einschließlich `ConfigOptionString`
- `psm_config_set_at`
- Presets installieren/auswählen
- Extruder- und Filamentzuordnung

Direkte `++s->config_revision`-Stellen werden entfernt, damit keine Mutation doppelt zählt und keine vergessen wird.

- [ ] **Step 4: Slice-Ergebnis an Quell-Revision binden**

`psm_slice_start` merkt sich die aktuelle `design_revision`. Nur bei erfolgreichem Abschluss wird diese als `completed_slice_revision` gesetzt. `psm_gcode_export`, `psm_gcode_suggested_name`, Statistik- und Preview-Zugriffe lehnen ein veraltetes Ergebnis mit `PSM_ERR_STALE_RESULT` ab.

Android bildet den Fehler auf einen eigenen Zustand ab:

```kotlin
data class SliceFreshness(
    val sourceRevision: Long,
    val currentRevision: Long,
    val isCurrent: Boolean,
)
```

`SlicerService` setzt `lastGcode = null`, sobald die Core-Abfrage ein stale Ergebnis meldet. Export- und Sende-Buttons sind dann deaktiviert und zeigen „Änderungen noch nicht gesliced“.

- [ ] **Step 5: Tests ausführen**

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64
./android/gradlew -p android testDebugUnitTest
```

Expected: stale result is rejected; ordinary export directly after slicing still succeeds.

- [ ] **Step 6: Commit**

```bash
git add core/include/psmobile_core.h core/src/psmobile_session.hpp core/src/psmobile_session_state.hpp core/src/psmobile_core.cpp core/src/psmobile_presets.cpp core/src/psmobile_extruders.cpp viewport/src/psm_viewport.cpp android/jni/psm_jni.cpp android/app/src/main/java/de/psmobile/core/PsmCore.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt core/test/psm_contract_tests.cpp
git commit -m "fix: invalidate stale slice results by revision"
```

---

### Task 4: Slicing auf einem unveränderlichen Snapshot ausführen

**Files:**

- Modify: `core/src/psmobile_session.hpp`
- Modify: `core/src/psmobile_session_state.hpp`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/src/psmobile_presets.cpp`
- Modify: `core/src/psmobile_extruders.cpp`
- Modify: `viewport/src/psm_viewport.cpp`
- Modify: `core/test/psm_contract_tests.cpp`

- [ ] **Step 1: Fehlschlagenden Parallelitäts-/Snapshot-Test schreiben**

Der Test startet einen Slice, ändert sofort Position und `layer_height`, wartet auf das Ergebnis und prüft:

1. kein Crash oder Deadlock,
2. das Ergebnis trägt die Revision vom Start,
3. es ist wegen der nachfolgenden Änderung stale,
4. ein zweiter Slice verwendet die neue Revision und ist aktuell,
5. 25 Wiederholungen zeigen denselben Zustand.

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64 --filter snapshot
```

Expected before implementation: ThreadSanitizer ist auf Android nicht das Primärgate; der fachliche Test schlägt mindestens an der erwarteten stale Quell-Revision fehl und kann unter Last abstürzen.

- [ ] **Step 2: Daten- und Jobzustand trennen**

`psm_session` erhält zwei klar getrennte Sperren:

```cpp
std::mutex data_mutex;
std::mutex job_mutex;
```

`data_mutex` schützt `model`, `config`, `presets`, Konfigurationscaches und Revisionen. `job_mutex` schützt abgeschlossenes `Print`, Statistiken, Dateipfad und Job-Callbacks. `state` und `cancel_requested` bleiben atomar.

Ein Slice-Auftrag besitzt seine Daten:

```cpp
struct psm_slice_input {
    Slic3r::Model model;
    Slic3r::DynamicPrintConfig config;
    uint64_t revision;
};
```

`psm_slice_start` kopiert `model`, `config` und Revision gemeinsam unter `data_mutex`. Der Worker fasst danach `s->model` und `s->config` nicht mehr an.

- [ ] **Step 3: `Print` worker-owned machen**

Im Worker wird ein lokales `std::shared_ptr<Slic3r::Print>` aufgebaut und ausschließlich mit `input.model`/`input.config` verwendet. Nach erfolgreichem Ende werden Print, Statistiken, G-Code-Pfad und Quell-Revision in einem kurzen `job_mutex`-Abschnitt veröffentlicht.

`psm_slice_cancel` setzt nur `cancel_requested`. Der installierte PrusaSlicer-Cancel-Callback liest dieses Atomic und bricht im Worker ab. Die UI dereferenziert keinen `Print*`, während der Worker ihn zerstören könnte.

- [ ] **Step 4: Alle Core- und Viewport-Zugriffe serialisieren**

- C-ABI-Leser nehmen `data_mutex`, kopieren ihr Ergebnis und geben die Sperre vor Rückkehr frei.
- C-ABI-Mutatoren nehmen `data_mutex`, mutieren und markieren die Revision im selben kritischen Abschnitt.
- Der Viewport nimmt `data_mutex` nur beim Neuaufbau von Renderdaten und während eines Gizmo-Mutationsschritts, nicht in jedem Frame.
- Preview-Code nimmt unter `job_mutex` einen `shared_ptr` auf das abgeschlossene `Print` und konvertiert danach ohne gehaltene Session-Sperre.
- Sperrreihenfolge ist überall `data_mutex` vor `job_mutex`; kein Code darf die umgekehrte Reihenfolge nehmen.

- [ ] **Step 5: Snapshot- und Cancel-Tests ausführen**

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64 --filter snapshot
build/scripts/run-core-tests.sh x86_64 --filter cancel
```

Expected: all repetitions pass, cancellation ends in `PSM_STATE_CANCELLED`, and the next slice starts normally.

- [ ] **Step 6: Commit**

```bash
git add core/src/psmobile_session.hpp core/src/psmobile_session_state.hpp core/src/psmobile_core.cpp core/src/psmobile_presets.cpp core/src/psmobile_extruders.cpp viewport/src/psm_viewport.cpp core/test/psm_contract_tests.cpp
git commit -m "fix: slice immutable session snapshots"
```

---

### Task 5: Toggle-Extraktion ohne ODR-Verstoß und mit korrekter Invalidierung

**Files:**

- Create: `core/src/psmobile_toggles.hpp`
- Modify: `core/src/psmobile_toggles.cpp`
- Modify: `core/src/generated/psm_toggles.cpp`
- Modify: `build/scripts/extract-toggles.py`
- Create: `build/scripts/tests/test_extract_toggles.py`
- Modify: `core/src/psmobile_session.hpp`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `core/src/psmobile_presets.cpp`
- Modify: `core/include/psmobile_core.h`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt`
- Modify: `core/test/psm_contract_tests.cpp`

- [ ] **Step 1: Generator-Golden-Test schreiben**

Der Python-`unittest` führt `extract-toggles.py` in ein Temp-Verzeichnis aus und prüft:

- Ausgabe enthält `#include "psmobile_toggles.hpp"`.
- Ausgabe enthält **keine** zweite `struct ToggleCollector`.
- zweimaliges Generieren ergibt byte-identische Dateien.
- die Zahl der `toggle_field`-Aufrufe entspricht der Quelle.

Run:

```bash
python -m unittest discover -s build/scripts/tests -p 'test_extract_toggles.py'
```

Expected: fail, weil der Generator die Struktur derzeit selbst definiert.

- [ ] **Step 2: Eine einzige Collector-Definition einführen**

`psmobile_toggles.hpp`:

```cpp
#pragma once

#include <map>
#include <string>

namespace Slic3r { class DynamicPrintConfig; }

namespace psm {

struct ToggleCollector {
    std::map<std::string, bool> enabled;

    void toggle_field(const std::string &key, bool on, int index = -1)
    {
        (void) index;
        enabled[key] = on;
    }

    void collect_print_fff(Slic3r::DynamicPrintConfig *config);
};

} // namespace psm
```

Generator und Hülle inkludieren diesen Header. Nur die Methode `collect_print_fff` wird generiert. Damit existiert exakt eine Klassendefinition im Programm.

- [ ] **Step 3: C-ABI-Fehler nicht als „enabled“ verschlucken**

Die bisherige Rückgabe `int32_t psm_config_enabled(...)` wird durch eine additive, eindeutige Abfrage ergänzt:

```c
typedef struct {
    int32_t enabled;
    char    blocked_by[64];
} psm_config_enablement;

PSM_API psm_result psm_config_enablement_get(
    psm_session *s,
    const char *key,
    psm_config_enablement *out);
```

Eine Exception liefert `PSM_ERR_GENERIC`; sie darf nicht so aussehen, als sei der Parameter bedienbar.

- [ ] **Step 4: Cache-Invalidierung testen und korrigieren**

Native Tests prüfen:

- `support_material=false` sperrt Support-Unteroptionen.
- Änderung auf `true` aktiviert sie ohne Session-Neustart.
- Änderung eines `ConfigOptionString` erhöht `config_revision`.
- Auswahl eines anderen Print-, Filament- oder Drucker-Presets erhöht `config_revision`.
- Drucker-/Filamentoptionen, für die keine extrahierte Regel existiert, werden als „keine Regel“ und nicht als geprüft wahr ausgewiesen.

Alle Konfigurationspfade verwenden `psm_mark_config_changed` aus Task 3.

- [ ] **Step 5: JNI-Aufrufe pro Seite bündeln**

`PsmCore` erhält `enablement(keys: List<String>): Map<String, Enablement>`. Ein JNI-Aufruf nimmt eine Schlüsselliste, wertet alle Einträge unter derselben Core-Revision aus und gibt ein Stringarray im Format `key<TAB>enabled<TAB>blockedBy` zurück. `SettingsScreen` ruft diese Funktion einmal je Seite und Revision auf.

Run:

```bash
python -m unittest discover -s build/scripts/tests -p 'test_extract_toggles.py'
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64 --filter toggles
./android/gradlew -p android testDebugUnitTest
```

Expected: generator and toggle invalidation tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/psmobile_toggles.hpp core/src/psmobile_toggles.cpp core/src/generated/psm_toggles.cpp build/scripts/extract-toggles.py build/scripts/tests/test_extract_toggles.py core/src/psmobile_session.hpp core/src/psmobile_core.cpp core/src/psmobile_presets.cpp core/include/psmobile_core.h android/jni/psm_jni.cpp android/app/src/main/java/de/psmobile/core/PsmCore.kt android/app/src/main/java/de/psmobile/ui/SettingsScreen.kt core/test/psm_contract_tests.cpp
git commit -m "fix: make extracted setting toggles deterministic"
```

---

### Task 6: Geometrieoperationen aus dem Android-Service in den Core verschieben

**Files:**

- Modify: `core/include/psmobile_core.h`
- Modify: `core/src/psmobile_core.cpp`
- Modify: `android/jni/psm_jni.cpp`
- Modify: `android/app/src/main/java/de/psmobile/core/PsmCore.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `core/test/psm_contract_tests.cpp`

- [ ] **Step 1: Fehlschlagende Geometrietests schreiben**

Tests decken diese Fälle ab:

- Ein 20×10×5-mm-Quader wird nach 90°-Rotation anhand seiner transformierten Bounding Box skaliert.
- Ein Bett mit negativen Koordinaten nutzt `max - min`, nicht nur `max`.
- `fit_to_bed(..., 0.90)` hält auf X und Y zehn Prozent Gesamtrand ein.
- Nach der Operation liegt das Objekt mit der transformierten Unterkante auf Z=0.

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64 --filter geometry
```

Expected: fail; die bestehende Skalierung verwendet die unrotierte Roh-Bounding-Box und Android berechnet Bettbreite/-tiefe selbst.

- [ ] **Step 2: Fachliche C-ABI ergänzen**

```c
PSM_API psm_result psm_model_scale_longest_edge(
    psm_session *s,
    psm_object_id id,
    float target_mm);

PSM_API psm_result psm_model_fit_to_bed(
    psm_session *s,
    psm_object_id id,
    float fill_ratio);
```

Die Core-Implementierung nutzt die transformierte Instanz-Bounding-Box und PrusaSlicers `bed_shape`. `fill_ratio` muss im Bereich `(0, 1]` liegen.

- [ ] **Step 3: Android-Duplikat entfernen**

`SlicerService.scaleToBed` parst `bed_shape` nicht mehr. Die Methode delegiert nur:

```kotlin
fun scaleToBed(id: Int) =
    withObject(id, "Aufs Bett einpassen") { it.fitToBed(id, 0.90f) }
```

`scaleToSize` delegiert an `scaleLongestEdge`.

- [ ] **Step 4: Tests ausführen**

Run:

```bash
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64 --filter geometry
```

Expected: all geometry contracts pass.

- [ ] **Step 5: Commit**

```bash
git add core/include/psmobile_core.h core/src/psmobile_core.cpp android/jni/psm_jni.cpp android/app/src/main/java/de/psmobile/core/PsmCore.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt core/test/psm_contract_tests.cpp
git commit -m "refactor: move bed fitting into the core"
```

---

### Task 7: Android-Slice-Lebenszyklus korrekt als gestarteten Foreground-Service modellieren

**Files:**

- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/de/psmobile/MainActivity.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/androidTest/java/de/psmobile/slicing/SlicerServiceLifecycleTest.kt`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Instrumentierten Lebenszyklustest schreiben**

Der Test startet den Service mit `ACTION_START_SLICE`, löst Unbind/Rebind aus und prüft:

- `onStartCommand` wird erreicht,
- Foreground-Notification existiert,
- der Dienst bleibt nach Unbind gestartet,
- `ACTION_CANCEL_SLICE` fordert Abbruch an,
- nach Abschluss wird Foreground beendet und `stopSelfResult(startId)` aufgerufen.

Run:

```bash
./android/gradlew -p android connectedDebugAndroidTest
```

Expected: fail, weil der Dienst derzeit nur über lokalen Binder gestartet wird.

- [ ] **Step 2: Command-basierten Service-Lebenszyklus implementieren**

`SlicerService` definiert:

```kotlin
const val ACTION_START_SLICE = "de.psmobile.action.START_SLICE"
const val ACTION_CANCEL_SLICE = "de.psmobile.action.CANCEL_SLICE"
```

Beim Nutzer-Tap ruft die Activity aus dem Vordergrund auf:

```kotlin
ContextCompat.startForegroundService(
    this,
    Intent(this, SlicerService::class.java)
        .setAction(SlicerService.ACTION_START_SLICE),
)
```

`onStartCommand` ruft für Start sofort `startForeground` und startet danach genau einen Job. Für Cancel wird nur abgebrochen. Der lokale Binder bleibt für UI-State und Interaktion bestehen. Nach Done/Failed/Cancelled folgen `stopForeground(STOP_FOREGROUND_REMOVE)` und `stopSelfResult(startId)`.

- [ ] **Step 3: Cancel-Aktion in die Notification aufnehmen**

Die Notification erhält einen immutable `PendingIntent.getService` mit `ACTION_CANCEL_SLICE`. Der Status nach einem Prozessverlust wird beim nächsten App-Start als abgebrochener Job dargestellt; es wird kein falsches Resume versprochen.

- [ ] **Step 4: Prozessgrenze ehrlich festhalten**

`AndroidManifest.xml` bekommt in dieser Phase bewusst **kein** `android:process`. Der Kommentar erklärt, dass der Job nach Task 4 isoliert ist, die Remote-Prozess-IPC aber ein eigener Folgeplan ist. Das verhindert, dass jemand den Attributwechsel ohne AIDL/Messenger und serialisierte Eingaben vornimmt.

- [ ] **Step 5: Test und manuellen Screen-off-Check ausführen**

Run:

```bash
./android/gradlew -p android connectedDebugAndroidTest
adb shell dumpsys activity services de.psmobile
```

Manual:

1. Slice eines ausreichend großen Modells starten.
2. Display ausschalten.
3. Zwei Minuten warten.
4. Display einschalten und Fortschritt/Abschluss prüfen.
5. Zweiten Lauf über Notification abbrechen.

Expected: Service ist als started+bound sichtbar; Slice überlebt Unbind und Screen-off; Cancel endet sauber.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/de/psmobile/MainActivity.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/main/res/values/strings.xml android/app/src/androidTest/java/de/psmobile/slicing/SlicerServiceLifecycleTest.kt android/gradle/libs.versions.toml android/app/build.gradle.kts
git commit -m "fix: run slicing as a started foreground service"
```

---

### Task 8: PrusaLink-Versand und Credentials härten

**Files:**

- Create: `android/app/src/main/java/de/psmobile/net/SecretStore.kt`
- Modify: `android/app/src/main/java/de/psmobile/net/PrinterStore.kt`
- Modify: `android/app/src/main/java/de/psmobile/net/PrusaLink.kt`
- Modify: `android/app/src/main/java/de/psmobile/net/DigestAuth.kt`
- Modify: `android/app/src/main/java/de/psmobile/slicing/SlicerService.kt`
- Modify: `android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/backup_rules.xml`
- Create: `android/app/src/main/res/xml/data_extraction_rules.xml`
- Create: `android/app/src/test/java/de/psmobile/net/DigestAuthTest.kt`
- Create: `android/app/src/androidTest/java/de/psmobile/net/SecretStoreTest.kt`

- [ ] **Step 1: Fehlschlagende Security- und Digest-Tests schreiben**

Tests prüfen:

- Printer-JSON enthält weder `apiKey` noch `password`.
- Migration liest einen alten Klartext-Eintrag genau einmal, legt das Secret verschlüsselt ab und entfernt Klartext.
- Wiederherstellung ohne Keystore-Key führt zu „Credentials erneut eingeben“, nicht zu einem Crash.
- parallele Digest-Header für dieselbe Nonce verwenden eindeutige, streng steigende `nc`-Werte.
- ein unbekannter `algorithm` wird abgelehnt und nicht als MD5 berechnet, aber mit falschem Namen versendet.
- `suggestedGcodeName()` wird als Remote-Dateiname verwendet.

Run:

```bash
./android/gradlew -p android testDebugUnitTest connectedDebugAndroidTest
```

Expected: tests fail against the current plaintext store and mutable counter.

- [ ] **Step 2: Secrets mit Android Keystore verschlüsseln**

`SecretStore` erzeugt einen nicht exportierbaren AES-256-GCM-Key im Provider `AndroidKeyStore`. Pro Credential werden zufälliger 12-Byte-IV und Ciphertext gespeichert. `PrinterStore` enthält nur nicht geheime Metadaten und eine `credentialRef`.

Die alte Struktur wird transaktional migriert:

1. alten JSON-Eintrag lesen,
2. Secret verschlüsselt speichern,
3. neuen Metadateneintrag schreiben,
4. erst danach Klartextfelder entfernen.

`EncryptedSharedPreferences` wird nicht neu eingeführt, weil die API inzwischen deprecated ist; die Keystore-Nutzung bleibt explizit und testbar.

- [ ] **Step 3: Backup-Regeln ergänzen**

Manifest:

```xml
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules">
```

Beide XML-Dateien schließen die Secret-Preference-Datei von Cloud-Backup und Device-Transfer aus. Nicht geheime Druckermetadaten dürfen erhalten bleiben; nach Restore fordert die UI Credentials neu an.

- [ ] **Step 4: HTTP-Verhalten explizit machen**

Da lokale PrusaLink-Instanzen häufig nur HTTP anbieten und Android ab API 28 Cleartext standardmäßig sperrt:

- Printer erhält ein persistiertes `allowInsecureHttp`, standardmäßig `false`.
- `https://` bleibt Standard, wenn der Nutzer ein Schema angibt.
- Schema-loser Host wird nicht mehr still zu HTTP.
- Für HTTP zeigt die UI eine klare Warnung und verlangt ausdrückliche Aktivierung je Drucker.
- Erst danach wird Cleartext im Manifest mit `android:usesCleartextTraffic="true"` technisch erlaubt; `PrusaLink` lehnt HTTP ohne diese Druckerfreigabe selbst ab.

Damit ist die globale Android-Freigabe nicht zugleich eine fachliche Freigabe für beliebige Ziele.

- [ ] **Step 5: Digest und Parallelität korrigieren**

- `challenges` wird durch `ConcurrentHashMap` ersetzt.
- Der Nonce-Counter wird `AtomicInteger`.
- Unterstützte Algorithmen werden explizit gemappt; mindestens `MD5` funktioniert wie bisher, unbekannte Werte liefern einen Fehler.
- Netzwerk-I/O läuft auf `Dispatchers.IO`, nicht auf `Dispatchers.Default`.

- [ ] **Step 6: Druckerwahl und Dateinamen korrigieren**

Nach einem Slice zeigt `SlicerScreen` bei mehreren Druckern einen Ziel-Dialog. Kein Code verwendet mehr `linkPrinters.first()` als implizites Ziel. `SlicerService.sendToPrinter` nutzt `suggestedGcodeName()` auch remote und bereinigt nur unzulässige Pfadzeichen.

- [ ] **Step 7: Tests und Hardware-Smoke-Test**

Run:

```bash
./android/gradlew -p android testDebugUnitTest connectedDebugAndroidTest
```

Hardware:

1. API-Key-Drucker testen.
2. Digest-Drucker testen.
3. HTTP-Freigabe ablehnen und erwarteten Fehler sehen.
4. Freigabe erteilen, Probe und Upload durchführen.
5. Bei zwei eingerichteten Druckern jeweils das gewählte Ziel verifizieren.

Expected: keine Credentials im Backup/Printer-JSON; beide Auth-Flows funktionieren; Ziel und Dateiname stimmen.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/de/psmobile/net/SecretStore.kt android/app/src/main/java/de/psmobile/net/PrinterStore.kt android/app/src/main/java/de/psmobile/net/PrusaLink.kt android/app/src/main/java/de/psmobile/net/DigestAuth.kt android/app/src/main/java/de/psmobile/slicing/SlicerService.kt android/app/src/main/java/de/psmobile/ui/SlicerScreen.kt android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml/backup_rules.xml android/app/src/main/res/xml/data_extraction_rules.xml android/app/src/test/java/de/psmobile/net/DigestAuthTest.kt android/app/src/androidTest/java/de/psmobile/net/SecretStoreTest.kt
git commit -m "fix: harden printer credentials and upload selection"
```

---

### Task 9: Eine maschinenlesbare Statusmatrix zur Wahrheitsquelle machen

**Files:**

- Create: `docs/feature-matrix.json`
- Create: `build/scripts/feature-report.py`
- Create: `build/scripts/tests/test_gap_report.py`
- Modify: `build/scripts/gap-report.py`
- Modify: `docs/02-architektur.md`
- Modify: `docs/03-roadmap.md`
- Modify: `docs/07-stopp-punkte.md`
- Modify: `docs/09-fehlerliste.md`
- Modify: `docs/10-funktionsvergleich.md`
- Modify: `README.md`

- [ ] **Step 1: Fehlschlagende Reporttests schreiben**

Tests führen Reports in einem Temp-Verzeichnis aus und prüfen:

- `gap-report.py --output <tempfile>` schreibt nicht fest nach `build-out`.
- `--output -` schreibt nur nach stdout.
- Gizmos `Move`, `Rotate` und `Scale` kommen aus der Feature-Matrix und werden als vorhanden erkannt.
- ungültiger Status oder fehlender Evidenzpfad lässt `feature-report.py --check` non-zero enden.
- ein `hardware_tested`-Eintrag muss einen Geräte-/Testbeleg nennen.

Run:

```bash
python -m unittest discover -s build/scripts/tests -p 'test_*.py'
```

Expected: fail because output path and capability sets are currently hardcoded.

- [ ] **Step 2: Matrixschema und erste echte Einträge anlegen**

`feature-matrix.json` verwendet dieses Format:

```json
{
  "schema": 1,
  "features": [
    {
      "id": "android.viewport.move",
      "title": "Objekt im Viewport verschieben",
      "platform": "android",
      "status": "emulator_tested",
      "evidence": [
        "viewport/src/psm_viewport.cpp",
        "docs/08-loop-protokoll.md"
      ],
      "blocked_by": []
    },
    {
      "id": "ios.viewport",
      "title": "iOS 3D-Viewport",
      "platform": "ios",
      "status": "not_started",
      "evidence": [
        "ios/PSMobile/ContentView.swift"
      ],
      "blocked_by": [
        "ios.build"
      ]
    }
  ]
}
```

Alle v1-Funktionen aus Brainstorming/Roadmap werden eingetragen. „Code vorhanden“ und „Hardware getestet“ dürfen nie derselbe implizite Zustand sein.

- [ ] **Step 3: Reports aus der Matrix erzeugen**

`feature-report.py`:

- validiert Schema, Status und Evidenzpfade,
- erzeugt eine Markdown-Tabelle nach Plattform und Status,
- zeigt Blocker getrennt,
- bricht bei ungültiger Evidenz ab.

`gap-report.py` behält die automatische PrusaSlicer-Parameteranalyse, bezieht eigene Fähigkeiten aber aus der Matrix. `HABEN_WIR_GIZMOS` und `HABEN_WIR_MENUE` entfallen.

Run:

```bash
python build/scripts/feature-report.py --check
python build/scripts/feature-report.py --markdown docs/10-funktionsvergleich.md
python build/scripts/gap-report.py external/PrusaSlicer android/app/src/main/assets/psui --output -
```

Expected: all commands succeed without Schreibzugriff auf `build-out/gap-report.txt`.

- [ ] **Step 4: Dokumente auf den tatsächlichen Stand bringen**

Mindestens diese Widersprüche werden korrigiert:

- Preview ist auf Android vorhanden; alte „fehlt“-Angaben werden historisch markiert.
- Move/Rotate/Scale sind vorhanden, übrige Gizmos nicht.
- Slicer-Service läuft in dieser Phase im UI-Prozess.
- iOS ist ein Scaffold ohne funktionsfähigen Viewport und ohne verifizierten Build.
- Display-Dezimierung, gestufte Preview und gemessene harte Speicherlimits sind noch nicht implementiert.
- PrusaLink gilt erst nach Hardware-Test als getestet.
- Android-first v1 ist der aktuelle Ausführungsfokus.

- [ ] **Step 5: Commit**

```bash
git add docs/feature-matrix.json build/scripts/feature-report.py build/scripts/tests/test_gap_report.py build/scripts/gap-report.py docs/02-architektur.md docs/03-roadmap.md docs/07-stopp-punkte.md docs/09-fehlerliste.md docs/10-funktionsvergleich.md README.md
git commit -m "docs: make feature evidence the status source"
```

---

### Task 10: Release-Gate durchführen und Folgepläne abgrenzen

**Files:**

- Modify: `docs/feature-matrix.json`
- Modify: `docs/03-roadmap.md`
- Modify: `docs/04-lizenz-und-store.md`
- Create: `docs/release/android-v1-gate.md`

- [ ] **Step 1: Vollständigen technischen Gate-Lauf ausführen**

Run:

```bash
python -m unittest discover -s build/scripts/tests -p 'test_*.py'
python build/scripts/feature-report.py --check
ANDROID_ABI=x86_64 build/scripts/build-core.sh
build/scripts/run-core-tests.sh x86_64
build/scripts/stage-native.sh x86_64
./android/gradlew -p android testDebugUnitTest assembleDebug
./android/gradlew -p android connectedDebugAndroidTest
```

Expected: all automated gates pass. Das APK enthält ausschließlich native Bibliotheken mit aktuellem Fingerprint.

- [ ] **Step 2: Geräte-Matrix ausführen**

Mindestens:

- ein arm64-Gerät mit 4–6 GB RAM,
- ein arm64-Gerät mit 8 GB oder mehr,
- ein x86_64-Emulator.

Je Gerät:

1. STL und 3MF importieren.
2. Zahlenrotation und 90°-Button prüfen.
3. Während eines Slice ändern; altes Ergebnis muss stale sein.
4. erneut slicen, Preview öffnen, exportieren.
5. Screen-off und Notification-Cancel prüfen.
6. PrusaLink-Probe und Upload auf echter Hardware prüfen.
7. Prozessspeicher und Slice-Spitzenwert protokollieren.

Ergebnisse werden mit Datum, Geräteklasse, Android-Version und Build-Fingerprint in `docs/release/android-v1-gate.md` festgehalten und in der Matrix referenziert.

- [ ] **Step 3: Lizenz-/Store-Blocker ausdrücklich offen halten**

Vor öffentlicher Distribution müssen mindestens vorhanden oder entschieden sein:

- Root-`LICENSE`,
- `THIRD-PARTY-NOTICES`,
- AGPL-Quellcodeangebot und Buildanleitung,
- Entscheidung zu Apple-App-Store/AGPL aus `docs/04-lizenz-und-store.md`,
- Datenschutzhinweis für lokale Drucker-Credentials und Netzwerkzugriffe.

Ohne diese Punkte bekommt die Matrix für Distribution `blocked`; technische Tests dürfen den rechtlichen Status nicht auf „fertig“ setzen.

- [ ] **Step 4: Zwei getrennte Folgepläne anlegen**

Nach bestandenem Stabilitätsgate werden separat geplant:

1. **Slicer-Prozess-Isolation:** serialisierter Slice-Auftrag, AIDL/Messenger, Job-Recovery, Prozess-Low-Memory-Test.
2. **iOS-Parität:** statische Core-Library, plattformspezifischer GL/Metal-Viewport, macOS-CI-Build, Swift-Concurrency und Background-Task-Verhalten.

Diese Tracks bleiben getrennt, weil sie unabhängige Architektur- und Testmatrizen haben.

- [ ] **Step 5: Commit**

```bash
git add docs/feature-matrix.json docs/03-roadmap.md docs/04-lizenz-und-store.md docs/release/android-v1-gate.md
git commit -m "docs: record the Android v1 release gate"
```

---

## Definition of Done

- Grad/Radiant ist an jeder Plattformgrenze eindeutig und getestet.
- Modell-/Konfigurationsänderungen machen ältere Slice-Ergebnisse sicher stale.
- Slicing arbeitet auf einem unveränderlichen Snapshot; UI- und Viewport-Änderungen können nicht mit Worker-Daten rennen.
- Toggle-Logik hat eine einzige C++-Definition und wird bei jeder relevanten Konfigurationsänderung invalidiert.
- Geometrieoperationen verwenden transformierte Bounding-Boxes und leben im Core.
- Android-Slicing läuft als korrekt gestarteter Foreground-Service.
- PrusaLink-Zielwahl, Remote-Dateiname, HTTP-Freigabe und Credential-Speicherung sind explizit und getestet.
- Native `.so`-Artefakte können nicht still veraltet in eine neue APK gelangen.
- Feature-Status ist maschinenlesbar, evidenzbasiert und widerspruchsfrei.
- Android v1 hat ein dokumentiertes Geräte-Gate.
- Separater Slicer-Prozess und iOS-Parität sind bewusst abgegrenzte Folgepläne, keine behaupteten vorhandenen Funktionen.
