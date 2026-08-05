# IPA-Geraetebuild – 2026-08-05

## Ergebnis

Der Packaging-Fehler ist behoben und durch einen lokalen Regressionstest
abgedeckt. Eine neue IPA wurde **nicht** nach
`C:/Users/Nils/OneDrive/PSMobile` kopiert, weil auf diesem Windows-Workspace
kein macOS-/Xcode-Host und kein konfiguriertes SSH-Ziel verfuegbar ist. Es wird
keine alte IPA als Erfolg ausgegeben.

## Ursache der falschen Erfolgsmeldung

`package-ipa.sh` hat den Rueckgabestatus von `xcodebuild` mit
`| grep ... || true` verworfen. Gleichzeitig blieb
`build-out/ios-derived-device/.../PSMobile.app` nach einem frueheren Lauf
liegen. Nach einem fehlgeschlagenen Link konnte das Skript daher diese alte App
signieren und verpacken.

## Aenderung

- Das Device-DerivedData wird vor dem Xcode-Build entfernt.
- `xcodebuild` laeuft ungefiltert; stdout und stderr werden vollstaendig nach
  `build-out/logs/package-ipa-xcodebuild.log` geschrieben und behalten ihren
  Fehlerstatus.
- Signieren und Packen sind nur nach erfolgreichem `xcodebuild` und einem
  vorhandenen neuen App-Bundle erreichbar.
- Das neue IPA wird zuerst in einer temporaeren Datei erzeugt und mit
  `unzip -t` geprueft. Erst dann ersetzt ein `mv` das Ziel; bei jedem Fehler
  wird kein Zielpfad als Erfolg ausgegeben.

## Verifikation

Lokal ausgefuehrt:

```text
python -m unittest build.scripts.tests.test_package_ipa -v
Ran 2 tests ... OK
```

Der Fehlerfall legt vorher absichtlich sowohl eine alte App als auch eine alte
IPA an. Er beweist: `xcodebuild`-Fehler bleibt ein Fehler, das alte App-Bundle
wird entfernt, es wird nicht signiert/gepackt und der ungefilterte Linkertext
steht vollstaendig im Log. Der Erfolgsfall beweist Signatur, Archivpruefung und
atomaren Zielersatz.

## Noch auf dem Mac auszufuehren

Der konkrete Linkerfehler konnte hier nicht erfasst werden; er ist erst nach
diesem ungefilterten Aufruf belastbar und darf vorher nicht geraten werden:

```bash
cd /Pfad/zu/PSMobile
OUTPUT="$HOME/PSMobile-2026-08-05-advanced-stabilisierung.ipa"
bash build/scripts/package-ipa.sh "$OUTPUT"
cp "$OUTPUT" "$HOME/Library/CloudStorage/OneDrive-Personal/PSMobile/"
```

Bei einem Fehler liegt das vollstaendige, nicht maskierte Xcode-/Linker-Log
unter `build-out/logs/package-ipa-xcodebuild.log`. Erst nach dessen Auswertung
kann die Linkerursache gezielt repariert und die erzeugte IPA nach OneDrive
kopiert werden.
