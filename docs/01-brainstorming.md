# PSMobile - Brainstorming

Stand: 2026-07-28

Ziel: PrusaSlicer auf Android und iOS, mit einer UI die fuer Touch und Stift
gebaut ist statt fuer Maus und Tastatur.

---

## 1. Ausgangslage (gemessen, nicht geschaetzt)

Basis ist der vorhandene Fork:
`Z:\Prusa Slicer Fork\Bump Mesh Plugin\PrusaSlicer`, PrusaSlicer 2.9.6,
Tag `v2.9.6-bumpmesh.2`, HEAD `6bcec8d`.

| Messung | Wert | Bedeutung |
| --- | --- | --- |
| `src/libslic3r` | ~190.000 LOC | der eigentliche Slicer |
| `#include <wx...>` in libslic3r | **0** | Core ist GUI-frei, sauber portierbar |
| `src/slic3r` (GUI) | ~166.000 LOC, 404 Dateien | komplett wxWidgets-gebunden |
| `src/libvgcode` | ~5.600 LOC | G-Code-Preview-Renderer, klein und gekapselt |
| `resources/shaders/ES/` | **34 Shader, `#version 100`** | GLES-Shader existieren bereits |
| CMake-Option `SLIC3R_OPENGL_ES` | vorhanden | offizieller GLES-Pfad im Upstream |
| OpenVDB-Nutzung | nur `OpenVDBUtils*`, `Hollowing*`, SLA | fuer FDM abschaltbar |
| STEP/OCCT | `SLIC3R_ENABLE_FORMAT_STEP=OFF` moeglich | fuer v1 abschaltbar |
| `boost/process` in libslic3r | 1 Treffer | einzelne Stelle, ersetzbar |
| libcurl in libslic3r | 0 Treffer | Netzwerk sitzt nur im GUI-Layer |

### Die drei Kernaussagen daraus

1. **Der Slicer selbst muss nicht portiert werden, er muss nur
   cross-kompiliert werden.** libslic3r ist portables C++17 ohne GUI-Kopplung.
   Das ist die gute Nachricht und der Grund, warum das Projekt ueberhaupt
   realistisch ist.
2. **Die GUI muss neu gebaut werden, nicht portiert.** wxWidgets hat keinen
   brauchbaren Android-Port, und `wxOSX/iPhone` ist seit Jahren ein Stub.
   Die 166k LOC in `src/slic3r` sind kein Portierungsziel, sondern eine
   *Referenz*, aus der wir Logik herausloesen.
3. **Der 3D-Renderer ist zu grossen Teilen schon mobiltauglich.** Prusa hat
   fuer Raspberry Pi / Wayland bereits GLES-2-Shader und einen
   `SLIC3R_OPENGL_ES`-Pfad gebaut. Das spart den groessten Einzelbrocken
   des Renderings.

---

## 2. Was "Port" hier konkret heisst

Drei getrennte Baustellen, die man nicht vermischen darf:

```
+----------------------------------------------------+
|  Schicht A: Neue Touch-/Stift-UI                    |  100% neu
|  (Kotlin/Compose bzw. SwiftUI bzw. Flutter)         |
+----------------------------------------------------+
|  Schicht B: Viewport + Bruecke                      |  ~30% neu, 70% adaptiert
|  GLES-Renderer, Kamera, Gizmos, C-ABI, JNI/Swift    |
+----------------------------------------------------+
|  Schicht C: libslic3r + libvgcode + Deps            |  0% neu, nur Buildarbeit
|  Slicing, G-Code, 3MF, Profile, Arrange             |
+----------------------------------------------------+
```

Der Aufwand ist **nicht** gleich verteilt. Schicht C ist zaehe, aber
absehbare Buildarbeit. Schicht A ist Designarbeit. Schicht B ist die
eigentliche Ingenieursarbeit und das Risiko.

---

## 3. Optionen fuer den UI-Stack

