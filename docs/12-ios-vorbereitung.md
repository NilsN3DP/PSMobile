# iOS: was vorbereitet ist und was du mieten musst

Stand: 2026-08-02.

Ziel ist ein iPad-Prototyp, ohne dass ein Mac vor Ort steht. Dieses
Dokument sagt, was du mietest, was du danach eingibst, und was
erfahrungsgemäß zuerst schiefgeht. Alles, was ohne macOS vorbereitet
werden konnte, ist vorbereitet.

## Warum überhaupt ein Mac

Xcode gibt es nur für macOS, und ohne Xcode gibt es kein iOS-SDK. Das
lässt sich nicht umgehen: die SDKs liegen in Xcode und dürfen nicht
daraus entnommen werden. Ein Linux-Container hilft hier nicht, anders
als bei Android.

macOS in einer virtuellen Maschine auf eigener Hardware laufen zu
lassen ist technisch möglich, nach Apples Lizenzbedingungen aber nur
auf Apple-Hardware erlaubt. Dazu kommt, dass ohne Apple-GPU alles in
Software gerendert wird — Xcode selbst läuft damit, der Simulator wird
zäh bis unbrauchbar. Genau den bräuchtest du aber für einen Prototyp.

## Zwei Wege

### Weg A: gemieteter Mac, stundenweise

Ein Mac mini oder eine Mac-VM bei einem Anbieter wie MacStadium,
Scaleway oder AWS EC2 Mac. Du bekommst eine echte Maschine per
Fernzugriff.

Zu beachten: AWS EC2 Mac rechnet in vollen 24 Stunden ab, weil die
Hardware pro Instanz dediziert ist. Scaleway und MacStadium sind für
einen einzelnen Tag günstiger. Preise ändern sich, deshalb stehen hier
keine.

Vorteil: du siehst den Simulator live und kannst die Oberfläche
tatsächlich bedienen.

### Weg B: macOS-Läufer bei GitHub Actions

Kein eigener Rechner, sondern eine Maschine pro Lauf. Abgerechnet nach
Minute, für öffentliche Repos kostenlos. Der Ablauf ist als
`.github/workflows/ios-simulator.yml` fertig hinterlegt: er baut alles,
startet die App im iPad-Simulator und legt Screenshots als Artefakt ab.

Voraussetzung: das Repo muss auf GitHub liegen. Bisher liegt es nur
lokal auf dem Unraid.

Vorteil: kein Einrichtungsaufwand, wiederholbar, und der Cache spart ab
dem zweiten Lauf die Stunden für die Dependencies.

Nachteil: du siehst nur Bilder, keine Bedienung.

**Empfehlung:** Weg B für den ersten Blick, Weg A sobald du wirklich
etwas ausprobieren willst.

## Was auf dem Mac zu tun ist

Ein einziges Skript, in Schritten und wiederholbar:

```bash
git clone <repo> psmobile && cd psmobile
build/scripts/macos-bootstrap.sh
```

Es prüft die Werkzeuge, holt PrusaSlicer 2.9.6, wendet die Patches an,
baut Dependencies und Kern, legt die Profile ins Bundle und erzeugt das
Xcode-Projekt. Jeder Schritt lässt sich einzeln aufrufen
(`tools`, `sources`, `deps`, `core`, `project`), und was fertig ist,
wird übersprungen — bricht etwas ab, kannst du nach dem Beheben einfach
erneut starten, ohne die schon gebauten Stunden zu verlieren.

Standardziel ist der **Simulator** (`SIMULATORARM64`). Das ist die
günstigere Wahl: kein Apple-Entwicklerkonto, keine Provisionierung. Für
ein echtes iPad brauchst du `PSM_IOS_PLATFORM=OS64` und ein
Signierprofil, also ein Entwicklerkonto.

## Was erfahrungsgemäß zuerst schiefgeht

Diese Punkte stammen aus dem Android-Port, wo dieselben Bibliotheken
über dieselben Wege gebaut wurden.

**GMP und MPFR.** Sie bauen per autotools statt CMake und sind der
empfindlichste Teil. Der eingecheckte Patch trennt bereits C- von
C++-Flags — ohne das brach der Cross-Build unter Android ab. Sollte
`configure` trotzdem stolpern, ist das die erste Stelle zum Nachsehen;
`build/scripts/mk-autotools-wrappers.sh` zeigt, wie es unter Android
gelöst wurde.

**Suchpfade der Dependencies.** Unter Android hat das zweimal Zeit
gekostet: die Toolchain setzt die Suchwurzeln um, und PrusaSlicers
CMakeLists leert `CMAKE_PREFIX_PATH`. Die iOS-Toolchain setzt
`CMAKE_FIND_ROOT_PATH` und `CMAKE_PREFIX_PATH` deshalb schon aus
`PSM_DEPS_PREFIX`. Wenn eine Bibliothek trotzdem nicht gefunden wird,
liegt es fast sicher daran und nicht daran, dass sie fehlt.

**`-fno-aligned-allocation`.** libslic3r setzt das für Apple-Ziele. Bei
Deployment-Target 15.0 ist es nicht mehr nötig und kann stören.

**Simulator und Gerät sind getrennte Bauten.** Die Dependencies müssen
für jede Plattform einzeln gebaut werden. `project.yml` kennt beide
Pfade, damit der Linker beim Simulatorbau nicht in eine falsche
Fehlersuche führt — vorher stand dort nur das Gerät, was sich als Haufen
fehlender Symbole gezeigt hätte statt als fehlender Pfad.

**Der lange Teil ist der Dependency-Bau.** Unter Android waren es
mehrere Stunden. Plane das ein, bevor du eine Maschine stundenweise
mietest, und nutze bei GitHub den Cache.

## Was der Prototyp zeigen wird und was nicht

Der Kern ist plattformneutral: `libslic3r` und `psmobile_core` sind
dieselben wie unter Android, die C-ABI ist die einzige Grenze. Slicing
sollte also funktionieren, sobald es baut.

Die Oberfläche dagegen ist auf Android in Compose geschrieben. Auf iOS
liegt nur ein SwiftUI-Gerüst mit dem Einstieg und einem Zustandshalter
(`ios/PSMobile/`). Der Prototyp zeigt also den Kern auf iOS-Hardware,
nicht die fertige Oberfläche. Simple Mode, Advanced Mode, das
Modelle-Blatt, die Einstellungsseiten — all das müsste in SwiftUI
nachgebaut werden.

Wenn du vorher nur wissen willst, wie die Oberfläche in iPad-Maßen
aufgeht, geht das ohne Mac: den Android-Emulator auf 834 × 1194 oder
1024 × 1366 Punkte stellen. Das ist kein iOS, beantwortet aber die
Layoutfrage.

## Vor jeder Veröffentlichung

PrusaSlicer steht unter AGPL-3.0. Die Auslieferung über den App Store
ist rechtlich ungeklärt, siehe `04-lizenz-und-store.md`. Bauen und
lokal testen ist unproblematisch, veröffentlichen nicht.
