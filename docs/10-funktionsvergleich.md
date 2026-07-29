# Funktionsvergleich PrusaSlicer 2.9.6 (Desktop) ↔ PSMobile

Stand: 29.07.2026. Alles hier ist am Quelltext oder am Geraet nachgeprueft,
nicht geschaetzt. Zahlen stammen aus:

* `android/app/src/main/assets/psui/tabs.json` (extrahierte Einstellungen)
* `grep -c "def = this->add(" src/libslic3r/PrintConfig.cpp` → 573 Optionen
* Logcat der laufenden App

---

## 1. Was vollstaendig da ist

| Bereich | Zustand |
|---|---|
| Slicing-Kern | libslic3r 2.9.6 unveraendert. Gleiche Eingabe → gleicher G-Code wie am Desktop. |
| Modellimport | ueber `FileReader::load_model`: STL, OBJ, 3MF, AMF, SVG, ZIP. **STEP fehlt** (`SLIC3R_ENABLE_FORMAT_STEP=OFF`, OCCT nicht gebaut). |
| Herstellerprofile | 221 Drucker, 520 Druckprofile, 5762 Filamente geladen; nach Ersteinrichtung gefiltert auf das Gewaehlte. |
| Ersteinrichtung | Drucker + Duesenvariante, wie der Configuration Assistant. MMU-Modelle sind in der Liste. |
| Presets waehlen | Drucker / Druck / Filament, mit Kompatibilitaetsfilter. |
| Einstellungen | 247 Parameter auf 20 Seiten, Simple/Advanced/Expert aus `PrintConfig`. |
| Sprachen | 21, 103.446 Strings aus den `.po` von Prusa. Englisch ist Standard. |
| 3D-Ansicht | GLES 2.0 mit PrusaSlicers eigenen ES-Shadern, Auswahl, Verschieben, Ansichtsknoepfe. |
| Anordnen | `psm_arrange` = die Arrange-Logik des Originals. |
| Slicen | Fortschritt, Abbruch, Statistik. |
| G-Code | Export und Teilen. |
| PrusaLink | Senden mit Digest-Auth, Druckerverwaltung, Backup auf Netzlaufwerk. **Nie gegen einen echten Drucker getestet.** |
| G-Code-Vorschau | libvgcode, seit 29.07. Reiter "3D editor view / Preview" plus Schichtregler. |

## 2. Was fehlt

### 2.1 Bearbeitung am Modell — die groesste Luecke

| Desktop | PSMobile |
|---|---|
| Verschieben-Gizmo | Nur Ziehen mit dem Finger, keine Zahleneingabe |
| Skalieren-Gizmo | fehlt (ABI `psm_model_set_scale` existiert) |
| Drehen-Gizmo | fehlt (ABI `psm_model_set_rotation` existiert) |
| Flach legen | fehlt |
| Schneiden (Cut) | fehlt (`libslic3r/CutUtils` ist wx-frei) |
| Messen | fehlt |
| Text / SVG praegen | fehlt |
| Bemalen: Stuetzen, Naht, MMU-Farbe | fehlt komplett |
| Variable Schichthoehe | fehlt |
| Modifikatoren, Negativvolumen, Stuetzblocker/-erzwinger | fehlt |
| Objektbaum mit Instanzen und Einstellungen je Objekt | nur flache Liste |
| Rueckgaengig / Wiederholen | fehlt — `slic3r/Utils/UndoRedo.*` ist wx-frei und portierbar |

### 2.2 Projekte

3MF-Projekt speichern und laden fehlt. Damit gibt es keine Moeglichkeit,
einen Stand aufzuheben. `libslic3r/Format/3mf.hpp` kann beides und haengt
an keiner GUI.

### 2.3 Fehlende Einstellungsseiten

247 von 573 Optionen sind erreichbar. Nicht extrahierbar waren die Seiten,
die `Tab.cpp` zur Laufzeit aufbaut:

* **Drucker → Extruder 1..N** — hier liegt die gesamte **Retraction**
  (Laenge, Geschwindigkeit, Lift Z, Wipe, Retract on layer change).
  Das ist die schmerzhafteste Luecke.
* **Custom G-code** — Start, Ende, vor/nach Schichtwechsel, Werkzeugwechsel,
  zwischen Objekten, Farbwechsel, Pause. Auch bei Filament.
* **Machine limits** — Beschleunigung, Ruck, Maximalgeschwindigkeiten.
* **Bed shape** — Bettform und -groesse.
* **Notes**, **Dependencies** — leer.

### 2.4 Verhalten der Einstellungen

Siehe Abschnitt 3.

### 2.5 Multimaterial