### Option A - Nativ pro Plattform + gemeinsamer C++-Viewport
Kotlin/Jetpack Compose auf Android, SwiftUI auf iOS. Beide zeichnen ein
`GLSurfaceView` bzw. `CAEAGLLayer`/`MTKView`, in dem derselbe C++-Renderer
laeuft. Steuerung, Listen, Einstellungen, Dateibrowser sind nativ.

- **Pro**: bestes Touch-Gefuehl, echte Systemintegration (Files, Share,
  Apple Pencil Hover/Druck, S-Pen), keine Framework-Zwischenschicht im
  Renderpfad, beste Performance und beste Store-Chancen.
- **Contra**: UI-Arbeit faellt zweimal an.

### Option B - Flutter fuer alles
Eine UI-Codebasis, `dart:ffi` auf das C-ABI, Viewport ueber Texture/Platform
View.

- **Pro**: eine UI-Codebasis, schnelle Iteration, gutes Layout-System.
- **Contra**: der 3D-Viewport ist genau die Sache, die Flutter *nicht* gut
  kann - man landet bei einer Platform-View-Kruecke mit
  Latenz- und Eingabe-Problemen. Stift-Druck und Hover sind in Flutter
  schwaecher abgebildet. Genau unsere Kernanforderung leidet.

### Option C - Reines C++ mit Dear ImGui
PrusaSlicer nutzt ImGui bereits massiv fuer die Gizmos. Man koennte die
gesamte Oberflaeche in C++ halten.

- **Pro**: schnellster Weg zu "es laeuft", viel Gizmo-Code direkt
  wiederverwendbar, eine Codebasis fuer beide Plattformen.
- **Contra**: ImGui ist eine Desktop-Debug-UI. Keine Systemtastatur, kein
  natives Scrolling, keine Accessibility, kein Dark-Mode-Handling, fuehlt
  sich auf dem Tablet fremd an. Genau das Gegenteil des Projektziels.

### Empfehlung
**Option A**, aber mit einem Trick: Der Viewport bekommt intern *trotzdem*
ImGui fuer die reinen In-3D-Overlays (Gizmo-Handles, Mess-Labels), weil dort
der Prusa-Code direkt wiederverwendbar ist. Alles was Chrome ist - Toolbar,
Objektliste, Profil-Einstellungen, Dialoge - wird nativ. So bekommt man
90% des Gefuehls von Option A bei deutlich weniger Arbeit als einem
kompletten Gizmo-Neubau.

---

## 4. Bindeglied: das C-ABI

Kernstueck der Architektur ist eine schmale, stabile C-Schnittstelle
(`psmobile_core.h`), weil C++-Symbole weder ueber JNI noch ueber Swift
sauber tragen.

Grob:

```c
psm_session*  psm_session_create(const char* datadir);
int           psm_model_load(psm_session*, const char* path);
int           psm_preset_apply(psm_session*, const char* bundle_id, const char* preset);
int           psm_slice_start(psm_session*, psm_progress_cb, void* user);
void          psm_slice_cancel(psm_session*);
int           psm_gcode_export(psm_session*, const char* out_path);
/* Viewport laeuft separat und rein in C++, ohne ABI-Overhead pro Frame */
```

Wichtig: Der Viewport geht **nicht** durch das ABI. Pro Frame ueber JNI zu
gehen waere ein Performancefehler. Die UI schickt nur Kommandos hinunter,
das Rendering bleibt komplett nativ.

---

## 5. Touch- und Stift-Konzept (der eigentliche Mehrwert)

PrusaSlicer-Bedienung ist maus-zentriert: Rechtsklick, Hover-Tooltips,
Tastenkuerzel, dichte Menues. Nichts davon existiert auf einem Tablet.

