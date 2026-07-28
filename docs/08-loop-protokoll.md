# Loop-Protokoll

Diese Datei ist das Gedaechtnis zwischen den Loop-Durchlaeufen. Jeder
Durchlauf startet ohne Erinnerung an den vorigen - hier steht, was
passiert ist.

## Ablauf fuer jeden Durchlauf

1. **Erst lesen, dann bauen.** In dieser Reihenfolge:
   `docs/09-fehlerliste.md` (hat Vorrang), dann
   `docs/07-stopp-punkte.md` (Arbeitsliste), dann den letzten Eintrag
   unten, dann `git log --oneline | head -10`.
2. **Einen Punkt nehmen**, nicht mehrere. **Fehler der Stufe A gehen vor
   allen neuen Funktionen** - eine App mit vier A-Fehlern ist nicht
   testbar, egal wie viele Funktionen sie hat. Danach B, danach die
   Arbeitsliste aus den Stopp-Punkten.
3. **Bauen und pruefen** - nicht nur schreiben. Nach Kernaenderungen
   immer `stage-native.sh` vor `build-apk.sh`, sonst liegt die alte .so
   im APK.
4. **Committen**, sobald der Punkt steht. Lieber mehrere kleine Commits
   als einer am Ende.
5. **Hier eintragen** - Datum, was gemacht, was gemessen, was aufgefallen
   ist. Auch Fehlschlaege. Besonders Fehlschlaege.
6. **Stopp-Punkte pflegen**: erledigten Punkt abhaken, neu entdeckte
   Probleme ergaenzen.

## Grundregeln, die nicht verhandelbar sind

- **E-12 gilt**: Was aus PrusaSlicer uebernehmbar ist, wird extrahiert -
  nie abgetippt. Beschriftungen kommen aus dem Sprachkatalog, Icons aus
  `resources/icons`, Struktur aus `Tab.cpp`. Wer eine Beschriftung
  selbst formuliert, macht etwas falsch.
- **Keine Entwurfsentscheidungen im Alleingang.** Wenn unklar ist, wie
  etwas aussehen soll, lieber in den Stopp-Punkten notieren als raten.
  Das hat schon einmal eine Stunde gekostet.
- **Nichts als fertig melden, was nicht lief.** Gebaut heisst nicht
  geprueft.

## Wann der Loop sich selbst anpassen soll

Wenn ein Punkt sich als groesser herausstellt als gedacht, oder ein
Problem auftaucht, das nicht zwingend geloest werden muss: **nicht
festbeissen**. Notieren, ueberspringen, den naechsten Punkt nehmen. Ein
blockierter Durchlauf, der drei Stunden am selben Fehler haengt, ist
schlechter als fuenf Durchlaeufe, die je einen kleinen Punkt erledigen.

Der Loop endet um **6:00 Uhr**. Wer nach 5:30 startet, faengt nichts
Grosses mehr an, sondern raeumt auf: committen, Stopp-Punkte aktuell
machen, Zusammenfassung hier eintragen.

---

## Verlauf

### 2026-07-29, Sitzung vor dem Loop

**Gemacht**
- Viewport (M4): GLES-Renderer mit PrusaSlicers eigenen ES-Shadern,
  Bett aus der Druckerkonfiguration, Auswahl gruen, Gesten, Ansichtsleiste.
- Extraktor (E-12): 20 Einstellungsseiten, 247 Parameter, 15 Werkzeuge,
  21 Sprachen mit 103446 Eintraegen, 37 Original-Icons.
- Einstellungsbildschirm mit Originalstruktur und -Tooltips, Filter
  Simple/Advanced/Expert aus dem `mode`-Feld von PrintConfig.
- Ersteinrichtung mit Drucker- und getrennter Duesenauswahl.
- Objekt verschieben durch Ziehen in der Bettebene.
- Einstellungsseite ausgerichtet: Beschriftung links, Bedienelement
  rechtsbuendig, Erklaerung darunter, Trennlinien.

