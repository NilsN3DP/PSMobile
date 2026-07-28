# Entscheidungen (ADR)

Stand: 2026-07-28

Kurzformat: Entscheidung, Begruendung, Konsequenz. Aenderungen bitte als
neuen Eintrag anhaengen, alte nicht ueberschreiben.

---

## E-01 - Zuschnitt: neuer Client, nicht Port der Anwendung

**Entscheidung**: PSMobile ist ein neuer Touch-Client um den PrusaSlicer-Kern,
kein Port der PrusaSlicer-Anwendung.

**Begruendung**: `src/libslic3r` (190k LOC) hat null wxWidgets-Includes und ist
portables C++17. `src/slic3r` (166k LOC, 404 Dateien) ist vollstaendig
wxWidgets-gebunden; wxWidgets hat keinen Android-Port und `wxOSX/iPhone` ist
ein Stub. Die GUI-Schicht kann also technisch nicht mitkommen.

**Konsequenz**: Die Desktop-GUI ist Referenz und Vorlage, nicht Portierziel.
Logik, die faelschlich in der GUI-Schicht sitzt (z. B. Netzwerk, Profil-
Verwaltung), muss identifiziert und nach unten gezogen werden.

---

## E-02 - UI-Stack: nativ pro Plattform ueber gemeinsamem C++-Viewport

**Entscheidung**: Kotlin/Jetpack Compose (Android) und SwiftUI (iOS) fuer die
gesamte Chrome-UI. Der 3D-Viewport ist eine gemeinsame C++-Codebasis auf
OpenGL ES, eingebettet per `GLSurfaceView` bzw. `MTKView`/`CAEAGLLayer`.
In-3D-Overlays (Gizmo-Handles, Mess-Labels) duerfen Dear ImGui nutzen, weil
dort PrusaSlicer-Code direkt wiederverwendbar ist.

**Begruendung**: Der erklaerte Projektzweck ist gute Touch- und Stift-
Bedienung. Apple Pencil (Druck, Neigung, Hover, Doppeltipp) und S-Pen sind
nur ueber die nativen Eingabe-APIs vollstaendig erreichbar. Flutter schiebt
genau in diesem Pfad eine Platform-View-Zwischenschicht ein und bildet
Stiftdruck schwaecher ab; eine reine ImGui-Oberflaeche waere schnell gebaut,
fuehlt sich auf dem Tablet aber fremd an und hat keine Systemtastatur,
kein natives Scrolling und keine Accessibility.

**Konsequenz**: UI-Arbeit faellt zweimal an. Das wird bewusst in Kauf
genommen. Gegenmassnahme: alles, was nicht Darstellung ist, lebt unterhalb
des C-ABI und wird nur einmal gebaut.

---

## E-03 - Bruecke: schmales C-ABI, Viewport bleibt aussen vor

**Entscheidung**: Eine stabile C-Schnittstelle `psmobile_core.h` als einzige
Grenze zwischen App und Kern. Der Viewport laeuft komplett in C++ und geht
**nicht** pro Frame durch das ABI.

**Begruendung**: C++-Symbole tragen weder ueber JNI noch ueber Swift sauber.
Pro-Frame-Aufrufe ueber JNI waeren ein Performancefehler.

**Konsequenz**: Die UI schickt nur Kommandos hinunter und bekommt Zustand
und Fortschritt zurueck. Rendering und Eingabeverarbeitung im Viewport
bleiben nativ.

---

## E-04 - Beide Plattformen parallel

**Entscheidung**: Android und iOS werden parallel entwickelt. Mac fuer
iOS-Builds ist vorhanden.

**Konsequenz**: iOS-Targets, CMake-iOS-Toolchain und Swift-Bridge werden von
Anfang an mit angelegt. Android bleibt die fuehrende Plattform fuer die
Fehlersuche, weil auf dem Windows-Arbeitsplatz nur Android baubar ist.
**Offen**: E-06 muss beantwortet werden, bevor iOS-Auslieferung geplant wird.

---

## E-05 - Build-Umgebung: Docker auf dem Unraid

**Entscheidung**: Der Android-Cross-Build laeuft in einem reproduzierbaren
Docker-Image auf `localunraid` (Docker 29.5.2, 20 Kerne, 31 GB RAM).

**Begruendung**: Boost-b2 und die autotools-basierten Dependencies
cross-kompilieren unter Linux deutlich schmerzfreier als unter Windows.
Der Server hat mehr Kerne als der Arbeitsplatz und das Projektverzeichnis
liegt ohnehin auf ihm.

**Konsequenz**: Alle Build-Schritte sind Skripte im Image, nicht
Handgriffe. Pfad auf dem Server: `/mnt/user/N3DP/KI Projekte/PSMobile`.
Docker-Image-Pool hat nur ~40 GB frei - Build-Workspace gehoert aufs Array,
nicht in das Image.

---

## E-06 - Lizenz: offen, blockiert iOS-Auslieferung

**Entscheidung**: **Noch keine.** Bis zur Klaerung wird iOS nur gebaut und
lokal getestet, nicht ausgeliefert.

**Sachlage**: PrusaSlicer ist AGPL-3.0. Apples App-Store-Bedingungen
beschraenken die Nutzung pro Apple-ID und gelten nach verbreiteter
Auslegung als unvereinbar mit GPLv3/AGPLv3 (VLC-Praezedenzfall).
Zusaetzlich verlangt AGPL die Offenlegung aller Aenderungen.

**Stand 2026-07-28**: Direkter Draht zu Prusa Research vorhanden, Klaerung
laeuft. Die vier Punkte, die schriftlich zurueckkommen muessen, stehen in
`06-anfrage-prusa.md` - insbesondere Punkt 2 (Fremdanteile am Copyright),
weil eine Freigabe von Prusa nur Prusas eigenen Anteil deckt.

