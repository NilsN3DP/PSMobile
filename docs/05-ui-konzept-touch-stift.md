# UI-Konzept: Touch und Stift

Stand: 2026-07-28

## Leitgedanke

PrusaSlicer ist maus-zentriert: Rechtsklick, Hover-Tooltips, Tastenkuerzel,
dichte Menues, kleine Ziele. Nichts davon existiert auf einem Tablet.
Eine 1:1-Uebertragung waere schlechter als beide Welten.

Umgekehrt kann Touch etwas, was die Maus nicht kann: **direkt anfassen**.
Und der Stift kann etwas, was auch die Maus nicht kann: **Druck, Neigung,
Hover**. Genau daran orientiert sich das Konzept.

---

## Gesten-Grammatik

Die zentrale Designregel: **Finger navigieren, Stift arbeitet.**
Damit entfaellt der uebliche Modus-Konflikt zwischen "Kamera bewegen" und
"Objekt bearbeiten" komplett, sobald ein Stift im Spiel ist.

| Eingabe | Aktion |
| --- | --- |
| 1 Finger Tap | Objekt auswaehlen |
| 1 Finger Tap ins Leere | Auswahl aufheben |
| 1 Finger Drag auf Objekt | Objekt in XY verschieben, bettgebunden |
| 1 Finger Drag ins Leere | Kamera orbit |
| 2 Finger Drag | Kamera pan |
| 2 Finger Pinch | Zoom |
| 2 Finger Rotate | Kamera-Roll |
| 2 Finger Doppeltipp | Ansicht zuruecksetzen |
| Long-Press auf Objekt | Radialmenue direkt am Finger |
| **Stift Tap** | praezise Auswahl, nie Orbit |
| **Stift Drag** | Malen bzw. Gizmo ziehen |
| **Stift Druck** | Pinselradius |
| **Stift Hover** (Pencil) | Vorschau des Pinselflecks unter der Spitze |
| **Stift Doppeltipp am Schaft** | Malen / Radieren umschalten |
| **Stift Neigung** | weicher Pinselrand |

### Warum das wichtig ist
Ohne Stift muss ein Finger-Drag auf einem Objekt entscheiden: verschieben
oder Kamera drehen? Jede Loesung dafuer ist ein Kompromiss. Mit Stift gibt
es die Frage nicht mehr - deshalb ist die Stiftunterstuetzung hier keine
Zusatzfunktion, sondern loest ein Grundproblem der Bedienung.

---

## Bildschirmaufteilung

### Tablet (Querformat, Hauptzielgeraet)
```
+--+------------------------------------------+--------+
|W |                                          | Objekt-|
|e |                                          | liste  |
|r |            3D-Bett, vollflaechig         | (ein-  |
|k |                                          | klapp- |
|z |                                          | bar)   |
|e |                                          |        |
|u |                                          |        |
|g |                                          |        |
+--+------------------------------------------+--------+
|  [Profil v]  [Slicen]        [Vorschau]  [Exportieren]|
+-------------------------------------------------------+
```
Werkzeugleiste links, weil dort die Hand ohnehin am Geraet ruht.
Objektliste rechts, einklappbar. Aktionsleiste unten.

### Telefon (Hochformat)
```
+-------------------------------+
|                               |
|        3D-Bett                |
|                               |
|                               |
+-------------------------------+
| [Werkzeuge in Daumenreichweite]|
+-------------------------------+
|  Bottom-Sheet: Profil/Slicen  |
+-------------------------------+
```
Alles Wichtige unterhalb der Bildschirmmitte. Nichts oben, was man
regelmaessig antippen muss.

---

## Einstellungen in drei Tiefen

PrusaSlicer hat einige hundert Parameter. Die alle in eine mobile Liste zu
kippen waere unbenutzbar - sie wegzulassen aber auch falsch.

1. **Einfach** - fuenf Regler, die 90 % der Faelle abdecken:
   Schichthoehe, Fuellung, Stuetzen, Brim, Material.
2. **Erweitert** - die ueblichen zwei Dutzend.
3. **Experte** - die vollstaendige Parameterliste.

Der Clou: Ebene 3 wird **generiert**, nicht handgebaut.
`libslic3r/PrintConfig.cpp` beschreibt jeden Parameter bereits
datengetrieben - Typ, Bereich, Einheit, Tooltip, Enum-Werte. Daraus laesst
sich die Experten-UI automatisch erzeugen. Das spart nicht nur Arbeit,
sondern haelt die App bei jedem PrusaSlicer-Update automatisch aktuell.

---

## Fortschritt und Wartezeit

Ein Modell, das der Desktop in 30 s slict, braucht auf dem Handy eher
3 bis 5 Minuten. Das ist Physik, kein Fehler - aber die UI muss es
tragen:

- Fortschritt mit **benannter Phase** ("Perimeter", "Fuellung",
  "Stuetzen"), nicht nur Prozent. Das macht Wartezeit ertraeglich.
- Jederzeit abbrechbar.
- Weiterarbeiten waehrend des Slicens moeglich.
- Laeuft in einem Foreground-Service - der Bildschirm darf aus.
- Fertigmeldung als Benachrichtigung.
- Bei sehr grossen Modellen aktiv Remote-Slicing anbieten statt
  stillschweigend 20 Minuten zu rechnen.

---

## Was aus der Desktop-UI ersatzlos entfaellt

- Menueleiste
- Tastenkuerzel als primaerer Bedienweg
- Hover-Tooltips als primaerer Erklaerweg
- Rechtsklick-Kontextmenues (ersetzt durch Long-Press-Radialmenue)
- Fenster-in-Fenster-Dialoge (ersetzt durch Sheets)
- Die dreispaltige Parametertabelle

## Was besser wird als auf dem Desktop

- **Painting mit dem Stift.** Supports und Naht aufmalen ist mit der Maus
  fummelig und mit dem Stift natuerlich. Das ist der Grund, warum sich
  dieses Projekt lohnt.
- **Direkte Manipulation.** Ein Objekt mit dem Finger schieben ist
  unmittelbarer als Zahlen in Felder zu tippen.
- **Modell ansehen, wo der Drucker steht.** Tablet in der Hand an der
  Maschine statt zurueck zum Schreibtisch.