**Gemessen**
- MK4S mit 0.4er Duese: 1 Drucker, 5 Druckprofile, 252 Filamente.
  Mit allen Duesen: 10 Drucker. Ohne Auswahl: 221/520/5762.
- Druckermodelle sichten: 0,05 s. Profile einrichten: 1,7 s.
- Wuerfel slicen auf dem Geraet: 166 Layer, 0,4 s.

**Aufgefallen**
- Der Push aufs Geraet schlaegt aus Git Bash still fehl, weil MSYS die
  Android-Pfade umschreibt. Immer PowerShell fuer `adb push` nehmen.
- `du -h` ueber die Unraid-Freigabe meldet falsche Groessen. `ls -la`
  oder `find -printf '%s\n'` nehmen.
- Einmal wurde ein APK mit alter .so getestet, weil `stage-native.sh`
  vergessen wurde. Kostet eine halbe Stunde Fehlersuche.

**Offen als naechstes**: Objekt skalieren und drehen.

### 2026-07-29, spaeter Abend - PrusaLink und Sicherung

**Gemacht**
- `net/PrusaLink.kt`: Verbindungstest ueber `GET /api/v1/status`, Upload
  ueber `PUT /api/v1/files/{storage}/{name}`, beides mit `X-Api-Key`.
  Endpunkte aus PrusaLink selbst uebernommen, nicht geraten. Bewusst mit
  HttpURLConnection statt einer Fremdbibliothek - zwei Aufrufe brauchen
  kein OkHttp.
- `net/PrinterStore.kt`: Geraete mit Name, Adresse, Schluessel und
  zugehoerigem Druckerprofil.
- `net/BackupStore.kt`: Sicherung ueber das Storage Access Framework.
  Damit funktionieren eingebundene Netzlaufwerke, ohne dass die App eine
  Speicherberechtigung oder eigenes SMB braucht.
- `ui/PrintersScreen.kt`: Verwaltung mit Hinzufuegen, Bearbeiten,
  Verbindungstest, Loeschen.
- Senden-Knopf in der Seitenleiste, sichtbar sobald G-Code vorliegt und
  mindestens ein Geraet eingerichtet ist. Die Sicherung laeuft auch dann,
  wenn der Upload scheitert - der G-Code ist ja trotzdem entstanden.

**Nicht fertig, ehrlich vermerkt**
- Der Schalter "Nur eingerichtete Drucker zeigen" wird gespeichert, aber
  die Profilliste filtert noch nicht danach. Der Haken dafuer sitzt in
  `SlicerService.refreshPresets()`: dort muesste gegen
  `PrinterStore.all(context).map { it.presetName }` gefiltert werden.
- Upload und Sicherung sind **nicht gegen ein echtes Geraet getestet** -
  im Emulator gibt es keinen Drucker. Die Endpunkte stammen aus dem
  PrusaLink-Quelltext, aber gelaufen ist es nie.
- Der API-Schluessel liegt in gewoehnlichen SharedPreferences. Vor einer
  Veroeffentlichung gehoert er in EncryptedSharedPreferences.

**Aufgefallen**
- Kotlin-String-Interpolation in einem ssh-Kommando wird von der lokalen
  Shell gefressen. Patch-Skripte als Datei schreiben, nicht inline.
- **`adb install -r` tauscht die DEX auf diesem Emulator nicht
  zuverlaessig.** Zweimal wurde eine alte Oberflaeche getestet und
  faelschlich fuer kaputt gehalten. Immer erst `adb uninstall
  de.psmobile`, dann `adb install`.

### 2026-07-29, Korrektur der PrusaLink-Anmeldung

**Anlass**: Hinweis vom Nutzer, dass PrusaLink inzwischen mit
Benutzername und Passwort arbeitet, nicht mehr mit API-Schluessel.

**Geprueft, nicht geraten**: In PrusaSlicers `OctoPrint.cpp`,
`PrusaLink::set_auth` stehen beide Verfahren nebeneinander -
`atKeyPassword` setzt `X-Api-Key`, `atUserPassword` ruft
`http.auth_digest`. Also **HTTP-Digest**, nicht Basic.

