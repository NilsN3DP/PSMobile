# Arbeitsjournal

**Wer hier arbeitet, liest diese Datei zuerst und schreibt am Ende jedes
Schrittes hinein.**

Am 3. August 2026 haben zwei Agenten denselben Baum bearbeitet, ohne
voneinander zu wissen. Ergebnis: ein Schlüsselbund-Speicher zweimal
gebaut, PrusaLink von beiden Seiten halb, eine Zusammenführung mit elf
Konflikten — und ein Dateiabgleich, der fremde Arbeit überschrieben hat.
Nichts davon ist verlorengegangen, aber es hat mehrere Stunden gekostet.

Dieses Journal soll das verhindern. Es ist kein Bericht für später,
sondern die Absprache währenddessen.

## Regeln

1. **Vor dem ersten Schritt:** die letzten Einträge lesen und
   `git log --oneline -20` ansehen. Wer denselben Bereich anfassen will,
   den ein anderer offen hat, sucht sich etwas anderes oder schreibt es
   hier als Frage hinein.
2. **Nach jedem fertigen Schritt:** ein Eintrag. Ein Schritt ist das,
   was einen Commit wert ist.
3. **Was in einen Eintrag gehört:** was gemacht wurde und warum, welche
   Dateien, welche Tests gelaufen sind, was offen blieb — und was gerade
   *nicht* angefasst werden sollte.
4. **Fehler stehen hier genauso wie Erfolge.** Ein Journal, in dem nur
   gelingt, ist beim nächsten Mal wertlos.
5. **Kein `sync-mac.sh` ohne Blick auf den Mac.** Das Skript hat seit
   dem 3. August eine Sperre; sie lässt sich mit `--trotzdem` umgehen,
   und genau das ist der Moment zum Nachdenken.
6. **Arbeit wandert per Git, nicht per Dateikopie.** Zwischen Unraid und
   Mac über ein Bündel:
   `git bundle create /pfad/x.bundle main`, drüben
   `git fetch /pfad/x.bundle 'refs/heads/main:refs/remotes/unraid/main'`
   und `git checkout -B main unraid/main`. Ein Bündel trägt Commits —
   was nicht committet ist, kommt nicht mit.

## Wer arbeitet woran

| Bereich | zuletzt | Stand |
|---|---|---|
| iOS Simple Mode, Viewport, Projekte, Slicen | Claude | steht, 30 Tests |
| iOS ColorMix und INDX-Positionen | Codex | steht |
| PrusaLink: Digest, Regeln, Client, Schlüsselbund | beide, zusammengeführt | steht, ohne echten Drucker geprüft |
| Profilupdate-Politik (gemeinsames Modul) | Codex | Regel da, keine Oberfläche |
| iOS Advanced: Werkzeugleiste, Objektbaum | Claude | steht, 4 Tests |
| Bemalen: Stützen, Naht, MMU | Claude | steht auf iOS, 2 Tests |
| Sonderwerte auf iOS (Bett, Reinigung) | Claude | zwei von fünf |

---

## 2026-08-03

### Claude — iOS von der Ersteinrichtung bis zum G-Code

Der Tag begann mit einer iOS-App, die ein Druckbett zeichnen konnte, und
endete mit einer, die einen Druck von Anfang bis Ende durchträgt.

Gebaut: Simple Mode mit allen Panels, App-Einstellungen mit Startmodus,
Startbildschirm, Modelle-Blatt (anordnen, klonen, entfernen, Mehrbett),
Objektleiste (schneiden, teilen, ablegen, einpassen), Projekte als 3MF,
Zurück/Wiederholen, Schnelleinstellungen, Material je Extruder,
Zusammenfassung nach dem Slicen mit G-Code-Ausgabe, G-Code-Vorschau mit
Schichtregler, umschaltbare Gizmos.

Ins gemeinsame Modul gezogen: `SliceSummary`, `SimpleModelSheetState`,
`Lang`, `TabsCatalog` und weitere — vierzehn Regelsätze insgesamt.

**Drei Funde, die ohne Tests durchgegangen wären:**

- Ein `accessibilityIdentifier` an einem Stapel vererbt sich in SwiftUI
  an *jedes* Kind und überschreibt deren eigene. Danach hieß jeder Knopf
  wie der Bildschirm. Abhilfe: `PSMarke`, eine unsichtbare Marke.
- Die erzeugte Test-STL schrieb pro Dreieck sechs statt zwölf Zahlen.
  libslic3r meldete dazu nur „Loading of a model file failed".
- Ein gedrehter SwiftUI-Regler stimmt in seinen Trefferflächen nicht mehr
  mit dem überein, was man sieht — weder von Hand noch im Test bedienbar.

