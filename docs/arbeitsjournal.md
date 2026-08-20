# Arbeitsjournal

> **Vor diesem Journal:**
> [00-gleichstand-implementierungsplan.md](00-gleichstand-implementierungsplan.md).
> Dort steht der Stand von Android gegenueber iOS und die Reihenfolge
> der Arbeitspakete. iOS ist die Referenz.


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
| Gleichstand Android ↔ iOS | Claude | Plan in `00-gleichstand-implementierungsplan.md`, AP-01/02/03/06/07/08/09/18 fertig, AP-05 bis auf die Schiene |

---

## 2026-08-20

### Claude — AP-25: der Selbsttest, und ein Fehlurteil unterwegs

Die Paarliste des Abgleichs kannte mehrere Bildschirme gar nicht, weil
sie auf beiden Seiten verschieden heissen - `SelbsttestView.swift` etwa
gegen `SelbsttestScreen.kt`. „Kein Gegenstueck gefunden" hiess bis heute
schlicht, dass ein Bildschirm **nie verglichen wurde**. Ein stiller
Ausfall, kein lauter. Die Liste ist ergaenzt, mit einem Hinweis, worauf
man beim Ergaenzen achten muss: die Android-Datei muss aehnlich eng
geschnitten sein, sonst liefert der Vergleich hunderte bedeutungslose
Zeilen.

Dabei kam auch heraus, dass das Skript auf der Windows-Konsole am ersten
Pfeil abbrach - mitten im Bericht. Das ist schlimmer als gar kein
Bericht: man haelt die halbe Liste fuer die ganze. Jetzt schreibt es
UTF-8 mit `errors="replace"`.

**Der Selbsttest war der Fund.** Drueben heisst der Knopf *Run all
checks*, wird waehrend des Laufs zu *Cancel*, und am Ende steht eine
Zeile da: alles bestanden, oder wie viele Schritte fehlschlugen. Hier
hiess er *Start*, war waehrend des Laufs ausgegraut, und am Ende sagte
nichts, ob etwas schiefging - dafuer musste man den Bericht oeffnen oder
sechzehn Haken durchsehen. Alle drei Punkte sind angeglichen.

Abgebrochen wird **zwischen** den Schritten. Mitten in einem
aufzuhoeren hiesse, den Kern in einem Zustand zurueckzulassen, ueber den
der Bericht nichts Wahres sagen koennte. Die uebrigen Schritte stehen
danach als *uebersprungen · abgebrochen* da.

**Und jetzt das Fehlurteil.** Beim ersten Versuch sah ich, dass alle
Schritte durchliefen, und schrieb - hier im Journal, schon halb
formuliert -, der Abbruch habe nicht gegriffen. Statt das zu glauben,
habe ich zwei Log-Zeilen eingebaut und noch einmal gemessen:

    abbrechen laeuft=true
    schritt Standardfilament nach Einrichtung abbruch=true
    schritt Drucker auswaehlen abbruch=true
    ...

Der Abbruch griff einwandfrei. Beim ersten Versuch hatte schlicht mein
Tipp danebengelegen - der Bildschirm war anders gescrollt als
angenommen. Dieselbe Falle wie schon zweimal: **frisches Bildschirmfoto
vor jedem Tipp in eine gescrollte Flaeche.** Haette ich dem ersten
Eindruck geglaubt, haette ich an funktionierendem Code herumgebaut.

**Ein echter Mangel kam dabei doch heraus.** Nach einem Abbruch stand
*All checks passed* da. Es war nichts fehlgeschlagen - geprueft war aber
auch nicht alles. Jetzt steht dort *Abgebrochen*. Am Bildschirmfoto
sofort zu sehen, im Quelltext nicht.

**Nachgesehen und gleich:** die Schrittnamen sind auf beiden Seiten
deutsch, auch bei englischer Oberflaeche. Sah nach einem Versaeumnis
aus, ist aber drueben genauso - der Bericht ist ein Werkzeug fuer die
Entwicklung, kein Text fuer Nutzer.

### Claude — Der Abgleich log, und wie ich es gemerkt habe

Das Vergleichsskript von gestern meldete, den Moduskarten der Startseite
fehle auf Android die Uebersetzung: „Get to print quickly with a few
clear decisions." stehe nur auf iOS. Ich wollte das schon beheben - und
habe vorher nachgesehen. Der Text steht dort laengst zweisprachig.

Der Fehler lag im Skript. Der Ausdruck verlangte hinter dem zweiten
Text sofort die schliessende Klammer. Steht der Aufruf ueber mehrere
Zeilen, hat er davor aber meist ein Komma - und dann greift das Muster
nicht. Ein `,?` mehr, und die Liste wurde deutlich kuerzer und
belastbarer. Der Kommentar im Skript sagt jetzt, warum das `,?` dort
steht.

Das ist die Lehre, nicht der Regexfehler: **ein Werkzeug, das Fehler
meldet, muss selbst geprueft werden, bevor man seinen Meldungen
folgt.** Haette ich losgelegt, haette ich funktionierenden Text
„repariert" und im Journal stolz davon berichtet.

**Zwei echte Funde blieben ueber.** Die Zeile zum Protokoll hiess
„Protokoll teilen", drueben „Protokoll exportieren" - und die Datei
verlaesst die App, genau wie beim G-Code, wo hier schon „exportieren"
steht. Angeglichen. Androids Untertext bleibt: er nennt, was drinsteht
und was nicht, und ist damit der bessere; er geht als Z-Punkt nach
drueben. Und die Ueberschrift der Zuletzt-Kacheln stand in Versalien,
drueben schlicht „Zuletzt". Auch das angeglichen.

**Drei Funde habe ich liegen gelassen**, weil sie Entscheidungen sind
und keine Buegelarbeit: die Schreibweise *colour* gegen *color* (Android
39-mal zu 5, iOS 4-mal zu 1 - **beide Seiten sind in sich
uneinheitlich**), die durchweg abweichenden ColorMix-Beschriftungen, und
„Alle Projekte" als eigener Bildschirm gegen das Aufklappen an Ort und
Stelle. Alle drei stehen mit Zahlen im Plan, damit die Entscheidung
billig ist.

**Und noch etwas beim Belegen.** Fuer das Bildschirmfoto der
Zuletzt-Kacheln habe ich zwei Eintraege direkt in die Preferences
geschrieben und danach wieder entfernt. Beim erzwungenen Neustart
mittendrin kam ein ANR: `executing service …SlicerService, waited
20176ms`. Nicht die Startseite, sondern der Dienst beim Hochfahren des
Kerns. Ein sauberer Neustart danach war unauffaellig, ich habe es also
nicht zum Fehler erklaert - aber notiert. Auf einem schwaecheren Geraet
koennte daraus einer werden.

### Claude — AP-24 zu Ende, und eine Korrektur an mir selbst

Gestern hatte ich in Abschnitt Z geschrieben, iOS lege Zahlen und
Legende schwebend ueber das Bett und muesse nachziehen. **Das war
falsch.** `AdvancedWorkspaceView.vorschauInhalt` zeigt sie laengst im
rechten Band, und `FinalPreviewPanel.swift` sagt es im Quelltext
ausdruecklich: „Nur noch fuer den Simple Mode - der Advanced Mode zeigt
Statistik und Legende stattdessen direkt im rechten Menueband". Android
war der Nachzuegler, nicht iOS. Der Eintrag ist korrigiert.

Was iOS wirklich noch nicht tut, ist das *Ausblenden* des Editors: dort
steht die Vorschau unter dem Editorinhalt, hier ersetzt sie ihn. Das ist
Nils' Vorgabe, und in dieser Richtung zieht iOS nach.