### Gesten-Grammatik (Vorschlag)
| Geste | Aktion |
| --- | --- |
| 1 Finger Tap | Objekt auswaehlen |
| 1 Finger Drag auf Objekt | Objekt in XY verschieben (bettgebunden) |
| 1 Finger Drag auf leere Flaeche | Kamera orbit |
| 2 Finger Drag | Kamera pan |
| 2 Finger Pinch | Zoom |
| 2 Finger Rotate | Kamera-Roll |
| Long-Press | Radial-/Kontextmenue am Finger |
| Stift-Tap | praezise Auswahl, kein Orbit |
| Stift-Drag | **Malen** (Support/Seam/MMU) |
| Stift-Druck | Pinselradius |
| Stift-Hover (Pencil) | Vorschau des Pinselflecks |
| Stift + Doppeltipp am Schaft | Werkzeugwechsel Malen/Radieren |

### Warum der Stift hier wirklich etwas bringt
Die Paint-Werkzeuge von PrusaSlicer - Supports aufmalen, Naht setzen,
MMU-Farben malen - sind mit der Maus fummelig und mit dem Stift
*besser als auf dem Desktop*. Das ist kein Kompromiss-Port, das ist ein
Feature, das die Mobilversion dem Desktop voraushat. Das sollte das
Aushaengeschild der App werden.

### UI-Layout
- Bett-Ansicht fuellt den Bildschirm, keine feste Sidebar.
- Werkzeuge in einer daumenreichbaren Leiste unten (Phone) bzw. seitlich
  links (Tablet, Hand ruht auf dem Geraet).
- Einstellungen als Bottom-Sheet in drei Tiefen: *Einfach* (5 Regler),
  *Erweitert*, *Experte* (volle PrusaSlicer-Parameterliste, generiert aus
  `PrintConfig.cpp` - die ist bereits datengetrieben und laesst sich
  automatisch in eine mobile Liste uebersetzen).

---

## 6. Die harten Probleme

### 6.1 Lizenz und App Store (das groesste nicht-technische Risiko)
PrusaSlicer steht unter **AGPL-3.0**. Apples App-Store-Nutzungsbedingungen
beschraenken die Nutzung pro Apple-ID und sind damit nach verbreiteter
Auslegung (und nach Auffassung der FSF) unvereinbar mit GPLv3/AGPLv3 -
der bekannte VLC-Fall. Konsequenzen:

- Android/Google Play und F-Droid: unkritisch, GPL-Apps sind dort ueblich.
- iOS App Store: rechtlich heikel. Mogliche Wege: Einverstaendnis von
  Prusa Research als Rechteinhaber (sie halten das Copyright am Grossteil),
  oder Verteilung ueber TestFlight/Sideloading/alternative EU-Marktplaetze,
  oder iOS bewusst zurueckstellen.
- Zusaetzlich: **Alle Aenderungen muessen offengelegt werden**, AGPL greift
  auch bei Netzwerknutzung.

Das ist eine Entscheidung, keine technische Frage - sie muss aber *vor*
dem iOS-Aufwand fallen, nicht danach.

### 6.2 iOS baut nicht unter Windows
Ein iOS-Build braucht zwingend macOS mit Xcode. Von diesem Rechner aus
geht Android vollstaendig, iOS gar nicht. Optionen: Mac vorhanden,
GitHub-Actions-macOS-Runner, oder gemieteter Mac-Cloud-Runner.

### 6.3 Speicher
Slicing ist speicherhungrig. iOS killt Apps per Jetsam bei ~1,5-3 GB je
nach Geraet, Android ist etwas gnaediger, aber nicht viel. Ein
50-MB-STL mit 0,1 mm Layern kann das sprengen. Gegenmassnahmen:
Mesh-Dezimierung beim Import, Slicing in einem eigenen Prozess
(Android: `android:process` - der Kill trifft dann nicht die UI),
aggressives Streaming des G-Codes statt alles im RAM.