**Eine Zeile mit großer Wirkung:** der GL-Kontext lief mit GLES 2,
libvgcode braucht GLES 3. Sichtbar wurde das erst beim Umschalten auf die
Vorschau, als „Unable to compile vertex shader".

Tests: 30 auf iOS (Einheit und Bedienung), Android unverändert grün.

### Claude — PrusaLink: Digest und Regeln ins gemeinsame Modul

MD5 und HTTP-Digest nach RFC 7616 liegen jetzt in Kotlin im gemeinsamen
Modul, geprüft gegen die Vektoren aus RFC 1321 und gegen Pythons hashlib
an den Blockgrenzen. MD5 von Hand, weil Android `MessageDigest` und iOS
CommonCrypto hätte — zwei Wege zum selben Ergebnis, beide nur über
plattformabhängigen Code. Die cnonce kommt aus dem kryptografischen
Zufall der Plattform; erste Verwendung von `expect`/`actual` im Modul.

`PrusaLinkRules` hält, was keine Steckdose braucht: Adressen
zusammensetzen (ohne Schema gilt HTTPS), Dateinamen entschärfen,
Antwortcodes deuten. Android hat seine eigene Digest-Fassung abgegeben.

**Nicht am Gerät geprüft.** Ohne echten Drucker ist nur belegt, dass die
Rechnung stimmt und dass Klartext ohne Freigabe abgelehnt wird.

### Claude — die beiden Arbeitslinien zusammengeführt

Auf dem Mac lief parallel `codex/ios-parity-beta-rc` mit achtzehn
Commits. Zusammengeführt nach dem Grundsatz, dass jede Entscheidung genau
einmal existiert:

- `PrinterCredentialStore` bleibt der reine Schlüsselbund-Zugang aus
  Codex' Zweig; meine Druckerliste steht als `PrinterStore` daneben.
- `SetupScreen` behält die Fassung ohne Typparameter — die generische
  Gruppierung überlebt die Brücke nach Swift nicht.
- `SimpleModelSheetState` liegt im gemeinsamen Modul und arbeitet mit
  Listen statt Mengen, aus demselben Grund.

**Mein Fehler:** `sync-mac.sh` schiebt Dateien per tar und überschreibt
ohne Rückfrage. Es hat Codex' Arbeitsstand an neun Dateien überschrieben.
Gerettet als Commit `bc8cd68` auf dem Zweig
`rettung/ueberschrieben-2026-08-03`. Das Skript bricht jetzt ab, wenn der
Mac uncommittete Änderungen hat.

**Zweiter Fund beim Aufräumen:** das erzeugte Xcode-Projekt und 18 MB
PrusaSlicer-Ressourcen lagen im Repo. Beide entstehen aus Quellen, die
schon da sind. Jetzt in `.gitignore`; `stage-resources.sh ios` und
`xcodegen` stellen sie in Sekunden wieder her.

**Dritter Fund:** beim Zusammenführen ging die Testhost-Zeile in
`ios/project.yml` verloren. Der Testlauf meldete daraufhin Erfolg und
führte null Tests aus — die unangenehmste Art zu scheitern, weil sie wie
Bestehen aussieht. Wer hier arbeitet: bei jedem Testlauf auf die Zeile
`Executed N tests` sehen, nicht nur auf `TEST SUCCEEDED`.

**Und noch einer, mit Auflösung:** Codex hatte einen Unit-Test für den
Schlüsselbund geschrieben und in `daaa18f` wieder herausgenommen. Ich bin
in dieselbe Falle gelaufen und habe zwei Anläufe gebraucht, um zu sehen,
warum:

- *Ohne* Testhost meldet jeder Schlüsselbund-Aufruf `-34018`,
  `errSecMissingEntitlement` — ein Testbündel ohne App-Prozess hat dafür
  keine Berechtigung.
- *Mit* Testhost liegt PSMShared zweimal im Prozess. Kotlin/Native-Klassen
  sind dann doppelt registriert, und der Lauf bricht vor dem ersten Fall
  ab: eine Wand aus „Class … is implemented in both".

Es gibt in diesem Aufbau also keinen Weg, den Schlüsselbund als Einheit
zu prüfen. Die Prüfung steht jetzt in der Bedienung: Passwort eingeben,
Bildschirm verlassen, zurückkommen — steht es wieder da, hat der
Schlüsselbund gehalten. Für das Einheitsziel gilt weiterhin: nur reine
Swift-Dateien, mitkompiliert, ohne Abhängigkeit zur App.

**Merke fürs Testen:** immer auf die Zeile `Executed N tests` sehen. Es
gab heute zwei Läufe, die `TEST SUCCEEDED` meldeten und null Tests
ausführten — einmal, weil das Xcode-Projekt nicht neu erzeugt war,
einmal, weil die Testdateien nur im Arbeitsstand lagen und das Bündel
nur Commits trägt.

