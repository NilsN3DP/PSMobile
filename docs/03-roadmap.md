# PSMobile - Roadmap

Stand: 2026-07-28

Jeder Meilenstein hat ein **pruefbares** Ergebnis. Kein Meilenstein gilt als
fertig, weil Code existiert - nur, weil das Ergebnis nachweisbar eintritt.

---

## M0 - Fundament
**Ergebnis**: Projektkopf, Entscheidungen dokumentiert, reproduzierbares
Build-Image auf dem Unraid, das `aarch64-linux-android-clang++ --version`
beantwortet.

- [x] Projektstruktur und Git-Repo
- [x] Brainstorming, Architektur, Roadmap, Lizenzanalyse, UI-Konzept
- [ ] Docker-Image mit Android-NDK, CMake, Ninja
- [ ] PrusaSlicer 2.9.6 als `external/PrusaSlicer` ausgecheckt

## M1 - Der Kern baut fuer Android
**Ergebnis**: `libslic3r.a` fuer `arm64-v8a` existiert und `nm` zeigt
`Slic3r::Print::process`.

- [ ] Dependency-Cross-Build (Profil ohne wx/GLEW/OCCT/OpenVDB/OpenCSG/z3)
- [ ] libslic3r, libnest2d, libseqarrange, libvgcode kompilieren
- [ ] Portabilitaetsluecken schliessen (`Platform.cpp`, die eine
      `boost/process`-Stelle, Datadir-Aufloesung)
- [ ] `arm64-v8a` zuerst, danach `x86_64` fuer den Emulator

**Risiko**: Das ist der zaeheste Teil des Projekts. CGAL braucht GMP/MPFR,
Boost braucht ein eigenes b2-Toolset, TBB braucht die Android-Variante.
Hier wird die meiste Zeit verbrannt.

## M2 - Beweis: Handy slict
**Ergebnis**: Ein Kommandozeilenbinary auf einem echten Android-Geraet
nimmt eine STL entgegen und schreibt gueltigen G-Code. Kein UI.

- [ ] C-ABI `psmobile_core.h` definiert und implementiert
- [ ] Testbinary, per `adb push` aufs Geraet
- [ ] Referenzvergleich: G-Code identisch zum Desktop-Ergebnis
- [ ] Laufzeit und Spitzenspeicher gemessen und dokumentiert

**Das ist der eigentliche Machbarkeitsbeweis.** Alles davor ist Vorarbeit,
alles danach ist Fleissarbeit mit kalkulierbarem Risiko.

## M3 - App-Huellen
**Ergebnis**: Android-App mit Compose, die eine STL aus der Dateiauswahl
laedt, slict und den G-Code teilt. iOS-Gegenstueck baut auf dem Mac.

- [ ] Gradle-Projekt, JNI-Bruecke, Slicer in eigenem Prozess
- [ ] Foreground-Service mit Fortschrittsanzeige und Abbruch
- [ ] Xcode-Projekt, SwiftUI-Shell, Bridging-Header
- [ ] Gebuendelte Prusa-FDM-Profile

## M4 - Viewport
**Ergebnis**: Bett, Modell und Kamera auf dem Bildschirm, fluessig bei 60 fps.

- [ ] GLES-Kontext auf beiden Plattformen
- [ ] Shader aus `resources/shaders/ES/` laden
- [ ] Bettgeometrie, Gitter, Modellrendering
- [ ] Kamera: Orbit, Pan, Zoom

## M5 - Touch und Stift
**Ergebnis**: Die Gesten-Grammatik aus `05-ui-konzept-touch-stift.md` ist
vollstaendig umgesetzt und fuehlt sich richtig an.

- [ ] Gestenerkennung mit sauberer Trennung Finger/Stift
- [ ] Auswahl per Raycast-Picking
- [ ] Transform-Gizmos, daumentauglich dimensioniert
- [ ] Haptisches Feedback beim Einrasten

## M6 - G-Code-Preview
**Ergebnis**: Nach dem Slicen ist der Pfad sichtbar, mit Layer-Slider.

- [ ] `libvgcode` einbinden
- [ ] Layer-Slider und Faerbung nach Feature-Typ
- [ ] Gestaffeltes Laden (Speicher!)

## M7 - Rund machen
**Ergebnis**: Die App ist ohne Erklaerung benutzbar.

- [ ] Einstellungen in drei Tiefen (Einfach / Erweitert / Experte)
- [ ] Auto-Arrange
- [ ] Export nach Files/iCloud/Drive
- [ ] Direktupload an PrusaLink und PrusaConnect
- [ ] Optionales Remote-Slicing im LAN

## M8 - Ausliefern
**Ergebnis**: Installierbare Builds.

- [ ] Android: signiertes Release, F-Droid-tauglich
- [ ] iOS: TestFlight (Store abhaengig von E-06 / Lizenzklaerung)
- [ ] Open-Source-Compliance vollstaendig

## M9 - v2: Stift-Painting
**Ergebnis**: Supports, Naht und MMU-Farben mit dem Stift malen -
besser als auf dem Desktop.

- [ ] Triangle-Mesh-Painting auf GLES portieren
- [ ] Pinselradius ueber Stiftdruck
- [ ] Hover-Vorschau (Apple Pencil)

---

## Ehrliche Einordnung

M0 ist eine Sitzung. M1 ist der Brocken - Wochen, nicht Tage, und der
Punkt, an dem das Projekt scheitern koennte. M2 ist der Moment, ab dem man
weiss, ob es wirklich geht. M3 bis M7 sind viel Arbeit, aber ohne
grundsaetzliche Unbekannte. Insgesamt ist das ein Monatsprojekt, kein
Wochenendprojekt.