**Gemacht**
- `net/DigestAuth.kt`: Digest nach RFC 7616 von Hand. Androids
  HttpURLConnection beherrscht nur Basic - Digest muss selbst gerechnet
  werden.
- `PrusaLink` unterstuetzt beide Verfahren, Vorgabe ist Benutzer und
  Passwort mit `maker` als Benutzernamen.
- Beim Upload wird die nonce vorher ueber eine billige GET-Anfrage
  geholt. Wer erst sendet und dann 401 bekommt, hat die Datei umsonst
  uebertragen. Bei abgelaufener nonce wird einmal neu geholt und
  wiederholt.
- Alte gespeicherte Eintraege ohne `auth`-Feld, die einen API-Schluessel
  hatten, werden weiter als API-Schluessel behandelt.

**Weiterhin ungetestet gegen ein echtes Geraet.**

### 2026-07-29, 01:20 - Loop-Durchlauf 1: A1 und A5

**Gemacht**
- A1 behoben: Navigationszustand aus der Oberflaeche in den Service
  gehoben (`SlicerService.Screen`). `loadModel` ruft jetzt `showBed()`,
  damit ein hereinkommendes Modell die Ansicht zurueckholt.
- A5 dabei entdeckt und gleich behoben: Der eingehende Intent erzeugte
  eine zweite Activity-Instanz, die das Modell ein zweites Mal
  importierte - zwei identische Wuerfel auf dem Bett. Jetzt
  `launchMode=singleTask` plus eine Sperre gegen doppelte Auswertung
  desselben Intents.

**Gemessen**
- Vorher: Druckerbildschirm blieb stehen, Modell lud unsichtbar, danach
  lagen zwei Objekte auf dem Bett.
- Nachher: Ansicht springt aufs Bett, genau ein Objekt.

**Aufgefallen**
- Beim ersten Testlauf waren die Taps zu frueh - die Ersteinrichtung
  braucht nach dem Start rund 15 s, bis die Liste steht. Wer per
  `input tap` testet, muss vorher per Screenshot pruefen, ob die
  Oberflaeche wirklich da ist. Sonst haelt man einen Bedienfehler fuer
  einen Programmfehler; genau das ist hier einmal passiert.
- Ein XML-Kommentar zwischen den Attributen eines Tags ist ungueltig.
  Der Gradle-Build meldet das nicht deutlich, er lief einfach durch.
- Backticks in einem ssh-Kommando werden von der lokalen Shell als
  Kommandosubstitution gelesen. Fuer Dokumentaenderungen die
  Datei-Werkzeuge nehmen, nicht python-Heredocs ueber ssh.

### 2026-07-29, 01:45 - Loop-Durchlauf 2: A2 und A3

Beide zusammen genommen, weil sie dieselbe Ursache haben - die
Zustandsverwaltung der Einstellungsseite. Eines ohne das andere zu
beheben waere halbe Arbeit gewesen.

**Gemacht**
- `SlicerService.configRevision` eingefuehrt. Steigt bei Profilwechsel
  und Neuinstallation.
- A2: Die Metadaten kommen jetzt einmal je Seite, Stufe und Revision aus
  einem `remember`, statt bei jeder Neuzeichnung fuer jeden Parameter
  einzeln ueber JNI.
- A3: Die Zeilen haengen ihren zwischengespeicherten Wert an dieselbe
  Revision.

**Gemessen**
- Wechsel von "0.20mm SPEED @MK4S 0.4" auf "0.10mm FAST DETAIL @MK4S 0.4":
  Schichthoehe zieht von 0,2 auf 0,1 mit. Vorher blieb 0,2 stehen und
  waere beim naechsten Antippen in das neue Profil zurueckgeschrieben
  worden.
- Die Profilliste zeigt genau die 5 Profile der MK4S mit 0.4er Duese.

**Aufgefallen**
- Nichts Neues. Der Durchlauf lief ohne Umweg durch, weil diesmal vor
  jedem Tippen ein Screenshot geprueft wurde - die Lehre aus Durchlauf 1.