**Zwei Dinge aus dem Vergleich uebernommen.** Erstens heisst der Block
drueben „G-code preview", nicht „Preview" - so heisst er hier jetzt
auch. Zweitens traegt er dort einen Knopf *Editor* als Rueckweg. Den
gab es hier nicht; man musste quer ueber den Bildschirm in die obere
Leiste. Jetzt steht er neben der Ueberschrift, in beiden Modi.

**Und der Simple Mode war keine offene Frage.** Ich hatte notiert, wohin
die Legende dort gehoert, muesse Nils entscheiden. Falsch: iOS hat eine
feste Antwort. Auf breiten Geraeten steht die Karte am rechten Rand, auf
schmalen unten - drueben iPad gegen iPhone, hier die Bildschirmbreite.
Kopfzeile *Finaler G-Code*, derselbe *Editor*-Knopf, darunter Zahlen und
Legende. Den Schichtbereich fuehrt iOS dort als zwei Regler mit; hier
nicht, weil der senkrechte Regler am linken Rand dasselbe tut.

**Zwei Kollisionen, beide erst am Bildschirmfoto zu sehen.** Die Karte
lag ueber der Werkzeugspalte am rechten Rand. Und der senkrechte
Schichtregler fing am Bildschirmrand an: sein oberer Griff sass in der
Statusleiste, die Zahl „198" stand neben der Uhrzeit. Der erste
Korrekturversuch schob ihn genau in die Kapsel *Bett 1* - erst der
zweite sass. Das war kein neuer Fehler, der Regler stand im Simple Mode
immer schon so; aufgefallen ist es erst, als daneben etwas anderes
Ordentliches stand.

### Claude — AP-24: rechts steht jetzt entweder der Editor oder die Vorschau

Nils, waehrend ich am Abgleich sass: das schwebende Menue ueber dem Bett
soll weg, sein Inhalt gehoert beim Ansehen des G-Codes ins rechte Band,
und allgemein soll rechts zwischen einer Viewer- und einer
Editor-Ansicht umgeschaltet werden, je nachdem, wo man gerade ist.

Die Karte lag ueber der Platte, weil - so stand es im Quelltext - „das
Seitenband im Vorschaumodus oft zu" sei. Das Argument stimmte, nur war
die Antwort darauf falsch herum: statt die Karte ueber das Bett zu
legen, macht das Band jetzt beim Wechsel in die Vorschau von selbst auf.
Dafuer reicht ein Zaehler weit oben in der Bildschirmfunktion, auf den
weiter unten - wo die Breite bekannt ist und `inspectorOpen` entsteht -
ein Effekt horcht.

Das Band traegt seitdem zwei Gesichter. Im Editor steht oben
*Arbeitsbereich*, darunter die drei Profilseiten und die Bereiche. In
der Vorschau steht oben *Vorschau*, darunter die Zahlen, die Umschaltung
*Merkmale ↔ Extruder*, die Legende und der Verbrauch. Die drei
Profilseiten sind dort **weg**: sie wirken auf den naechsten Schnitt,
nicht auf das, was man gerade ansieht. Der Schneiden-Block unten bleibt
in beiden - er gehoert zum Projekt, nicht zur Ansicht.

Ueber dem Bett steht damit nichts mehr ausser den beiden Reglern.

**Eine Kleinigkeit, die erst am Bildschirmfoto auffiel.** Die Legende war
eine Reihe mit waagerechtem Schieber - in der breiten Karte ging das
auf, im schmaleren Band lief sie rechts aus dem Bild. Man sah „Solid
inf…" und keinen Hinweis, dass da noch etwas kommt; das sieht aus wie
abgeschnittener Text, nicht wie etwas zum Schieben. Jetzt bricht sie um
(`FlowRow`), und alle sieben Merkmale stehen da.

**Offen und ausdruecklich nicht erfunden:** der Simple Mode hat die
Vorschau-Legende gar nicht - dort gibt es weder Zahlen noch
Merkmalswahl, obwohl iOS beides in beiden Modi zeigt. Wohin sie im
Simple Mode gehoert, ist eine Anordnungsfrage; die entscheidet Nils.

Ebenfalls von ihm vorgemerkt: **iOS fehlt der Werkzeugweg-Regler** aus
AP-14. Steht jetzt als Punkt 7 in Abschnitt Z.

### Claude — AP-11 war nur zur Haelfte umgesetzt

Der Plan sagte, ohne den Mac sei nichts mehr offen. Statt zu warten habe
ich die Beschriftungen Bildschirm fuer Bildschirm gegeneinander gehalten:
ein kleines Skript zieht aus den iOS-Dateien alle `st("…", "…")` und aus
den Android-Dateien alle `appText("…", "…")` und zeigt, was auf einer
Seite steht und auf der anderen fehlt. Ueber die *Reihenfolge* sagt das
nichts - fehlende und zusaetzliche Bedienelemente findet es zuverlaessig.

Der ertragreiche Treffer: **`SliceSheet.swift` gegen
`SimpleSliceSheet.kt`.** Auf iOS standen dort „Send", „Send to printer"
und „The G-Code could not be written.", auf Android keines davon.

Der Grund ist eine Verwechslung beim Bauen von AP-11. Der Plan nennt als
Vorlage `Screens/SliceSheet.swift` - das ist das **Simple-Mode-Blatt**.
Gebaut wurde der Block aber im **Advanced-Seitenband**, weil dort das
Ergebnis steht. Beides ist fuer sich richtig, nur war danach das
Simple-Blatt aermer als drueben, und der Plan hielt AP-11 fuer fertig.

Jetzt folgt das Blatt dem Aufbau von drueben: bei mehreren Dateien die
Zahl, ein Sammelexport und eine Zeile je Datei; bei einer Datei der
Export und darunter das Senden; und wenn gar keine Datei entstand, der
Grund statt eines Knopfs, der ins Leere fuehrt.

**Ein Fehler dabei, der zeigt, warum man hinsehen muss.** Zuerst hing die
Fallunterscheidung an der Laenge von `gcodeDateien`. Die Liste fuellt
aber nur ein Lauf ueber *alle* Betten; ein gewoehnlicher Schnitt legt
seine eine Datei in `lastGcode` ab. Ergebnis: nach einem normalen
Schnitt stand „Der G-Code liess sich nicht schreiben." unter Zahlen, die
belegten, dass er sehr wohl geschrieben worden war. Am Bildschirmfoto
sofort zu sehen, im Quelltext nicht.

Belegt am Emulator. Fuer den Drucker-Fall habe ich einen Testdrucker
direkt in die Preferences geschrieben (`run-as de.psmobile`), das
Bildschirmfoto gemacht und ihn wieder entfernt - die Alternative waere
gewesen, die halbe Druckereinrichtung durchzuklicken. Ergebnis: *Export
G-code* prominent, darunter *Send to printer*, darunter *Close*.

### Claude — Zwei Kleinigkeiten, die beim Testen aufgefallen sind

**„Geslict in 0m".** Der Satz im Slice-Blatt nennt, wie lange der
Schnitt gedauert hat, und `SliceSummary.duration()` kannte als kleinste
Einheit Minuten. Ein Schnitt dauert aber selten eine Minute - bei einem
Testwuerfel eine halbe Sekunde. Dort stand dann „0m", was aussieht wie
ein Fehler.

Jetzt stehen unter einer Minute Sekunden da. Der erste Versuch machte
daraus „0s" - dieselbe Null, nur in kleinerer Einheit, weil `toLong()`
abrundet. Unter einer Sekunde steht deshalb „<1s". Das ist die
gemeinsame Regel, gilt also fuer beide Apps; zwei Tests decken beide
Faelle ab. Belegt am Emulator: „Sliced in <1s".

Der Strich fuer „kein Ergebnis" bleibt, wo er war - eine Null waere eine
Aussage, ein Strich sagt, dass nichts vorliegt. Der Unterschied zu
„<1s" ist genau dieser: dort liegt ein Wert vor, er ist nur klein.