**Naechster Schritt**: siehe `04-lizenz-und-store.md` und
`06-anfrage-prusa.md`.

---

## E-07 - Basis ist Upstream 2.9.6, nicht der BumpMesh-Fork

**Entscheidung**: PSMobile baut auf `prusa3d/PrusaSlicer` Tag `version_2.9.6`
auf. Der lokale Fork `bump-mesh-only` liegt nur 2 Commits davor.

**Begruendung**: Saubere, rebasebare Basis. Die BumpMesh-Aenderungen sind
GUI-nah und fuer den mobilen Zuschnitt zunaechst irrelevant.

**Konsequenz**: BumpMesh-Commits koennen spaeter gezielt uebernommen werden.
Der bestehende Worktree unter `Z:\Prusa Slicer Fork\...` wird **nicht**
angefasst (siehe Arbeitsregeln der Projektfamilie).

---

## E-08 - v1-Umfang: FDM-Kern

**Entscheidung**: v1 kann Import (STL/3MF/OBJ), Platzieren/Transformieren,
Auto-Arrange, Prusa-FDM-Profile, Slicen mit Fortschritt und Abbruch,
G-Code-Preview und Export inkl. PrusaLink/PrusaConnect.

**Bewusst draussen**: SLA/Resin (zieht OpenVDB + OpenEXR nach), STEP-Import
(zieht OCCT nach), Multi-Material-Painting (v2), voller Profil-Editor.

**Konsequenz fuer den Build**: `SLIC3R_GUI=OFF`,
`SLIC3R_ENABLE_FORMAT_STEP=OFF`. Das entfernt wxWidgets, GLEW, OCCT,
OpenCSG und Catch2 aus dem Cross-Build.

**Korrektur 2026-07-28**: Der urspruengliche Plan wollte auch OpenVDB,
OpenEXR und Blosc streichen. Das war falsch, siehe E-10. z3 bleibt
ebenfalls drin, weil `libseqarrange` daran haengt und von `libslic3r`
oeffentlich verlinkt wird.

---

## E-09 - Netzwerk bleibt in der nativen Schicht, kein libcurl

**Entscheidung**: CURL und OpenSSL werden **nicht** fuer Android/iOS
gebaut. PrusaLink- und PrusaConnect-Zugriffe macht die native Schicht -
OkHttp auf Android, URLSession auf iOS.

**Begruendung**: Messung am Quelltext: `libslic3r` enthaelt **null**
Referenzen auf `curl/curl.h`. Der gesamte Netzwerkcode sitzt in
`src/slic3r/Utils/*`, also in der Desktop-GUI-Schicht, die ohnehin
entfaellt. Zwei grosse Dependencies weniger, und die nativen Stacks
koennen mehr: Systemzertifikate, Proxy-Konfiguration, Hintergrund-
Uebertragungen, WLAN-Wechsel.

**Konsequenz**: PrusaSlicers Wurzel-`CMakeLists.txt` verlangt CURL
allerdings bedingungslos. Dafuer gibt es einen Patch, der den Fund an
`SLIC3R_GUI` koppelt (`patches/`).

---

## E-10 - OpenVDB wird doch mitgebaut

**Entscheidung**: OpenVDB, OpenEXR und Blosc werden fuer Android
cross-gebaut, obwohl v1 kein SLA kann.

**Begruendung**: Beim Bauen zeigte sich, dass `SLA/Hollowing.cpp` fest an
`OpenVDBUtils.hpp` gekoppelt ist und unbedingt mitkompiliert wird. Nur
die `OpenVDBUtils*`-Quellen sind in `libslic3r/CMakeLists.txt` optional,
nicht der Rest der SLA-Schicht. OpenVDB herauszupatchen waere ein
invasiver Eingriff in den Slicer-Quelltext gewesen; das Mitbauen kostete
am Ende einen Durchlauf ohne einen einzigen Fehler.

**Konsequenz**: Groessere Bibliothek als noetig, dafuer kein Fork-Ballast
und SLA steht spaeter ohne weitere Build-Arbeit zur Verfuegung. Falls die
Groesse spaeter stoert, ist der richtige Hebel `--gc-sections` plus LTO,
nicht das Herausschneiden von Quelldateien.

---

## E-11 - Patches statt Fork

**Entscheidung**: Alle noetigen Aenderungen an PrusaSlicer liegen als
lesbare Patches in `patches/` und werden nach dem Klonen angewendet.
`external/PrusaSlicer` ist nicht eingecheckt.

**Begruendung**: AGPL verlangt die Offenlegung der Aenderungen. Ein
Patch-Stapel zeigt auf einen Blick, was wir angefasst haben, und laesst
sich auf eine neue PrusaSlicer-Version rebasen. Ein eigener Fork wuerde
das verschleiern.

**Bisher noetige Patches**:

| Patch | Grund |
| --- | --- |
| GMP/MPFR-CXXFLAGS | Rezept schiebt `-std=gnu11` auch in die C++-Flags; clang++ bricht ab |
| CURL optional | siehe E-09 |
| OpenGL/GLEW optional | auf Android gibt es kein Desktop-GL, nur GLES |
| PNG fuer Android bauen | Upstream baut libpng nur fuer MSVC/Apple, sonst System-Bibliothek - die es auf Android nicht gibt |
| Cross-Compile-Erkennung | Upstream erkennt nur Apple-Multi-Arch; sonst wird das fuer arm64 gebaute `encoding-check` auf dem x86-Host ausgefuehrt |