Siehe Abschnitt 4.

### 2.6 Sonstiges

* SLA vollstaendig (Kern koennte es, UI nicht)
* Sequenzieller Druck: Vorschau und Kollisionspruefung
* Prusa Connect, Physical Printers
* Konfigurations-Schnappschuesse
* Suchfeld in den Einstellungen (`Ctrl+F` am Desktop)
* Systeminfo, Update-Pruefung

---

## 3. Sind die Einstellungen echt oder Schmuck?

**Echt.** Der Weg ist durchgehend:

```
SettingsScreen  →  core[key] = value  →  psm_config_set
                →  s->config (DynamicPrintConfig)
                →  print->apply(model, config)   (psmobile_core.cpp:515)
```

`psm_config_set` schreibt in genau die `DynamicPrintConfig`, die beim Slicen
an `Print::apply` geht. Ein geaenderter Wert landet im G-Code. Unbekannte
Schluessel und ungueltige Werte werden abgelehnt (`set_deserialize_nothrow`),
es gibt also keine stillschweigend verschluckten Eingaben.

Aber es fehlen drei Dinge, die der Desktop kann:

1. **Ein Presetwechsel wirft alle Aenderungen weg.**
   `psm_preset_select` endet mit `s->config = s->presets->full_config()`
   (`psmobile_presets.cpp:452`). Am Desktop bleiben Aenderungen als
   "modified" stehen, werden orange markiert und man wird gefragt.
   Bei uns sind sie kommentarlos weg.

2. **Nichts ueberlebt einen App-Neustart.**
   In den SharedPreferences liegen nur `printers` und `lang`. Weder die
   Presetauswahl noch geaenderte Werte werden gespeichert.

3. **Keine Abhaengigkeitslogik.**
   Der Desktop graut ueber `toggle_print` Felder aus, die im aktuellen
   Zustand wirkungslos sind (z. B. alle Stuetzenparameter, wenn Stuetzen
   aus sind). Bei uns ist alles immer bedienbar. Der Wert wird zwar
   gesetzt, tut aber nichts — das kann verwirren.

Dazu fehlt der "geaendert"-Marker und "als Preset speichern".

---

## 4. Multicolor / Multimaterial?

**Nein, noch nicht — trotz gruener Vorzeichen.**

Was da ist:

* MMU3-Druckermodelle stehen in der Ersteinrichtung
  (CORE One MMU3, MK4S MMU3, …) und lassen sich waehlen.
* Die Druckseite **Multiple Extruders** mit 28 Parametern ist vorhanden:
  Wipe Tower, Ooze Prevention, Extruder fuer Perimeter/Infill/Stuetzen.
* `update_multi_material_filament_presets()` wird beim Presetwechsel
  aufgerufen, die Filamentliste wird also auf die Extruderzahl gedehnt.

Was fehlt, und ohne das bleibt es einfarbig:

1. **Nur ein Filamentplatz in der UI.** Bei fuenf Extrudern bekommen alle
   fuenf dasselbe Filament. Es gibt keinen Weg, Slot 2 anders zu belegen.
2. **Keine Extruderzuweisung je Objekt oder je Teil.** Am Desktop ist das
   die Spalte im Objektbaum. Ohne sie druckt alles aus Extruder 1.
3. **Kein MMU-Bemalen.**
4. **Kein Farbwechsel (M600) im Schichtregler** — am Desktop das "+" am
   Regler in der Vorschau.

Ein MMU-Drucker laesst sich also auswaehlen und slicen; das Ergebnis ist
ein einfarbiges Teil mit Wipe Tower. Fuer echtes Multicolor braucht es
Punkt 1 und 2, danach 4 und zuletzt 3.

---

## 5. Reihenfolge nach Nutzen pro Aufwand

1. **Extruder-Seite mit Retraction** — die Parameter existieren in
   `PrintConfig`, nur die Seite fehlt. Kann `extract-ui.py` nachgereicht
   bekommen, indem die Extruderseite statisch erzeugt wird.
2. **Custom G-code + Machine limits + Bed shape** — dieselbe Mechanik.
3. **Skalieren und Drehen** — ABI liegt bereit, es fehlt nur die UI.
4. **Rueckgaengig/Wiederholen** — `UndoRedo.*` ist wx-frei.
5. **3MF-Projekt speichern/laden**.
6. **Presetaenderungen behalten und speichern** + Neustartfestigkeit.
7. **Mehrere Filamentplaetze + Extruder je Objekt** → Multicolor.
8. **Schneiden**, dann die Bemal-Werkzeuge (aufwendig, viel eigene GL-Arbeit).