### 6.4 Dependency-Cross-Build
Der `deps/`-Baum hat 29 Abhaengigkeiten. Fuer Android/iOS relevant:
- **Machbar mit Arbeit**: Boost, TBB, Eigen, Expat, PNG, JPEG, ZLIB,
  Qhull, NLopt, CGAL, GMP, MPFR, Cereal, NanoSVG, json, LibBGCode,
  heatshrink, Blosc, CURL, OpenSSL
- **Faellt fuer v1 weg**: wxWidgets (GUI weg), GLEW (GLES nutzt native
  Header), OCCT (STEP aus), OpenVDB+OpenEXR (nur SLA-Hollowing),
  OpenCSG, z3, Catch2
Das halbiert den Cross-Build-Aufwand.

### 6.5 Rechenzeit
Ein Modell, das auf dem Desktop 30 s braucht, braucht auf einem Handy
eher 3-5 Minuten. Das ist kein Bug, das ist Physik. Die UI muss darauf
ausgelegt sein: Hintergrund-Slicing mit Fortschritt, Foreground-Service
auf Android, und ein *optionaler* Remote-Slicing-Modus, der die Arbeit an
einen PC oder Server im LAN abgibt. Letzteres passt sehr gut zum
vorhandenen `PrusaFarm-PrusaLink-Control`-Projekt.

---

## 7. Was v1 koennen sollte - und was nicht

### Drin
- STL / 3MF / OBJ importieren (Files-App, Share-Sheet, Download)
- Objekte platzieren, skalieren, drehen, spiegeln, duplizieren
- Auto-Arrange (`libnest2d` ist bereits dabei)
- Prusa-Profile gebuendelt, FDM
- Slicen mit Fortschritt und Abbruch
- G-Code-Preview (via `libvgcode`, klein und schon fast fertig)
- Export in Files/iCloud/Drive **und** direkt an PrusaLink/PrusaConnect

### Bewusst draussen fuer v1
- SLA / Resin (zieht OpenVDB + OpenEXR nach)
- STEP-Import (zieht OCCT nach)
- Custom-Profil-Editor in voller Desktop-Tiefe
- Multi-Material-Painting (kommt in v2 - das ist die Stift-Killer-Funktion,
  aber sie braucht einen stabilen Viewport als Basis)
- Netzreparatur ueber die eingebaute Basis hinaus

---

## 8. Realistische Groessenordnung

| Meilenstein | Ergebnis | Aufwand |
| --- | --- | --- |
| M0 | Projektgeruest, Entscheidungen, Toolchain | klein |
| M1 | libslic3r + Deps bauen fuer Android arm64 | gross, zaeh |
| M2 | Headless: STL rein, G-Code raus, auf dem Geraet | mittel |
| M3 | C-ABI + JNI, Android-App-Huelle die slicen kann | mittel |
| M4 | GLES-Viewport: Bett, Modell, Kamera | gross |
| M5 | Touch-Gesten, Auswahl, Transform-Gizmos | gross |
| M6 | G-Code-Preview via libvgcode | mittel |
| M7 | Profil-UI, Export, PrusaLink | mittel |
| M8 | iOS-Ableger (setzt Mac + Lizenzentscheidung voraus) | gross |
| M9 | Stift-Painting (v2-Aushaengeschild) | gross |

Das ist ein Projekt in der Groessenordnung von Monaten, nicht Tagen.
Der Wert liegt darin, dass M1-M3 einen echten, testbaren Beweis liefern -
ein Handy, das eine STL zu G-Code slict - und ab da jeder Schritt
sichtbar ist.

---

## 9. Der ehrliche Satz dazu

Es gibt einen Grund, warum es das noch nicht gibt: nicht das Slicing, das
geht. Sondern die 166k Zeilen GUI, die nicht mitkommen. Wer sagt "wir
portieren PrusaSlicer", meint faktisch "wir bauen einen neuen Slicer-Client
um den Prusa-Kern herum". Das ist machbar und lohnend - aber es sollte von
Anfang an so heissen, damit der Zuschnitt stimmt.
