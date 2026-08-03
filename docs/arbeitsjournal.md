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
| iOS Advanced: Werkzeugleiste, Objektbaum | offen | — |
| Bemalen: Stützen, Naht, Farbwechsel | offen | — |
| Fünf Spezialdialoge auf iOS | offen | — |

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
