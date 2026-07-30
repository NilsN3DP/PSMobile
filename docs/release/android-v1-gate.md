# Android-v1-Gate

Stand: 2026-07-30

Dieses Protokoll trennt Quellcode, Build und reale Geräteprüfung. Ein
nicht ausgefülltes Feld ist kein stillschweigender Erfolg.

## Automatisierte Prüfungen

| Prüfung | Ergebnis | Beleg |
| --- | --- | --- |
| C++-Syntax für Core und Vertragstest | bestanden | lokaler Lauf 2026-07-30 |
| Kotlin-Kompilierung und JVM-Unit-Tests | bestanden | `testDebugUnitTest`, 2026-07-30 |
| Python-Report-/Fixture-/Generatortests | bestanden | 7 Tests, 2026-07-30 |
| Native Android-Bibliotheken neu linken | bestanden | arm64-v8a und x86_64, NDK r27c |
| Native-Fingerprint prüfen und stagen | bestanden | 2 ABIs, 13 MB / 14 MB |
| C-ABI-Vertragstest | bestanden | x86_64-Emulator, inklusive Slice/Stale, G-Code-Pfadkonflikt, gruppiertem Undo/Redo, Teil-Extruder, bettübergreifender Kopie und Mehrbett-3MF-Roundtrip |
| Debug-APK mit beiden ABIs | bestanden | `testDebugUnitTest assembleDebug` |
| Debug-APK installieren | bestanden | seitengleiche Testvariante `de.psmobile.betatest`; vorhandene anders signierte App und ihre Daten blieben unangetastet |

## Aktueller Beta-Kandidat

- APK: `PSMobile-android-beta-full-debug-arm64-x86_64.apk`
- Paket: `de.psmobile`, Version `0.1.0-m3`
- ABIs: `arm64-v8a`, `x86_64`
- SHA-256:
  `450A2803A66171A172030EC6198FE39E57F9B5E9EB14EFFCEAE9A163ABF78CC9`
- Native Build-IDs:
  `arm64-v8a 183041d3d47b6955e8b695714ca2fb1372d48993`,
  `x86_64 580ec48001682131ae62192e6412856eeb13e833`

## UI-Smoke-Test auf x86_64