### Claude — PrusaLink-Oberfläche und ihre Prüfungen

Druckerschirm zum Einrichten und Senden, erreichbar aus der
Zusammenfassung nach dem Slicen und aus dem Arbeitsbereich. Drei
Bedienungstests: ein Drucker lässt sich anlegen und erscheint mit
`https://` davor, das Passwort überlebt im Schlüsselbund, und eine
Klartextadresse wird ohne ausdrückliche Freigabe gar nicht erst gesendet.

Die `Info.plist` gibt nur das lokale Netz frei (`NSAllowsLocalNetworking`),
nicht das offene Internet. Selbstsignierte Zertifikate werden **nicht**
blind akzeptiert — wer HTTPS mit eigenem Zertifikat fährt, kommt derzeit
nicht durch. Das ist bewusst offen gelassen und wäre der nächste Punkt.

Stand: 33 Tests auf iOS grün, Android grün.

**Nicht am Gerät geprüft.** Ohne echten Drucker ist nur belegt, dass die
Rechnung stimmt und die Ablehnungen greifen.

### Claude — Advanced-Arbeitsbereich

Der Advanced Mode war eine Notlösung aus der Zeit, als auf iOS noch gar
nichts lief: Viewport, Liste, ein Knopf. Der Simple Mode konnte
inzwischen mehr als der Modus, der für diejenigen da ist, die alles
sehen wollen.

Jetzt: Werkzeugleiste mit eigenen Einstiegen für Druck-, Filament- und
Druckereinstellungen, Anordnen, Ansicht, Drucker, Moduswechsel. Rechts
der Inspektor — Gizmowahl, Größe in Prozent **und** Millimetern, Drehung
je Achse mit Vierteldrehungen, Ablegen, Einpassen, Spiegeln, Kopien,
Bettwechsel, und der Objektbaum mit Extruder je Teil.

Zwei Entscheidungen, die man später sonst nicht mehr sieht:

- Die Zahlenfelder übernehmen erst beim Verlassen. Bei jedem Tastendruck
  zu übernehmen hieße, dass aus „12" beim Tippen von „125" kurz die 12
  wird und das Modell zweimal springt.
- Auf schmalen Fenstern klappt die Seite über das Bett statt daneben.

**Dreimal dieselbe Falle an einem Tag.** Der `accessibilityIdentifier`
am umgebenden Stapel — heute früh bei drei Bildschirmen behoben,
abends beim Inspektor wieder gemacht. Die Gizmo-Knöpfe hießen danach
alle `advanced.objectTree`, und drei Tests fanden nichts. Es steht seit
heute in diesem Journal, und ich habe es trotzdem wiederholt: **wer in
SwiftUI eine Kennung setzt, setzt sie auf `PSMarke`, nie auf einen
Container.**

**Und ein Folgefehler daraus:** die Viewport-Tests zielten auf
`arbeitsbereich` — seit das eine Marke von einem Punkt Größe ist, lehnte
XCUITest das Spreizen mit „maximum possible scale 0.90" ab. Der Viewport
hat jetzt eine eigene Kennung; an ihm ist das gefahrlos, weil er eine
einzelne UIView ohne SwiftUI-Kinder ist.

Stand: **37 Tests auf iOS grün**, Android grün.

### Claude — schmale Geräte und die Sonderwerte