**Ein deutscher Schalter im englischen Betrieb.** In den Werkzeugen
stand „In Infill wischen" fest verdrahtet, waehrend die Zeile darunter
(„Wipe into other objects") laengst zweisprachig war. Ein Durchgang
durch alle Beschriftungen mit Umlauten hat sonst nichts gefunden - die
uebrigen Treffer sind Symbole wie „▾".

### Claude — AP-17 g: ZIP hinein, und damit ist ohne den Mac nichts mehr offen

Printables liefert Sammlungen als ZIP - STL neben Bildern, Lizenz und
Beiwerk. Der Kern kann das seit langem auspacken
(`psm_zip_extract_models`, in Kotlin `PsmCore.zipExtractModels`), nur
fuehrte kein Weg vom Dateiwaehler dorthin.

Jetzt schon: `application/zip` steht in den Dateitypen des Waehlers, und
`importOne` schickt eine ZIP an `SlicerService.loadZip` statt an die
Endungspruefung - eine ZIP ist keine Modelldatei, sondern eine Tuete
voll davon. Genau die Reihenfolge wie drueben, wo `loadZip` vor dem
Formatpfad steht (`PSMobileApp.swift:168`). Entpackt wird in einen
eigenen Ordner unter `cacheDir/zip/<zeit>`, danach geht jede lesbare
Datei durch `loadModel`.

**Was zaehlt, ist nicht, was ausgepackt wurde.** Der Kern meldet die
Zahl der entpackten Eintraege; ob daraus Objekte auf dem Bett werden,
sagt erst das Laden. Deshalb zaehlt `loadZip` die *geladenen* Modelle,
nicht die ausgepackten.

**Ein Hinweis, den es vorher nicht gab.** Bei genau einer ausgewaehlten
Datei bleibt der Abschlusshinweis sonst aus - die Rueckfrage zur 3MF
sagt dort selbst, was passiert ist. Eine ZIP stellt keine Rueckfrage,
bringt aber mehrere Modelle mit. Dass aus einer Auswahl zwoelf Teile
werden, gehoert gesagt, also erscheint der Hinweis fuer ZIPs immer.

Belegt am Emulator mit einer ZIP aus zwei STL und einer Textdatei:
„Files loaded - 2 models came from a ZIP archive.", und das Bett zeigt
danach drei Objekte statt einem.

**Ein Fehlversuch dazwischen war meiner:** die erste Test-ZIP enthielt
zwei selbst erzeugte Wuerfel, und libslic3r lehnte beide ab („Loading of
a model file failed"). Das Entpacken hatte funktioniert - im Log standen
beide Dateinamen. Mit den vorhandenen Test-STLs aus dem Emulator lief es
sofort. Wer hier testet, nimmt eine Datei, von der er weiss, dass der
Kern sie liest; sonst sucht man den Fehler im eigenen Code.

**Damit ist ohne den Mac nichts mehr offen.** Alle vier Pakete, die der
Plan als kernpflichtig fuehrte, waren es nicht - dreimal an einem Tag
dieselbe Ueberraschung. Offen bleiben nur AP-04, die iOS-Seite von AP-02
und Abschnitt Z, alle drei am Mac, sowie die beiden zurueckgestellten
Pakete und der Symboldurchgang.

### Claude — AP-14: der zweite Regler, und der erste wechselt die Seite

Die Vorschau hatte auf Android nur den senkrechten Schichtregler, und
der stand rechts - an derselben Kante wie die Seitenleiste. iOS hat ihn
links, wie den Desktop-Regler links vom Bett, und darunter einen
zweiten, waagerechten: welcher Ausschnitt der Werkzeugwege innerhalb der
sichtbaren Schichten gezeigt wird.

Beides steht jetzt auch hier. Der neue `WegSlider` ist derselbe Regler
wie der senkrechte, nur liegend - zwei Griffe, Zahl am Griff.

Die Bindung war wieder schon da: `nativeSetMoveRange` und
`nativeMoveRangeBounds` stehen seit langem in `PsmViewport.kt`, nur
gerufen hat sie niemand. Kein Kernbau. Damit ist das der dritte Fall
heute; die Spalte „Kern" im Plan stand fuer vier Pakete auf „ja" und war
bei allen vieren falsch.

**Warum die Grenzen zum Schichtbereich gehoeren.** Wie viele
Werkzeugwege es gibt, haengt davon ab, welche Schichten gerade sichtbar
sind - jede Aenderung am senkrechten Regler aendert auch die Spanne des
waagerechten. Getrennt gerufen arbeitete man mit den Grenzen von
gestern. `SceneController.setLayerRange(lo, hi, onBounds)` liefert sie
deshalb im selben Zug zurueck, ueber einen Rueckruf, weil der Viewport
auf dem GL-Faden laeuft. Genau wie `ViewportView.swift` es macht.
Uebernommen wird nur, was sich wirklich geaendert hat - sonst
ueberschreibt jeder Bildaufbau eine laufende Ziehgeste; der Vorbehalt
steht so auch drueben im Quelltext.

Beim Oeffnen der Vorschau gibt es noch keine Ziehgeste und damit keinen
Anlass zu fragen. Deshalb fragt `wegAnfragen(n)` einmal nach, sobald der
Schichtbereich steht - sonst saehe man den zweiten Regler erst, nachdem
man den ersten angefasst hat.

Belegt am Emulator: nach dem Schnitt steht unten *10 … 2617*; ein Zug am
senkrechten Regler auf Schicht 127 macht daraus *10 … 160x* - die
Grenzen ziehen also nach -, und ein Zug am rechten Griff auf *7189*
schneidet die Werkzeugwege im Bild sichtbar ab.

Ein Fehlversuch dazwischen war meiner, nicht der App: der erste Wisch
lag knapp neben dem Regler und drehte die Kamera. Wer hier misst, nimmt
die Koordinaten aus einem frischen Bildschirmfoto.

### Claude — AP-13: adaptiv rechnen, und warum „Übernehmen" grau blieb

Die Stützstellenliste fuer variable Schichthoehen hatte auf Android nur
den Weg von Hand. Jetzt steht darueber eine Zeile *Adaptiv* mit Regler
und *Berechnen*, wie drueben (`LayerProfileView.swift`). *Berechnen*
fuellt nur die Zeilen; wirksam wird es erst mit dem bestehenden
*Uebernehmen* - sonst haette dieselbe Seite zwei Wege zum selben Ziel.

Die Bindung war, wie bei AP-10, laengst da: `nativeLayerProfileAdaptive`
im JNI und `PsmCore.adaptiveLayerProfile(...)` in Kotlin. Kein Kernbau.

**Und dann blieb „Uebernehmen" grau.** Das war kein Fehler in der neuen
Zeile. Der Kern liefert das Profil als Treppe: an einer Stufe stehen
zwei Paare mit demselben z. Gemessen an einem 20-mm-Wuerfel: 83 Paare,
darin `(0.2, 0.2)` und `(0.2, 0.25)` direkt hintereinander - das eine ist
das Ende des alten Bandes, das andere der Anfang des neuen. Roh
uebernommen sind die Z-Werte nicht mehr streng steigend, und
`LayerProfile.canApply` haelt das Profil fuer ungueltig. Der Nutzer
saehe nur einen grauen Knopf und keinen Grund.

`fromPoints` zieht doppelte z jetzt zusammen und behaelt den **letzten**
Eintrag - ab dieser Hoehe gilt die neue Dicke. Wichtig ist, *wo* das
steht: in der gemeinsamen Regel, nicht in der Android-Ansicht. iOS ruft
dieselbe Funktion und haette denselben grauen Knopf gehabt, sobald dort
jemand *Berechnen* drueckt. Zwei Tests im `shared`-Modul decken beide
Faelle ab (Zusammenziehen, und was passiert, wenn danach nur ein Punkt
uebrig bleibt).

Nebenbei kam `LayerProfileEditorState` von seiner eigenen Umrechnung weg
- es baute die Zeilen vorher selbst aus `z.toString()`. Jetzt geht auch
das durch die Regel; eine Rechnung weniger, die auseinanderlaufen kann.

Belegt am Emulator: *Compute* macht aus den 83 Kernpunkten „81 height
ranges · 0.20 mm · 0.25 mm …", und *Apply* ist aktiv.

### Claude — AP-10: Anordnen mit Optionen, und ein Kernbau, den es nicht brauchte

Der Plan sagte fuer AP-10 „Kern: ja" und gab die Reihenfolge vor:
Bindung schreiben, Kern bauen (20-25 min auf dem Unraid), dann die
Oberflaeche. Vor dem Anstossen habe ich nachgesehen, was schon da ist -
und die Bindung stand bereits: `psm_arrange_bed_ex` in
`core/include/psmobile_core.h:426`, `nativeArrangeBedEx` in
`android/jni/psm_jni.cpp:1527`, `PsmCore.arrangeBed(...)` samt
`ArrangeStatus` und `ArrangeResult` in Kotlin. Und `grep -ac
nativeArrangeBedEx` auf der abgelegten `libpsmobile_core.so` findet sie
auch dort. Der Kernbau war schlicht nicht noetig.

Dasselbe gilt fuer die drei anderen Pakete, die der Plan als
kernpflichtig fuehrte: `nativeLayerProfileAdaptive` (AP-13),
`nativeSetMoveRange` und `nativeMoveRangeBounds` (AP-14) sowie
`nativeZipExtractModels` (AP-17 g) sind alle in der `.so`. **Kein Paket
im Plan braucht mehr einen Kernbau.** Das ist inzwischen der dritte Fall
nach AP-12 und AP-17 b/c, in dem der Plan aelter war als der Quelltext.
Die Regel steht dort jetzt zum dritten Mal: im Quelltext von heute
nachsehen, nicht in der Historie.

**Was gebaut wurde.** `AnordnenPanel.kt` haengt als kleines Feld am
*Anordnen*-Knopf der Schiene und geht beim Halten auf; Tippen ordnet
weiter sofort an. Vorher war Halten mit „aktuelles Bett" belegt - das war
dieselbe Operation unter anderem Namen und ist jetzt eine Zeile im Feld.
Darin: Zielmodus *Current bed / All beds*, die Zielbettliste mit Schloss
und Objektzahl, Abstand 0-50 mm in Halbschritten, *Allow rotation*, und
darunter der Ergebnissatz. Wie drueben, wo dasselbe als Popover am Knopf
haengt (`BedSelector.swift`, `ArrangePanel`).

**Zwei Quellen, sauber getrennt.** Die Frage „darf ich ueberhaupt?"
beantwortet der gemeinsame `BedStripContract`, nicht die Ansicht - dafuer
kam `AndroidBedStripAdapter.arrangeFuer(...)` dazu, das den Vertrag mit
dem Zielbett als aktivem fragt. Genau die Drehung, die
`arrangeAvailability(for:)` auf iOS macht. Die Meldung *danach* kommt vom
Kern: `ArrangeStatus` unterscheidet *leer*, *gesperrt* und *voll*, sonst
saehe der Nutzer bei einem vollen Bett dieselbe Zeile wie bei einem
leeren.

Im Dienst dazu `arrangeBett(index, gap, drehen)` und
`arrangeAlleBetten(gap, drehen)`; das alte `arrange()` bleibt fuer den
Tipp auf den Knopf.

Belegt am Emulator mit zwei Betten: Ziel Bett 2 (leer) meldet „Bed 2 is
empty. There is nothing to arrange.", Ziel Bett 1 meldet „Bed 1: 1
instances arranged." - wortgleich mit `ArrangePanel.anordnen()`.

**Nebenbefund im Plan.** Die Tabelle unter „Reihenfolge" war zerschossen:
eine frueher eingefuegte Zeile hatte die Zeilen aus „Erledigt" mehrfach
hineinkopiert, sodass Nummern und Pakete nicht mehr zusammenpassten. Sie
ist neu geschrieben, mit einer Spalte *Stand* und den Paketen AP-20 bis
AP-23, die vorher ganz fehlten.

---

## 2026-08-19

### Claude — Bemalen ging nicht, und warum das kein Einzelfall war

**Ausgangsfrage** war, warum sich Dateien nicht bemalen lassen.
**Befund**: Android hat `psm_viewport_set_paint_options` nie gerufen.
Damit blieb `paint_enabled` im Viewport falsch — die Bemalung landete im
Modell, wurde aber nie gezeichnet. iOS ruft es seit langem.

Beim Nachprüfen kamen im selben Werkzeug drei weitere Lücken heraus:
Streichen tat nichts (nur Tippen war verdrahtet), es gab nur den Pinsel
(`psm_model_paint_apply` war nicht gebunden), und jeder Tupfer war ein
eigener Rückgängig-Schritt, weil das Malen über den Weg für schwere
Netzumbauten lief.

Commits `e2f9dad`, `9da2adf`, `ef59dc7`. Am Emulator belegt: eine
durchgehende Spur statt einzelner Punkte, 1358 markierte Facetten nach
einem Strich.

### Claude — Objekt gehört dem Bett, auf das man es zieht

Nutzerreport. Ein Objekt blieb an seinem alten Bett hängen, auch wenn es
sichtbar auf einem anderen lag: die gespeicherte Position ist bettlokal,
der Versatz der Betten steckt allein in der Darstellung.

`psm_viewport_drop_selected` schließt jetzt das Ziehen ab. Android
bekam dazu die räumliche Mehrbett-Darstellung überhaupt erst —
`set_multi_bed_render`, `focus_bed` und `bed_label_anchor` waren nie
gebunden.

Commit `6f17c85`. Belegt: nach dem Ziehen steht `Bed 1 · 0 / Bed 2 · 1`.

**Fehler dabei, der Zeit gekostet hat:** mein erster Test schlug fehl,
weil der wiederhergestellte Arbeitsstand nur *ein* Bett hatte — die
Funktion kehrte korrekt bei `bed_models.size() <= 1` zurück. Ich habe
eine Diagnose-Ausgabe in den Viewport gebaut, den Kern zweimal gebaut
und erst dann gemerkt, dass der Testaufbau falsch war, nicht der Code.
Vor dem nächsten Diagnose-Kernbau: erst den Aufbau prüfen.

### Claude — Der Gleichstand ist größer als gedacht

Der Nutzer hat viermal nachgefragt, ob die Liste vollständig sei. Sie war
es dreimal nicht. Die Verfahren im Vergleich:

| Verfahren | findet | blind für |
|---|---|---|
| Begriffe suchen | fehlende Funktionen | alles andere |
| C-Schnittstelle vergleichen | nicht gebundene Funktionen | reine Oberfläche |
| `git log -- ios/` | nie übertragene Entscheidungen | — |
| Quelltext Datei für Datei | Ausarbeitung, Wortwahl | Abstände, Rhythmus |

Die Zahl, die es entschieden hat: **90 Commits fassen `ios/` an, ohne
`android/` anzufassen — ~5.700 geänderte Zeilen in 27 Ansichtsdateien.**
Wer nur den Zustand vergleicht, findet das nie.

Daraus entstand `docs/00-gleichstand-implementierungsplan.md`. Er ist ab
sofort der Einstiegspunkt, vor diesem Journal. Die README nannte bis
heute „Android-first" — das ist überholt und hat mit dazu beigetragen,
dass beide Fassungen auseinandergelaufen sind.

### Claude — AP-01 bis AP-05

- **AP-01** `psTouch()`: Zielflächen fallen nicht mehr unter 44 dp. Die
  Schrift war längst gedämpft, die Trefferflächen nicht — bei
  `MIN_SCALE 0.7` wurde aus 48 dp effektiv 33,6 dp. 118 Stellen.
- **AP-02** `Corners` im gemeinsamen Modul: 164 Stellen. Vorher zwölf
  verschiedene Radien auf iOS, fünfzehn auf Android.
- **AP-18** 17 Bindungen nachgezogen. Von 26 fehlenden Funktionen sind
  noch 2 übrig, beide nur Legenden.
- **AP-03** Vorschau mit Statistik, Legende und Verbrauch je Werkzeug.
  `PreviewRange` liegt im gemeinsamen Modul mit sechs Tests.
- **AP-04**, **AP-05** teilweise — siehe Plan.

### Claude — AP-07 Materialauswahl, und ein Planungsfehler

Die Auswahl zeigt jetzt Suchfeld, Typ-Knoepfe als echten Filter,
Farbpunkte der haeufigsten Farben und Karten mit einer echten Spule.
Die Regeln dafuer lagen seit langem als `FilamentCatalog` im
gemeinsamen Modul und wurden nur von iOS benutzt.

Commit `875a655`. Belegt: Typ-Filter PLA blendet von neun Typen auf vier
Karten ein.

**Planungsfehler, der auffiel:** im Plan stand bei AP-07 „kein Kernbau
noetig". Falsch — `psm_preset_option_at` war nicht gebunden, und ohne
das gibt es weder Typ noch Farbe je Profil. Beim Einplanen zuerst
pruefen, ob die Datenquelle ueberhaupt erreichbar ist, nicht nur ob die
Oberflaeche Compose ist.

**Nicht angefasst und bewusst so:** die iOS-Seite von AP-02 und der
ganze Abschnitt Z. Der Mac unter `192.168.1.107` antwortet nicht.
Geschriebener, aber ungebauter Swift-Code zählt nicht.

**Was beim Binden auffiel:** `psm_slice_accept_remote_gcode` nimmt eine
Szenenrevision entgegen. Wer sie nicht mitgibt, legt ein Ergebnis von
vorhin auf eine Anordnung von jetzt. Das wäre ein stiller Datenfehler
geworden.

### Claude — AP-08 Einstellungskopf, und eine Stunde am falschen Baum

Der Kopf der Einstellungen zeigte nur *Zurück* und die drei Reiter.
Welches Profil man gerade veraendert, stand nirgends; ein anderes
waehlen ging nur ueber den Umweg zurueck zum Bett. Jetzt stehen rechts
der Profilname (Tipp oeffnet die Suche) und, sobald es etwas gibt, der
Zaehler der ungespeicherten Werte (Tipp oeffnet die Rueckfrage).

`SlicerService.profilaenderungen()` sammelt ueber alle drei Sammlungen
und holt den lesbaren Namen aus dem Kern (`configMeta.label`) — nicht
aus einer Liste in der Oberflaeche, die beim naechsten
PrusaSlicer-Sprung veraltet. Die Suche ist `filterPresetOptions` aus
dem Advanced-Seitenband; eine zweite Suche mit eigenen Regeln waeren
zwei Antworten auf dieselbe Frage.

Commit `bb47ae1`. Belegt am Emulator: Kopf zeigt
`0.10mm FAST DETAIL @COREON…`, nach *Perimeters* 2 → 4 erscheint der
Zaehler `1`, die Rueckfrage nennt „Perimeters 2 → 4", Zuruecksetzen
stellt 2 wieder her, und die Suche filtert auf „SOLUBLE" von acht auf
zwei Profile.

**Statt der Lupe der Trichter.** PrusaSlicers Symbolsatz hat keine
Lupe, und eigene Symbole kommen nicht dazu (E-12). iOS zeigt dort ein
SF-Symbol; das ist die eine Stelle, an der beide Fassungen bewusst
verschieden aussehen.

**Der Fehler, der die meiste Zeit gekostet hat:** ich habe die Sitzung
in der falschen Kopie begonnen. Die Freigabe
`\\Localunraid\n3dp\KI Projekte\psmobile-parity-buildhost\repo` sieht
aus wie der Arbeitsstand — sie hat denselben Plan, dieselben Quellen
und war am selben Tag angefasst worden. Sie ist aber nur die
Build-Host-Spiegelung fuer den nativen Kern; ihr Git steht auf dem
10.08., und der Plan darin ist eine Kopie ohne den Abschnitt „Stand
und naechster Schritt". Ich habe darin AP-03 ein zweites Mal gebaut
(JNI und Kotlin lagen schon da, ich habe `PreviewRange` und die
Compose-Karte ergaenzt), bevor mir der Gradle-Daemon-Log den echten
Pfad verriet: der Worktree unter `C:\Users\Nils\.codex\worktrees\…`.
Die Doppelarbeit ist zurueckgenommen, die Spiegelung steht wieder auf
ihrem Stand von 19:58.

Zwei Dinge haetten das verhindert, beide jetzt im Plan unter **„Wo die
Arbeit liegt"**: die Tabelle der drei Kopien mit dem Satz, welche
bearbeitet wird — und der Hinweis, dass die Planfassungen in den
anderen beiden Kopien sind. Wer eine Kopie findet, in der `git log`
aelter ist als das Datum im Plankopf, hat die falsche.

**Zwei Fallstricke beim Bauen unter Windows**, ebenfalls jetzt im Plan:
`ANDROID_SDK_ROOT` steht auf `S:\PC-Auslagerung\Android\Sdk` — wer
zusaetzlich `ANDROID_HOME` setzt, bekommt von Gradle „several
environment variables … contain different paths to the SDK". Und die
Freigabe ist von Windows aus nur lesbar; Aenderungen dort gehen ueber
SSH als `root`.

**Nicht angefasst und bewusst so:** die iOS-Seite. Der Mac unter
`192.168.1.107` antwortet weiterhin nicht.

### Claude — AP-06 Schwebende Dialoge, und ein Fenster, das 48 px zu tief sitzt

`SchwebenderDialog` steht in `ui/SchwebenderDialog.kt` und traegt
Einstellungen, Drucker und ColorMix. Die drei ersetzten bisher den
ganzen Bildschirm; jetzt liegen sie als Karte mit Rand ringsherum ueber
dem Arbeitsbereich, und Werkzeugleiste, Bett und Seitenband bleiben
sichtbar. Ein Tipp daneben schliesst.

Commit `469203a`. Belegt am Emulator, gemessen am Bildschirmfoto:
104 px Rand oben, 120 px unten, 80 px links und rechts — oben und unten
jeweils der eigene Rand plus die Systemleiste.

**Der Fehler, der drei Anlaeufe gekostet hat.** Mit
`usePlatformDefaultWidth = false` ist das Dialogfenster so hoch wie der
Bildschirm, sitzt aber unter der Statusleiste — es ragt genau um deren
Hoehe unten heraus. Die Karte klebte an der Unterkante: 104 px Rand
oben, 8 px unten. Nacheinander versucht und alle wirkungslos:
`windowInsetsPadding(safeDrawing)`, `WindowInsets.safeDrawing.
asPaddingValues()`, `DialogProperties(decorFitsSystemWindows = false)`,
`WindowCompat.setDecorFitsSystemWindows` am echten Fenster und
`FLAG_LAYOUT_NO_LIMITS`. Erst die Rechnung aus der Ansicht der
Aktivitaet minus Systemleisten, mit der Karte oben angeschlagen, sitzt.

**Was mich dabei laenger im Kreis laufen liess als noetig:** ich habe
den Rand aus Bildschirmfotos gemessen und die Helligkeit in der
*Mittelspalte* geprueft — dort liegt der weisse Gestenbalken. Der zaehlte
als „Karte", und der untere Rand kam mehrfach falsch heraus. Wer so
misst, nimmt eine Spalte abseits der Mitte, oder besser die Kanten
(Farbsprung) statt einer Schwelle. Ausserdem hat einmal die
Navigation nicht gegriffen (die App war noch auf der Startseite), und
ich habe das Ergebnis trotzdem als Messung genommen — bei jedem
Messfoto zuerst pruefen, ob ueberhaupt das Richtige zu sehen ist.

**Nachgezogen im selben Zug** (`1e785c0`): Ersteinrichtung,
App-Einstellungen und die Druckerverwaltung von der Startseite. Die drei
lagen in `MainActivity` in derselben if/else-Kette wie der
Arbeitsbereich - solange eine dran war, wurde alles andere gar nicht
gezeichnet. Sie stehen jetzt als Ueberlagerung hinter der Kette.
`SchwebenderDialog` hat dafuer `abbrechbar` bekommen: beim allerersten
Start gibt es aus der Ersteinrichtung keinen Weg hinaus, dort darf auch
ein Tipp daneben nicht schliessen.

**Bewusst nicht umgestellt:** Profilwechsel und ZIP-Frage. Beide sind
kurze Rueckfragen und liegen schon im `AlertDialog` aus `ui/theme`, der
`ScaledOverlay` selbst mitbringt - in eine formatfuellende Karte
gefasst waeren sie groesser als ihre Frage. `RemoteSliceScreen` ersetzt
weiterhin den Bildschirm; es hat auf iOS kein Gegenstueck, deshalb sagt
der Vergleich dort nichts.

### Claude — AP-05, obere Leiste und die Fusszeile der Schiene

Die obere Leiste liest jetzt *Start | Neu · Oeffnen · Projekte · Sichern
· Sichern unter · Vorschau | Simple*, unten stehen nur noch Zurueck/Vor
und die Blickwinkel, und unten in der Werkzeugschiene stehen *Drucker*
und *App-Einstellungen*. Commit `c41a1ba`, am Emulator belegt.

Drei Entscheidungen, die keine reine Uebertragung waren:

**Oeffnen** fuehrt in denselben Dateiwaehler wie Import. iOS hat dafuer
zwei Knoepfe (Modell/Projekt); unter Android fragt das System bei einer
3MF ohnehin nach, ob sie als Projekt oder nur als Objekte hereinkommt -
dieselbe Unterscheidung, eine Stelle weniger.

**Der Vorschau-Umschalter** ist von unten nach oben gewandert. Unten
stand er zwischen Oben/Vorn/Hinten, also zwischen lauter Blickwinkeln -
das Umschalten zwischen Bett und Werkzeugwegen ist aber ein
Arbeitsschritt und kein Blickwinkel.

**Sichern unter bleibt.** Im Plan stand, iOS frage beim Sichern nach dem
Namen und brauche deshalb keinen zweiten Knopf. Unter Android kommt der
Name aus dem Systemdialog, und ein offenes Projekt still zu
ueberschreiben ist dort das erwartete Verhalten. Ein Knopf, der bei
jedem Sichern den Systemdialog aufruft, waere schlechter statt gleicher.
Steht als bewusste Ausnahme im Plan.

**Zwei Stolpersteine beim Umbau:** `MODEL_MIME_TYPES` liegt im Paket
`de.psmobile`, nicht in `de.psmobile.ui` - aus der Oberflaeche heraus
also qualifiziert. Und mein Einfuegepunkt fuer den Projekte-Dialog lag
oberhalb der Zustandsdeklaration, die er benutzt; in Compose faellt das
erst beim Uebersetzen auf, weil beides in derselben Funktion steht.

**Offen an AP-05:** *Stuetzen* und *Naht* in der Schiene, *Trennen* als
Untermenue, *Anordnen* per Tipp und Halten. Alle drei mit konkretem
Vorgehen im Plan - inklusive des Hinweises, dass die Schiene aus
PrusaSlicers eigener `toolbar.json` aufgebaut wird und die beiden
Malwerkzeuge dort nicht vorkommen.

### Claude — AP-22: alle Betten schneiden

Nils' Meldung, gebaut in der Nachtschleife. `startSliceAlleBetten()`
geht ueber einen eigenen Service-Befehl wie der einzelne Schnitt; die
Schleife nimmt alle Betten mit Objekten, schneidet jedes und legt
`bett-N.gcode` ab. Am Ende steht wieder das Bett aktiv, von dem aus
gestartet wurde.

Commit `6bd3438`. Belegt am Emulator: nach dem Tippen liegt
`files/bett-1.gcode` mit 53020 Bytes da, und *Export G-code* erscheint.

**Zwei Dinge, die auffielen.** Der Prozentwert faengt je Bett von vorn
an - ohne einen zweiten Zaehler saehe man beim dritten von fuenf Betten
dieselben 40 Prozent wie beim ersten. Deshalb `bettFortschritt` neben
`Progress`. Und `selectBed()` ruft `invalidateSliceResult()`, was
`lastGcode` loescht; die Datei wird deshalb erst nach der Schleife
gesetzt.

**Ein Fehler beim Einbauen:** mein Einfuegepunkt lag zwischen
`var lastGcode` und dessen `private set`. Kotlin meldete daraufhin „A
'val' property cannot have a setter" an einer ganz anderen Zeile - die
Fehlermeldung zeigte auf die Folge, nicht auf die Ursache. Vor dem
Einfuegen die zwei Zeilen unter dem Anker mitlesen.

**Offen:** die Dateien sind noch unsichtbar. Das ist AP-11 und steht als
naechster Schritt im Plan.

### Claude — AP-11: die Dateien aus dem Mehrbett-Schnitt sind sichtbar

Seit AP-22 entstanden beim Schneiden aller Betten mehrere Dateien - man
sah aber nur „Fertig" und hatte keinen Weg zu vier von fuenf. Der
Ergebnisblock zeigt jetzt die Zahl und je eine Zeile mit Namen und
eigenem Knopf.

Commit `8afd69d`. Belegt am Emulator: Wuerfel per Kopieren/Einfuegen auf
zwei Betten, dann *2 G-code files* mit `bett-1.gcode` und
`bett-2.gcode`; beide liegen mit 53020 Bytes in `files/`.

**Zwei Dinge, die beim Bauen auffielen.** Der Name auf dem Drucker kam
bisher aus `output_filename_format` - bei fuenf Betten waere das fuenfmal
derselbe Name gewesen, und der zweite Auftrag haette den ersten
ueberschrieben. Bei mehreren Dateien traegt der Auftrag deshalb den
Dateinamen. Und `shareableGcodeUri` raeumte den share-Ordner jedes Mal
leer; bei mehreren Dateien haette das Teilen der zweiten die erste
geloescht.

**Ein Werkzeugfehler, der Zeit gekostet hat:** mein Ersetzungsanker
`val dst = File(outDir, suggestedGcodeName())` kam zweimal vor - einmal
im Teilen, einmal im USB-Export. Das Skript hat es gemeldet statt blind
zu ersetzen; die Pruefung auf genau eine Fundstelle hat sich zum zweiten
Mal bezahlt gemacht.

**Offen an AP-11:** *Alle exportieren*, die Wortwahl im Simple-Blatt und
der Hinweis bei einem Schreibfehler. Steht im Plan.

### Claude — AP-11 zu Ende

Die drei Reststuecke: *Alle exportieren* teilt jetzt alle Dateien in
einem Durchgang (`ACTION_SEND_MULTIPLE`), das Simple-Blatt sagt
„Export G-code" statt „Sichern" und nennt Dauer und Dateizahl, und ein
Bett, dessen Datei sich nicht schreiben laesst, beendet nicht mehr den
ganzen Lauf - die Namen stehen als Hinweis im Ergebnisblock.

Commit `c63e329`. Belegt am Emulator: Advanced mit *2 G-code files*,
*Export all* und je einer Zeile fuer `bett-1.gcode` und `bett-2.gcode`;
das Simple-Blatt mit *Ready to print*, *Sliced in 0m*, den Zahlen,
*2 G-code files* und *Export all*.

**Nebenbefund:** `onShareGcode` im Simple Mode war nie verdrahtet. Der
Parameter hatte eine leere Vorgabe, und `MainActivity` hat sie nie
ueberschrieben - „G-Code sichern" fuehrte dort seit jeher ins Leere.
Solche Vorgaben verstecken fehlende Verdrahtung; wer einen Rueckruf mit
`= {}` anlegt, sollte im selben Schritt pruefen, ob ihn jemand setzt.

**Beim Bedienen des Emulators gelernt:** *Kopieren* wird erst
klickbar, wenn wirklich etwas ausgewaehlt ist, und der erste Tipp nach
dem App-Start geht oft ins Leere. Zwei Anlaeufe haben gefehlt, bis der
Wuerfel auf dem zweiten Bett lag. Bei solchen Ketten nach jedem Schritt
ein Bild machen, statt am Ende zu raten.

**Kleiner Rest:** `SliceSummary.duration()` meldet bei einem halben
Sekundenlauf „0m". Unter einer Minute gehoeren Sekunden dorthin; die
Regel liegt im gemeinsamen Modul und betrifft damit beide Seiten.

### Claude — AP-05 zu Ende: die linke Schiene

*Stuetzen* und *Naht* stehen jetzt in der Schiene, *Zu Objekten* und
*Zu Volumen* sind zu **Trennen** mit Untermenue zusammengefasst, und
*Anordnen* nimmt beim Tippen alle Betten, beim Halten nur das aktuelle -
der eigene Knopf *Aktuelles Bett* entfaellt damit.

Commit `f0c314a`. Belegt am Emulator: die Schiene liest Import ·
Loeschen · Leeren · Anordnen · Kopieren · Einfuegen · +Kopie · -Kopie ·
Optionen · Trennen · Stuetzen · Naht, darunter Drucker und
App-Einstellungen; das Trennen-Menue zeigt *To objects* und *To parts*.

**Was dabei zu beachten war:** die Schiene wird aus PrusaSlicers eigener
`toolbar.json` aufgebaut. *Stuetzen* und *Naht* kommen dort als
Leistenknopf nicht vor - sie gehoeren hinter die Schleife, nicht hinein.
Die drei zusammengefassten Eintraege (`arrangecurrent`, `splitobjects`,
`splitvolumes`) fallen dagegen aus der Schleife heraus.

**Zwei Kleinigkeiten:** `combinedClickable` verlangt ein ausdrueckliches
`@OptIn(ExperimentalFoundationApi)` - die Datei hatte den Import schon,
aber nur an einer anderen Funktion. Und die Beschriftung des
Trennen-Knopfes kam weiter aus `shortToolLabel("splitobjects")` und las
deshalb erst „Objekte"; den Tooltip zu aendern reicht nicht.

**Ein Fehler in der Buchfuehrung:** beim Fortschreiben des Plans habe
ich den Abschnitt „Als Naechstes" ersetzt, ohne zu sehen, dass der
alte AP-05-Text darunter weiterlief - der Abschnitt nannte AP-05
danach zweimal. Beim Ersetzen ganzer Abschnitte den Bereich zwischen
zwei Ueberschriften nehmen, nicht ein Textstueck daraus.

### Claude — AP-12, und ein Planpunkt, der ueberholt war

Der lange Druck auf ein Bett oeffnet jetzt ein Menue mit *Umbenennen*
und, bei einem leeren Bett, *Entfernen* - vorher sprang er sofort ins
Umbenennen. Commit `f03c7b0`, am Emulator belegt.

**Der eigentliche Fund war ein anderer.** AP-12 verlangte als Erstes,
die Bettleiste aus dem Easy Mode zu nehmen, mit Verweis auf `04b2c3d`
vom 6. August. Ein Blick in die heutige `SimpleModeView.swift` zeigt sie
dort aber wieder - `6875fd2` vom 9. August hat sie zurueckgeholt. Haette
ich den Punkt einfach abgearbeitet, waere Android um eine Leiste aermer
gewesen, die iOS laengst wieder hat.

**Daraus fuer den Plan:** ein Commit-Verweis sagt nur, was einmal
passiert ist. Vor dem Nachbauen pruefen, was drueben *heute* im
Quelltext steht - `git log -S` auf die Datei kostet zehn Sekunden.

### Claude — AP-16: das Band scrollt zum Bearbeiten-Bereich

Seit AP-20 klappt *Bearbeiten* bei einer Auswahl von selbst auf. Bei
offenen Profilen lag es aber unterhalb des sichtbaren Fensters - nach
dem Antippen eines Objekts geschah scheinbar nichts. Der Bereich meldet
jetzt seine Position im Band, und die Auswahl scrollt dorthin.

Commit `82f3fa8`. Belegt am Emulator: Wuerfel antippen zeigt *EDIT* mit
Objektnamen und den Griffen direkt unter den drei Einstellungszeilen.

**Der Stolperstein:** ohne ein abgewartetes Bild scrollt man an die
Stelle, an der der Bereich *vor* dem Aufklappen lag - die gemeldete
Position stammt noch aus dem alten Layout. `withFrameNanos { }` zwischen
Aufklappen und Scrollen genuegt.

**Nebenbei erledigt:** der zweite Teil des Pakets („Fokus bleibt beim
Blaettern layoutstabil") ist seit AP-20 gegenstandslos - es gibt nur
noch einen durchgehenden Scrollbereich statt vier, die beim Reiterwechsel
zurueckgesetzt wurden.

### Claude — AP-15: Druckerkarten in beiden Modi

Der Advanced Mode hatte fuer den Drucker ein Aufklappmenue mit rohen
Profilnamen. Die Kartenansicht gab es laengst - aber nur im Simple Mode.
Sie liegt jetzt als `ui/DruckerAuswahl.kt` fuer sich und wird von beiden
benutzt; die Karten stehen untereinander statt zu zweit nebeneinander,
wie drueben, und der Materialhinweis ist weg (er stimmte im Advanced
Mode nicht).

Commit `50de001`. Belegt am Emulator: unter PRINTER die Karte „Prusa
CORE One L MMU3 · SELECTED · OFFLINE · Nozzle 0.4 nozzle" mit orangem
Rand, darunter „Add printer".

**Ein Werkzeugfehler, der zurueckzunehmen war:** ich wollte die Karte
per Skript aus `SimpleModeScreen.kt` herausschneiden und suchte das Ende
mit `index("@Composable", index("SimplePrinterGlyph"))` - das fand das
*naechste* `@Composable` hinter der Glyphe und haette 78 Zeilen zu viel
entfernt. `git checkout` auf die Datei, dann der einfachere Weg:
`private` zu `internal` machen und die neue Datei die vorhandenen
Bausteine aufrufen lassen. Beim Ausschneiden von Quelltext lieber die
Sichtbarkeit aendern als Zeilen verschieben.

### Claude — AP-17 b, und wieder die Frage nach der Richtung

Die Proportionsvorschau gab es auf Android nur im Modelle-Blatt. Der
Plan nannte sie als Android-Luecke mit Verweis auf `0befe95` - und
dieser Commit sagt im Text selbst, er sei das „Gegenstueck zu
ObjectProportionThumb auf Android". iOS hat also *nachgezogen* und dabei
beide Listen bedacht; Android hatte sie in der Liste im Seitenband nie.

Jetzt steht dasselbe Kaestchen auch dort, abschaltbar ueber denselben
App-Schalter. Commit `d919c3d`, am Emulator belegt an der Zeile
„testwuerfel · 20 × 20 × 20 mm · 1 Vol.".

**Weiterhin gilt, was AP-12 gelehrt hat:** der Commit-Text ist die
zweite Quelle nach dem heutigen Quelltext. Hier stand die Richtung sogar
in der Commit-Nachricht - wer nur die Zeile im Plan liest, baut in die
falsche Richtung.

### Claude — AP-17 f: der Zoll-Schalter tut endlich etwas

Der Schalter stand in der gemeinsamen Liste, wurde auf Android aber von
niemandem gelesen - deshalb war er ausgeblendet, mit dem Vermerk „wieder
hereinnehmen, sobald Android sie umsetzt". Jetzt rechnet
`SettingsScreen` um: Felder mit der Einheit „mm" zeigen Zoll und nehmen
Zoll entgegen, der Kern bekommt weiterhin nur Millimeter.

Commit `bcb4361`. Belegt am Emulator: mit dem Schalter an steht bei
*Layer height* „0.0039 in" statt „0.1 mm".

**Nur Laengen.** Prozentwerte und Temperaturen bleiben, was sie sind -
auf iOS heisst die Pruefung `istLaenge`, und genau diese Einschraenkung
war dort der Grund, warum es ueberhaupt eingebaut wurde.

**Ein Horcher auf die Preferences** haelt die Anzeige aktuell, wenn der
Schalter umgelegt wird, waehrend die Einstellungsseite offen ist - seit
AP-06 schweben die App-Einstellungen darueber. Drueben ist genau das
einmal schiefgegangen: ein Millimeterwert unter dem Etikett „in", und
das naechste Speichern haette ihn nochmals mit 25,4 multipliziert.

**Beim Bedienen des Emulators verlaufen:** ich habe die Bedienelemente
im App-Einstellungen-Blatt aus einem alten Bildschirmfoto angetippt, aber
das Blatt war diesmal nicht gescrollt - der Tipp landete auf „Deutsch",
und die App stand ploetzlich auf Deutsch. Vor jedem Tipp in ein Blatt,
das gescrollt sein kann, ein frisches Bild machen.

### Claude — AP-17 c und e: einmal nichts zu tun, einmal umgeraeumt

**c war ueberholt.** Der Plan verlangte einen Abschnitt „Je Extruder" in
der Simple-Materialseite. Android hat dort laengst die *Materialpalette*:
Kacheln je Kopf mit Farbe und Material, dazu *Alle setzen* und eine
Farbreihe. iOS zeigt an derselben Stelle nur eine Liste mit Farbfeld und
Menue - die Android-Fassung ist die reichere. Also nichts nachzuziehen;
der Punkt steht jetzt umgekehrt in Abschnitt Z.

**e war echt.** Der Reinigungsturm stand unter den Projektwerkzeugen,
zwischen Plattenexport und eigenem G-Code - dabei ist er nur bei
mehreren Koepfen ueberhaupt eine Frage. Er steht jetzt direkt unter der
Extruderbank und erscheint erst ab zwei Extrudern, wie auf iOS.

Commit `431d961`. Belegt am Emulator: WIPE TOWER mit X 180.0, Y 140.0,
Rotation 0.0 und „Apply wipe tower" unter der Bank.

**Zum Verschieben von Quelltext, zum zweiten Mal:** ich habe wieder
versucht, einen Block per Index zu schneiden, und wieder war die
Bereichsgrenze falsch. Diesmal habe ich vor dem Schnitt die drei
Randzeilen geprueft (Anfang, Ende, Trenner danach) und bei
Abweichung abgebrochen - so faellt es auf, bevor die Datei geschrieben
ist. Und was aus einer Datei herauswandert, braucht oft nur `internal`
statt eines Umzugs: `ProjectNumberField` blieb, wo es war.

### Claude — AP-17 a: weitergeben statt nur speichern

Plattenexport, repariertes STL und gewandelter G-Code landeten ueber den
Systemdialog in einer Datei - und damit war Schluss. iOS bietet an
derselben Stelle „Weitergeben · <Dateiname>" an, also den Weg in eine
andere App. Der Dienst merkt sich jetzt die zuletzt geschriebene Datei
und macht sie ueber den FileProvider teilbar; die Projektwerkzeuge
zeigen den Knopf mit dem Dateinamen darin.

Das Speichern ueber den Systemdialog bleibt, wie es ist - es kommt nur
der zweite Weg dazu.

Commit `18817d5`. **Noch nicht am Bildschirm belegt:** der Knopf
erscheint erst nach einem Export, und dorthin fuehrt der Weg durch den
Systemdialog. Der naechste Durchgang macht genau das und haelt es fest;
solange steht das Paket im Plan als „gebaut, aber nicht belegt".

Das ist bewusst so notiert. Ein Paket, das gebaut aussieht und nie
angesehen wurde, ist die haeufigste Art, sich selbst zu belegen - der
Plan sagt seit dem ersten Tag, dass ohne Bildschirmfoto nichts fertig
ist.

### Claude — AP-17 a belegt

Der Weg am Emulator: Objekt waehlen, Bereich *Werkzeuge*, „Bed as STL",
im Systemdialog speichern - danach steht „Share · <Datei>" im Band.
Commit `5605ea0`.

**Dabei aufgefallen:** der Knopf las den Namen aus dem Zwischenspeicher
(`druckbett-106695083330542.stl`). Die Eindeutigkeit gehoert dorthin,
aber niemand will sie im Knopf lesen - er zeigt jetzt den Namen, unter
dem gespeichert wurde.

**Und eine Werkzeugerkenntnis, die eine halbe Runde gekostet hat:** das
rechte Band schien festgefahren - vier Anlaeufe mit `input swipe`
bewegten nichts, und ich habe schon an einer Regression aus AP-16
gezweifelt. Es lag an der Wischdauer: 250 ms wird als Fling verschluckt,
900 ms scrollt sauber. Steht jetzt im Plan unter „Worauf zu achten ist",
damit der naechste nicht dieselbe Fehlersuche macht.

### Claude — AP-17 d: die Mischfarbe steht jetzt vor dem Speichern da

Man waehlte zwei Koepfe und einen Anteil und tippte auf „hinzufuegen" -
welche Farbe dabei herauskommt, sah man erst danach in der Liste. Jetzt
steht die Vorschau darueber: Farbfeld und Hexwert, gerechnet vom
gemeinsamen `ColorMixCodec`, also dieselbe Zahl wie drueben.

Commit `492799b`. Belegt am Emulator: T1 und T2 bei 50 % ergeben
`#ED6941`, danach steht „Virtual extruder 6 · T1: 50 % · T2: 50 %" in
der Liste.

**Vorschau und Rezept aus derselben Rechnung.** Zwei getrennte Wege
waeren die naheliegende Falle: die Vorschau zeigt eine Farbe, gespeichert
wird eine andere, und niemand merkt es, bis der Druck laeuft. Das Rezept
traegt die Farbe jetzt mit, wie auf iOS.

**Damit ist alles zu, was ohne Kernbau und ohne Mac geht.** Was bleibt,
steht im Plan: vier Pakete mit Kernbau, die Mac-Sachen und die beiden
zurueckgestellten. Fuer den naechsten Schritt (AP-10) habe ich die
Reihenfolge in den Plan geschrieben - Bindung zuerst, dann Kernbau, sonst
baut man die alte Fassung.

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

2026-08-09 Task5 Fix Round3: JNI-Codec als produktive, host-testbare Einheit
extrahiert; Supplementary-Codepoint und ungültiges Surrogat mit g++ grün.
Unraid-Docker (29.5.2, psmobile-ndk:1) ist erreichbar; isolierter Exact-HEAD-
Build scheitert vor Kompilierung an vorhandenen Boost-Abhängigkeiten (<1.83).
Keine alten .so-Dateien verwendet.

Provenienzkorrektur: Der erste temporäre Build war kein Git-Checkout und wurde
verworfen. Der echte Bundle-Checkout `/tmp/psmobile-task5-r3-git` steht auf
`f6f5a72`; Docker-Konfiguration scheitert dort reproduzierbar an fehlenden
Boost-1.83-Komponenten (`system`, `regex`) vor dem Kompilieren.