| Ablauf | Ergebnis |
| --- | --- |
| App-Start und natives Laden | bestanden, kein `UnsatisfiedLinkError` oder Absturz |
| Erstkonfiguration | bestanden mit „Original Prusa MK4 Input Shaper“ |
| Bett hinzufügen und direkt wählen | bestanden; `Bett 1` und `Bett 2` gleichzeitig als Chips sichtbar |
| 3MF-Auswahldialog | bestanden; „Nur 3D-Objekte“ und „Als Projekt“ werden angeboten |
| 3MF nur als Objekte importieren | bestanden; Objektzahl des aktiven Betts erhöht |
| 3MF als Projekt importieren | bestanden; zwei Betten mit je einem Objekt übernommen |
| Projektlokales Druckerprofil | bestanden; eingebettetes „PSMobile Test Printer 0.4“ aktiviert |
| Fremdes Post-Processing | bestanden; Skript entfernt und im Importhinweis gemeldet |
| Projektleiste „Neu / Speichern / Speichern unter“ | bestanden; Aktionen dauerhaft über dem Bett sichtbar |
| Neues Projekt mit Verlustwarnung | bestanden; danach genau ein leeres Bett |
| 3MF über Android-Dokumentanbieter speichern | bestanden; reale 5-KiB-Datei in Downloads |
| Gespeicherte 3MF erneut als Projekt öffnen | bestanden; `Bett 1 · 2` und `Bett 2 · 1` blieben erhalten |
| Undo/Redo aus der Werkzeugleiste | bestanden; Duplikat änderte Bettzahl `1 → 2 → 1 → 2` |
| Kopieren/Einfügen | bestanden; Kopieren merkt das Objekt, Einfügen erzeugt erst auf Anforderung eine neue Objektkopie |
| Zwischenablage über mehrere Betten | bestanden; Quelle auf Bett 1 kopiert, Bett 2 gewählt und dort eingefügt, Ergebnis `Bett 1 · 1` / `Bett 2 · 1` |
| Instanz-Werkzeuge | bestanden; `+ Kopie` erhöht die Instanzzahl statt fälschlich ein neues Objekt anzulegen |
| Objekt-/Volumenbaum | bestanden; `Bauteil · 12 Dreiecke` sichtbar |
| MMU3-Extruder je Volumen | bestanden; Auswahl `Vom Objekt`, Extruder 1–5 und sichtbares Ergebnis `E3` |
| Objekttransformationen | bestanden; 90°-Drehung, Spiegeln, auf Bett einpassen und Instanzzahl wurden im Inspektor korrekt zurückgemeldet |
| Ungespeicherte Profiländerungen | bestanden; Dirty-Hinweis, Differenzdialog und Übernahme `fill_density: 15% → 25%` auf ein anderes Profil |
| Profil unter eigenem Namen / verwerfen | UI und Build vorhanden; vollständiger Persistenzlauf auf physischem Gerät offen |
| „Nur eingerichtete Drucker“ | Build bestanden; Profilliste wird aus den PrusaLink-Zuordnungen gefiltert, Hardware-Fixture offen |
| Responsive Tablet-Layout, Querformat | bestanden bei 1280 × 800 dp; Arbeitskopfzeile, Profil-/Objekt-/Bearbeiten-Inspektor und gemeinsame Ansichtsleiste ohne Überdeckung |
| Responsive Tablet-Layout, Hochformat | bestanden bei 800 × 1280 dp; 3D-Fläche bleibt frei und der Inspektor öffnet als 380-dp-Overlay mit fest sichtbarer Panel- und Schließen-Aktion |
| Touch-Zielgrößen | bestanden; Hauptaktionen, Objektbaum, Transformationsfelder, Setup, Einstellungen und Druckereditor auf mindestens 48 dp angehoben |
| Slice aus der Compose-UI | bestanden; 600 Layer, fertiger G-Code und Exportaktion |
| G-Code-Vorschau und Teilen | bestanden; Layer-Slider sichtbar, Android-Teilen zeigt `PSMobile Test Cube.gcode` |
| Foreground-Service während Slice | bestanden; `isForeground=true`, laufende Notification mit einer Aktion |
| 20 Slice-Zyklen hintereinander | bestanden; 20 Fertigmeldungen, identische Prozess-ID, kein Crash oder ANR |
| Abbruch und direkter Neustart | bestanden; großer Slice meldet `abgebrochen`, der nächste Lauf endet regulär |
| Großes 3MF / Speicherdruck | bestanden im Emulator; 41,03-MB-3MF, etwa 1,0 GB Peak-RSS nach Preview, kein Low-Memory-Abbruch |
| Hintergrund und Display aus | bestanden; 31,0-s-Slice lief mit `mWakefulness=Asleep` weiter und endete mit 600 Layern |
| G-Code-Pfadkonflikt | bestanden; früherer SIGSEGV `0x268` reproduziert, symbolisiert, behoben und durch überlappende Objekte im C-ABI-Test abgedeckt |
| Zufälliger Touch-Stresstest | bestanden; 500 Monkey-Ereignisse, keine verlorenen Eingaben, kein Crash oder ANR |
| Notification-Abbruch bedienen | offen; Foreground-Meldung erschien in diesem Emulator nicht sichtbar in der Notification-Shade |

Die Testvariante unterscheidet sich nur durch ein lokales
`applicationIdSuffix` außerhalb des Arbeitsbaums. Damit konnte sie
parallel zur bereits installierten, anders signierten App laufen.

## Noch auszuführende Gerätematrix

| Ziel | Android | RAM | Native-Fingerprint | Ergebnis |
| --- | --- | --- | --- | --- |
| arm64-Gerät, 4–6 GB | – | – | – | offen |
| arm64-Gerät, mindestens 8 GB | – | – | – | offen |
| x86_64-Emulator | API 36 | Emulator-RSS bis etwa 1,0 GB | `580ec48001682131ae62192e6412856eeb13e833` | C-ABI, Import, Speichern/Wiederöffnen, Mehrbett, Clipboard, Profile, Objektwerkzeuge, Vorschau/Teilen, Abbruch, 41-MB-Slice, Hintergrund und Screen-off bestanden |

Auf den beiden arm64-Zielen müssen STL- und beide 3MF-Importmodi,
Profilübernahme, Mehrbettauswahl, Gradrotation, Stale-Slice, Vorschau,
Export, Screen-off und Notification-Abbruch wiederholt werden. Auf dem
Emulator bleiben Geräte-Rotation während eines laufenden Slices,
Stale-UI und der tatsächlich bediente Notification-Abbruch offen.

## Drucker-Hardware

- API-Key-PrusaLink: offen
- Digest-PrusaLink: offen
- HTTP-Ablehnung und ausdrückliche Freigabe: offen
- Auswahl zwischen mindestens zwei Druckern: offen
- Upload-Dateiname aus `output_filename_format`: offen

## Release-Blocker

- Geräte- und Speicherläufe protokollieren.
- `LICENSE`, `THIRD-PARTY-NOTICES`, Quellcodeangebot und
  Datenschutzinformation fertigstellen.
- Store-/AGPL-Entscheidung aus `docs/04-lizenz-und-store.md` treffen.