**Responsive.** Dieselben Wege wie sonst, nur auf einem iPhone, und mit
zwei Zusicherungen, die auf einem großen Bildschirm nie auffallen: liegt
das Element ganz im Fenster, und lässt es sich treffen. Drei Tests, alle
grün — die Skalierung trägt. Ein Bildschirmfoto hat trotzdem etwas
gefunden, was kein Test gemeldet hätte: die Beschriftung der
Schrittleiste war einzeilig und abgeschnitten („Objekte impor…").

**Sonderwerte.** Erster Anlauf war, `bed_shape` und
`wiping_volumes_matrix` im allgemeinen Renderer abzufangen. Das geht
nicht — **`bed_shape` steht gar nicht in `tabs.json`.** PrusaSlicer baut
die Bettform am Desktop mit einem eigenen Widget statt als Zeile im
Parameterbaum; deshalb hat auch Android dafür einen getrennten Bereich.
Wer hier weitermacht: dasselbe gilt für Ramming, Ersetzungen und
Druckerhost — sie brauchen ebenfalls eigene Seiten, nicht Einträge im
Renderer.

Jetzt eine zusätzliche Seite am Ende der Druckerliste, als einzige
handgebaute. Die Bettform als Breite und Tiefe, solange sie rechteckig
ist, sonst als Punktliste — erfinden kann die Oberfläche eine Form
nicht. Die Reinigungsmengen als Gitter mit Zeilen- und Spaltenköpfen,
die Diagonale leer.

Offen von den fünf: Ramming, Ersetzungen, Druckerhost.

### Claude — Bemalen, und die Lücke auf Android

**Bemalen auf iOS.** Stützen erzwingen oder sperren, Naht setzen,
MMU-Farben. Der Kern konnte das seit langem — Facette treffen, Pinsel
mit Radius, zählen, löschen —, aber der Viewport lieferte Treffer, und
niemand hörte zu.

Am Desktop hängt das an einer Gizmo-Leiste mit Mausrad für den Radius.
Auf einem Tablet gibt es kein Mausrad, also ein Regler. Zwei
Entscheidungen, die man sonst später nicht mehr sieht: „Aus" ist ein
eigener Zustand (solange gemalt wird, dreht ein Wischen die Kamera
nicht mehr — das muss abstellbar sein), und die Zahl der markierten
Facetten steht daneben, weil ein paar gefärbte Dreiecke auf dunkler
Fläche leicht zu übersehen sind.

**Android hat aufgeholt, wo es hinterherhing.** Der Simple Mode startete
den Schnitt bis heute stumm. Jetzt dasselbe Blatt wie auf iOS, aus
denselben Regeln — `SliceSummary` im gemeinsamen Modul. Und statt eines
ausgegrauten Knopfes nennt auch Android, was fehlt.

**Beobachtung zum Mac, nicht zum Code:** in einem vollen Testlauf sind
zweimal Testrunner „unexpectedly exited". Keine Absturzberichte, aber
nur ~290 MB freier Speicher bei zwei laufenden Simulatoren. Der zweite
Simulator ist jetzt aus. Wer das wieder sieht: erst `vm_stat` ansehen,
bevor der Fehler im Code gesucht wird.

### Claude — was Tests auf iOS voneinander wissen

Ein voller Lauf hat etwas gefunden, das einzeln nie auffällt: der
Bettform-Test sah 300 mm, wo 250 stehen sollten. Kein Fehler im Code —
**PrusaSlicer behält Änderungen an einem Preset über den App-Start
hinaus**, und ein anderer Test in derselben Klasse ändert die Bettform
absichtlich. Der erste Test sah beim nächsten Lauf, was der zweite
hinterlassen hatte.

Daraus zwei Regeln fürs Testen auf iOS:

1. **Keine festen Werte aus Profilen erwarten.** Prüfe, dass überhaupt
   etwas Sinnvolles kommt, und prüfe Änderungen als Rundreise: setzen,
   Bildschirm verlassen, zurückkommen, vergleichen.
2. **Was der Test verändert, überlebt ihn.** UserDefaults, Keychain und
   Presets liegen im Simulator und bleiben. Wo ein sauberer Start nötig
   ist, gibt es Startargumente — `-psm-reset-setup`,
   `-psm-reset-printers` —, und ein neues gehört dazu, wenn ein Test
   sonst vom vorigen abhängt.

### Stand am Ende des 3. August

**iOS** trägt einen Druck von Anfang bis Ende: Ersteinrichtung,
Startbildschirm, Simple Mode mit allen Panels, Advanced-Arbeitsbereich
mit Objektbaum, alle 20 Einstellungsseiten, Schnelleinstellungen,
Projekte als 3MF, Zurück/Wiederholen, Slicen mit Zusammenfassung,
G-Code-Ausgabe, G-Code-Vorschau, Bemalen, PrusaLink, ColorMix und
INDX-Positionen, Bettform und Reinigungsmengen.

**Offen:** drei der fünf Sonderdialoge (Ramming, Ersetzungen,
Druckerhost), Miniaturbilder in der Objektliste, Rahmenauswahl,
MMU-Statistik, Oberfläche für die Profilupdates.

Nachtrag: die **variablen Schichthöhen** stehen seit dem späten Abend.
Stützstellen als Zahlenpaare statt einer Kurve — mit dem Finger ist eine
Kurve nicht zu treffen. Daneben ein Balken über die Modellhöhe, dünn
dunkel und dick hell, wie in PrusaSlicers eigener Darstellung. Die
Prüfung (mindestens zwei Punkte, streng steigende Z-Werte) liegt im
gemeinsamen Modul: der Kern lehnt alles andere ab, und ohne
vorgeschaltete Prüfung tippt jemand auf Übernehmen und es passiert
nichts.

**Für Android:** dieselbe Rechnung liegt dort noch in
`ui/LayerProfileEditorState.kt` und `ui/GeometryTools.kt`. Sie kann auf
`shared/rules/LayerProfile.kt` wechseln — die Regeln sind identisch,
nur ohne `Pair`, das die Brücke nach Swift nicht überlebt.

**Und das Wichtigste:** es lief noch nie auf echter Hardware. Alles
Geprüfte ist Simulator. Die Feature-Matrix sagt deshalb
`emulator_tested`, nicht `device_tested`.

### Side-Build-Baseline – Fix-Runde 1

Der geprüfte, lokale Side-Build-Snapshot hält die NAS-Quelle bei
`61c1b47` fest. Zugangsdaten wurden vor dem rekonstruierten Commit entfernt:
Remote-Slice-Tests verwenden nur lokal gesetzte `PSM_REMOTE_SLICE_HOST` und
`PSM_REMOTE_SLICE_TOKEN`; Docker verlangt sein Token aus der lokalen
Umgebung. Der Android-Regressionstest und der erneuerte
`ResourceInstallerTest` liefen mit der vorhandenen Android-Studio-JBR.

Der Host-C++-Vertragstest konnte weiterhin nicht eingerichtet werden, weil
dieser Rechner weder die benötigten Boost-1.83-Host-Abhängigkeiten noch einen
laufenden Docker-Daemon bereitstellt. Es wurden keine Buildprodukte oder
Zugangsdaten importiert.

Nachtrag Fix-Runde 1: Der isolierte Mac-Side-Build aus Commit `d281605`
hat `psm_contract_tests` für `SIMULATORARM64` neu gelinkt und auf einem
iOS-26.3-iPhone-17-Pro-Simulator ausgeführt. Der Contract-Test bestätigt
ABI 7, den einzelnen Undo-Checkpoint für die Druckerprofilmutation und die
Ablehnung eines während des Remote-Slice veralteten G-Code-Ergebnisses.

### Side-Build-Baseline – Fix-Runde 2

Der Remote-Slice-Weg hält die Designrevision nun beim Start fest und gibt
einen heruntergeladenen G-Code erst frei, nachdem der Kern genau diese
Revision akzeptiert hat. Ein inzwischen veraltetes Ergebnis landet nicht mehr
in `gcodeURL` oder `.done`, sondern endet mit einem erneuten Slice-Hinweis.

Auf dem aus Commit `7b71ee2` geklonten, isolierten Mac-Side-Build
(iOS-26.3-iPhone-17-Pro-Simulator) liefen die zwei neuen
`RemoteSliceCompletionTests` mit 0 Fehlern. Dafür wurde der Kern zuvor per
`bash build/scripts/build-ios.sh core` im Side-Build gebündelt; die fehlende
lokale Testdatei lieferte zuvor den erwarteten RED-Lauf. Der C++-Contract-Test
lief danach erneut mit `PASS: psm_contract_tests`; die Android-Regression
`:shared:allTests :app:testDebugUnitTest` lief lokal ebenfalls erfolgreich.
Der vollständige technische Nachweis steht im Task-1-Bericht.

### Side-Build-Baseline – Fix-Runde 3

Die Remote-Slice-Tests prüfen nun nicht nur eine geworfene Annahme, sondern
den tatsächlich von `SlicerModel` verwendeten Veröffentlichungswert: Erfolg
liefert die konkrete G-Code-URL, `.done` und das Remote-Flag; eine veraltete
Annahme liefert keine URL, `.failed` und kein Remote-Flag. Der RED-Lauf auf
dem Mac-Simulator fand die bewusst noch fehlende Zustands-Seam. Danach liefen
alle drei `RemoteSliceCompletionTests` mit 0 Fehlern bei vollständigem App-
Compile und Link. C++ und Android blieben unverändert; deren Nachweise aus
Fix-Runde 2 gelten fort.

### Side-Build-Baseline – Task 2: getrennte Vorschau-Identitaeten

Die Produktions-App bleibt `de.psmobile` mit dem Namen `PSMobile`. Daneben
stehen die iOS-Scheme `PSMobilePreview` und Androids `previewDebug`, beide mit
`de.psmobile.preview` und `PSMobile Preview`. Verschiedene Bundle- und
Paket-IDs geben den Apps getrennte Standard-Sandboxes fuer Einstellungen,
Zugangsdaten, Cache und Dokumente; es gibt bewusst weder eine gemeinsame
Preference-Suite, Keychain-Gruppe, App-Gruppe noch Android-`sharedUserId`.

Der erste Artefakttest war rot: Gradle kannte `assemblePreviewDebug` noch
nicht. Danach baute der Test beide APKs und prüfte sie mit `aapt2 dump
badging`; Produktions- und Vorschau-ID sowie sichtbare Namen sind getrennt,
eine gemeinsame UID fehlt. Mit der Android-Studio-JBR liefen dieser Test,
`:shared:allTests`, die Unit-Tests von `productionDebug` und `previewDebug`
und alle 15 Python-Buildtests grün.

Für iOS blieb die NAS-Arbeitskopie unberührt. Der Mac baute stattdessen den
Windows-Stand `07ae94f` in einem isolierten, abgehängten Baum unter
`/Volumes/Macintosh_HD/Users/user289137/psmobile-task2-identity-build-07ae94f`.
CMake 3.31.6, Ninja und XcodeGen lagen bereits unter `build-out/mac-tools`,
nur nicht im SSH-Pfad. Der frische SIMULATORARM64-Kern baute 77 Ziele; danach
lief der Artefakttest grün: beide `-showBuildSettings`-Abfragen, beide
Simulator-Builds, die erzeugten `Info.plist`-Dateien und Codesign-Entitlements
bestätigen die getrennten Identitaeten ohne geteilte Container- oder
Zugangsdatenrechte.

### Side-Build-Baseline – Task 2 Fix-Runde 1: Produktions-Workflows

Die neue Flavor-Dimension machte den alten Aufruf `testDebugUnitTest`
mehrdeutig. Das betraf den Docker-Helfer fuer APKs und die noch aktive
Baseline-Checkliste; der Gerätetest suchte ausserdem weiter nach dem alten
`app-debug.apk`. Der Fix benennt deshalb Debug, Release und Unit-Tests im
Build-Helfer ausdrücklich als Produktion und installiert im Gerätetest nur
`production/debug/app-production-debug.apk`, nach einer Existenzprüfung.

Zwei echte Skript-Fallen waren vorher rot: Ein Docker-Double zeichnete den
tatsächlich an Gradle übergebenen unqualifizierten Task auf; ein Fake-`adb`
zeichnete die Installation der alten APK auf. Beide Arbeitsabläufe sind nun
grün. Danach liefen der APK-Identitätstest, `:shared:allTests`, die
Production- und Preview-Unit-Tests und alle 17 Python-Buildtests mit der
Android-Studio-JBR. Alte Hinweise in abgeschlossenen Release-Nachweisen und
älteren beziehungsweise nebenläufigen Planbeispielen bleiben historische
Evidenz und wurden nicht mechanisch umgeschrieben.

### Side-Build-Baseline – Task 2 Fix-Runde 2: portable Skripte

Der Produktionshelfer war im echten Windows-Checkout trotz grünem Test nicht
portabel: Die Testkopie entfernte CRLF selbst, während Bash im Arbeitsbaum
`bash\r` sah und auch `bash -n` scheiterte. Jetzt erzwingt `.gitattributes`
LF nur für `*.sh`; mechanisch normalisiert wurden genau `build-apk.sh` und das
dazu gelesene `env.sh`. Der Test kopiert die Helfer unverändert und prüft
zusätzlich die Syntax des echten Arbeitsbaum-Skripts.

Ein zweiter RED-Fall nutzte einen völlig gültigen Projektpfad mit eckiger
Klammer. Die wildcard-fähigen PowerShell-Aufrufe sahen dadurch die vorhandene
Produktions-APK nicht. `Test-Path` und `Get-Item` arbeiten dort jetzt mit
`-LiteralPath`; der Fake-`adb` belegt weiterhin, dass nur die exakte
Produktions-APK installiert wird. Danach liefen alle 18 Python-Buildtests,
beide Android-Flavor-Unit-Suiten, der Identitäts-Artefakttest sowie Bash- und
PowerShell-Parseprüfungen grün.

### Side-Build-Baseline – Task 3: Objekt-Konfigurations-Overrides

Der C-Kern kann globale Druckparameter jetzt pro Objekt übersteuern,
zurücksetzen und als lokal/geerbt abfragen. Lesen fällt ohne Override auf die
globale Konfiguration zurück. Die Validierung arbeitet zuerst an einer
geklonten typgleichen Konfiguration, so dass ungültige Werte, unbekannte
Schlüssel und fehlende Objekt-IDs keine Teilmutation erzeugen. Wirksame
Änderungen erzeugen genau einen Verlaufseintrag, eine `config_revision` und
eine Design-Invalidierung; Reset eines nur geerbten Werts und ein identisches
Set sind erfolgreiche No-ops.

Der Contract wurde test-first geschrieben: der direkte Windows-MSVC-RED-Lauf
meldete die erwarteten fehlenden `psm_object_config_*`-Deklarationen. Der
Host-CMake-Build war weiterhin nur wegen fehlender Boost-1.83-Abhängigkeiten
nicht einrichtbar. Danach kompilierte der reine C-`psm_testcli` gegen die
neuen Deklarationen. Die ABI wurde wegen vier neuer C-Exports von 7 auf 8
erhöht; App und Kern vergleichen weiterhin dieselbe Header-Konstante.

Der Commit `97df4ddfe09918b0b8c76c293ff965b8d07432c3` lief auf dem Mac in
einem neuen, abgehängten Verzeichnis
`/Volumes/Macintosh_HD/Users/user289137/psmobile-task3-object-config-97df4dd`.
Der schmutzige Hauptcheckout blieb unangetastet; vorhandene Tool-,
Abhängigkeits- und Quellen-Caches wurden nur verlinkt. Der frische
SIMULATORARM64-Build linkte 50 Ziele einschließlich Kern, C-CLI und
Contract-Test. Auf dem iPhone-17-Pro-Simulator ergab der Lauf mit absoluten
Fixture-Pfaden `PASS: psm_contract_tests`. Zusätzlich lief lokal
`:shared:allTests :app:testProductionDebugUnitTest` erfolgreich (58 Aufgaben,
alle up-to-date). Der vollständige Nachweis steht im Task-3-Bericht.

### Side-Build-Baseline – Task 3 Fix-Runde 1: History-Revisionen

Undo und Redo stellten bereits die Objektkonfiguration aus dem
Modell-Snapshot wieder her, aber sie erhöhten bisher nur die Designrevision.
Dadurch konnten an `config_revision` gebundene Verbraucher einen
wiederhergestellten Objekt-Override übersehen. Vor dem Snapshot-Tausch
vergleicht der Kern deshalb die `ModelConfig`-Wörterbücher gleichartiger
Objekte je Bett. Nur eine tatsächliche Objektkonfigurationsdifferenz erhöht
`config_revision`; ein reiner Positions-Undo/Redo bleibt dort bewusst ohne
Zähleränderung. Jede erfolgreiche Wiederherstellung invalidiert weiterhin
genau einmal das Design.

Der neue Contract war auf dem Mac zuerst rot: Commit `cf1e122` baute im
frischen `psmobile-task3-fix1-red-cf1e122`-Worktree und endete gezielt mit
`FAIL: undo restores object override and advances both revisions`. Der
Fix-Commit `c6632ce` lief anschließend in einem neuen exakten
`psmobile-task3-fix1-green-c6632ce`-Worktree: der SIMULATORARM64-Build
erzeugte 325 Ziele und der iPhone-17-Pro-Simulator meldete
`PASS: psm_contract_tests`. Der C-`psm_testcli` kompilierte erneut gegen die
Headerdatei, und die Android-Regression `:shared:allTests
:app:testProductionDebugUnitTest` lief mit 58 up-to-date Aufgaben grün.
`psm_object_config_is_overridden` fängt jetzt zudem Ausnahmen aus seiner
Lock-/Lesestrecke ab und liefert konsistent den `-1`-Sentinel; eine
öffentliche Mutex-Fehlerinjektion existiert nicht.

### Side-Build-Baseline – Task 4: gemeinsame Inspektor- und Favoritenregeln

Die gemeinsamen, reinen Kotlin-Regeln legen das Inspektor-Ziel eindeutig
fest: keine Auswahl bedeutet Projekt mit keiner Objekt-ID, eine vorhandene
stabile Objekt-ID bedeutet Objekt mit genau dieser ID. Im vorhandenen
Android-/Shared-Code gibt es keinen weiteren Auswahl-Sentinel, deshalb wird
nur `null` als keine Auswahl behandelt.

Favoriten werden nach der autoritativen Reihenfolge des verfügbaren Katalogs
geordnet. Nicht verfügbare gespeicherte Schlüssel fallen weg und doppelt im
Katalog vorkommende Schlüssel erscheinen einmal an ihrer ersten Position;
leere Favoriten oder ein leerer Katalog ergeben eine leere Liste. Androids
bestehende `FavoriteSettings.orderedFor` delegiert direkt an diese Regel. Das
`SharedPreferences`-String-Set mit dem Namen `favorites`, die Toggle-API und
das Speicherformat bleiben unverändert.

### Side-Build-Baseline – Task 5: gemeinsame Mehrbett-Darstellung

Die Präsentationsregeln für Druckbetten liegen jetzt einmal im Kotlin-
Multiplatform-Modul `android/shared` und werden als echtes
`PSMShared.framework` nach Swift exportiert. Der Vertrag hält mindestens ein
Bett vor, normalisiert den aktiven Index, erhält IDs, Namen und Reihenfolge
stabil und beschreibt Add/Select/Lock/Rename/Remove. Das letzte Bett bleibt
unentfernbar; auch ein Bett mit Instanzen und einem veralteten
Objektzähler kann nicht entfernt werden. Arrange unterscheidet anhand von
Sperre, Objekt- und Instanzanzahl zwischen verfügbar, leer und gesperrt.

Der ursprüngliche RED-Lauf meldete die fehlenden `BedStrip*`-Typen. Bei der
Übernahme wurde ein zweiter RED-Fall ergänzt: `:shared:allTests` endete mit
132 Tests und genau einem Fehler, weil ein Bett mit drei Instanzen bei null
Objekten entfernbar war. Nach der minimalen Regelkorrektur lief der komplette
Shared-Lauf grün. Ein Android-Integrations-RED belegte außerdem, dass Simple
noch eine lokale Bettleiste führte; Simple und Advanced verwenden jetzt beide
den selben Selector und den von `SlicerService` gelesenen Kern-Aktivstatus.

iOS Simple verwendet denselben Selector, Arrange-Panel und
Mehrbett-Viewport-Modus wie Advanced. Ein erster fokussierter XCTest-Lauf
fand, dass ein gesperrtes Bett Arrange stumm deaktivierte. Das Panel zeigt
nun die vom gemeinsamen Vertrag abgeleiteten Locked-/Empty-Erklärungen; nach
einem zusätzlich erforderlichen `default`-Fall für das Kotlin-exportierte
Enum liefen alle drei `MultiBedArrangeUITests` grün.

Die Windows-Verifikation mit Java 17 (`:shared:allTests`, Production- und
Preview-Unit-Tests sowie beide Debug-APKs) war grün. In einer frischen,
abgehängten Mac-Worktree aus `155f78c` plus exaktem Task-Diff wurde das echte
iOS-Simulator-Framework gebaut (der Header exportiert `PSMSBedStripContract`
und alle zugehörigen Datenklassen), der Simulator-Core meldete
`PASS: psm_contract_tests`, XcodeGen sowie Production/Preview-Builds liefen
grün und der fokussierte Mehrbett-XCTest-Lauf meldete 3 Tests ohne Fehler.
Der schmutzige Mac-Hauptcheckout wurde nur als Quelle vorhandener
Abhängigkeits- und Ressourcen-Caches verwendet und nicht verändert.

Der Test-first-Lauf `./android/gradlew -p android :shared:allTests` war
zunächst mit den erwarteten fehlenden `Inspector*`- und
`FavoriteSettingRules`-Typen rot. Der Android-RED-Lauf
`./android/gradlew -p android :app:testProductionDebugUnitTest --tests
de.psmobile.ui.FavoriteSettingsTest` scheiterte gezielt nur daran, dass der
alte Filter einen doppelten Katalogschlüssel doppelt ausgab. Nach der
minimalen gemeinsamen Implementierung liefen `:shared:allTests` samt
gezieltem Favoriten-Test, beide Android-Debug-Flavor-Unit-Suiten und die 18
Python-Buildskript-Regressionen grün (ein erwarteter Skip).

#### Task 5 – Fix Round 1

Die Reviewrunde deckte drei echte Adapterfehler auf: Android verwarf
Core-Namen/-Locks, hielt Locks zusätzlich in Preferences und setzte
`instanceCount = objectCount`. Test-first kamen ein produktiver
`AndroidBedStripAdapter` und ein vom Service tatsächlich verwendeter
`AndroidBedStripActions`-Port hinzu. Die RED-Läufe fehlten zunächst an
diesen Produktionstypen; danach waren Add/Select/Rename/Lock/Remove,
Arrange und Moduserhalt grün.

Das C-ABI ist für `psm_bed_instance_count` von 8 auf 9 erhöht. Der
Simulator-Core-Vertrag prüft 1 Objekt/12 Instanzen sowie ein Objekt ohne
Instanz und meldete `PASS: psm_contract_tests`. Die Windows-Matrix lief mit
der tatsächlichen JBR 21.0.10 grün; JVM-Ziel bleibt 17. K/N, Core sowie
beide Xcode-Schemes waren in der frischen Mac-Worktree grün. Der schmutzige
Mac-Hauptcheckout blieb bei HEAD `e1e74a0`; sein Status-Fingerprint war
pre/post identisch (114 Einträge, SHA-256 `73fcc110...89685`).

Der deterministische Swift-Simulatortest `BedModeStateTests` belegt den
gemeinsamen Produktionsadapter über Simple→Advanced→Simple. Der direkte
XCUI-Navigationsversuch wurde verworfen, weil iOS 26 SwiftUI-Menü- und
offscreen Scroll-Children nicht stabil exportiert. Die fünf tatsächlich
bedienbaren Mehrbett-UI-Tests (inklusive Rename und Empty-Erklärung) liefen
grün.

2026-08-09 Task5 Fix Round2: JNI-Bednamen auf echte UTF-16/UTF-8-Konvertierung
umgestellt; ergänzender Unicode-Test (`Werkstatt 🛠️ 🔥`) grün. Der fokussierte
Gradle-Lauf war unter JBR 21.0.10 erfolgreich (JVM-Ziel 17). ABI9-.so-Dateien
und Fingerprints fehlen im Windows-Worktree; Docker ist nicht verfügbar, daher
kein Übernehmen alter Artefakte und kein behaupteter Runtime-Smoke.
