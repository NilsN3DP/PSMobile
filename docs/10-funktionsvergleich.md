# Funktionsvergleich PrusaSlicer 2.9.6 ↔ PSMobile

Stand: 29.07.2026, nach dem Lauf mit den Einstellungsseiten.
SLA bleibt ausserhalb des Umfangs; alle Zahlen unten sind reine FFF.

## Wie gemessen wird

Nicht nach Augenmass. `build/scripts/gap-report.py` stellt gegenueber:

1. **Parameter** — alle Optionen aus `libslic3r/PrintConfig.cpp` gegen
   die Schluessel in unserer `tabs.json`.
2. **Werkzeuge am Modell** — das `EType`-Enum aus `GLGizmosManager.hpp`.
3. **Menuebefehle** — die `append_menu_item`-Aufrufe aus `MainFrame.cpp`.

Der Bericht laesst sich jederzeit erneut fahren; er ist die Grundlage
fuer alles hier.

---

## 1. Parameter

```
PrintConfig definiert insgesamt   560
davon SLA                         190
FFF-relevant                      370
in PSMobile erreichbar            317
fehlend                            53
```

Zum Vergleich: am Morgen des 29.07. waren es 247. Der Sprung kam nicht
durch Abtippen, sondern weil `extract-ui.py` drei Muster in `Tab.cpp`
nicht kannte. Alle drei sind inzwischen abgedeckt:

| Muster im Original | Was dadurch fehlte |
|---|---|
| `option = optgroup->get_option("k"); … append_single_option_line(option)` | saemtliche Custom-G-code-Felder |
| Seiten aus `build_extruder_pages` / `build_kinematics_page` / `build_unregular_pages` | die gesamte Retraction, Machine limits |
| `line.append_option(optgroup->get_option("k"))` — zwei Werte unter einer Beschriftung | `top_solid_layers`, `first_layer_temperature`, `bed_temperature`, Luefterdrehzahlen |

### Die verbleibenden 53, einzeln eingeordnet

**a) Artefakte der Messung, keine echten Optionen (3)**

`machine_max_feedrate_`, `machine_max_acceleration_`, `machine_max_jerk_`

PrintConfig baut diese Namen selbst in einer Achsenschleife zusammen.
Der Bericht sieht den Praefix. Die echten Schluessel (`…_x`, `…_y`,
`…_z`, `…_e`) sind vorhanden. Nichts zu tun.

**b) Interne Buchfuehrung, am Desktop nirgends bedienbar (20)**

`printer_technology`, `printer_model`, `printer_vendor`,
`printer_variant`, `print_settings_id`, `printer_settings_id`,
`filament_settings_id`, `physical_printer_settings_id`, `preset_name`,
`preset_names`, `profile_vendor`, `profile_version`, `inherits`,
`inherits_cummulative`, `compatible_printers_condition_cummulative`,
`compatible_prints_condition_cummulative`, `default_filament_profile`,
`default_print_profile`, `extrusion_axis`, `filament_vendor`

Diese Werte stehen in den Profildateien und werden vom Programm
gepflegt, nicht vom Nutzer. `filament_vendor` benutzen wir bereits — zur
Gruppierung der Materialliste, nicht als Einstellzeile. Nichts zu tun.

**c) In 2.9 abgeloest oder nur noch fuer die Kommandozeile (4)**

`colorprint_heights` (durch Custom-G-code-Eintraege ersetzt),
`infill_only_where_needed` (entfernt), `thumbnails_format` (in
`thumbnails` aufgegangen), `duplicate_distance` (nur CLI).
Nichts zu tun.

**d) Am Desktop nicht sichtbar (4)**

`seam_preferred_direction`, `seam_preferred_direction_jitter` (im
Quelltext auskommentiert), `solid_layers`, `solid_min_thickness`
(Sammelwerte, die auf top/bottom durchschlagen). Nichts zu tun.

**e) Eigene Dialoge am Desktop — echte Luecken (14)**

| Schluessel | Dialog im Original | Aufwand |
|---|---|---|
| `bed_shape`, `bed_custom_texture`, `bed_custom_model` | `BedShapeDialog` — Form, Groesse, eigene Textur und Modell | mittel |
| `wiping_volumes_matrix`, `wiping_volumes_use_custom_matrix` | `WipingDialog` — Reinigungsmengen als Matrix Filament×Filament | mittel, fuer MMU wichtig |
| `filament_ramming_parameters` | `RammingDialog` — Rammkurve als Diagramm | hoch |
| `gcode_substitutions` | `SubstitutionManager` — Suchen-und-Ersetzen im Ausgabe-G-code | gering |
| `print_host`, `host_type`, `printhost_apikey`, `printhost_port`, `printhost_cafile`, `printhost_user`, `printhost_password`, `printhost_ssl_ignore_revoke`, `printhost_authorization_type` | `PhysicalPrinterDialog` | teilweise vorhanden |

Zum letzten Punkt: wir haben eine eigene PrusaLink-Verwaltung mit
Adresse, Benutzer und Passwort. Sie speichert aber in den
SharedPreferences statt im Druckerprofil, kennt keine anderen
Host-Typen (OctoPrint, Repetier, FlashAir, AstroBox, MKS) und
unterstuetzt kein Zertifikat.

**f) Am Objekt statt im Profil (4)**

`extruder`, `extruder_colour`, `wipe_into_infill`, `wipe_into_objects`

Die gehoeren in den Objektbaum, nicht auf eine Profilseite.
`extruder_colour` ist ueber die Extruderfarben in der Seitenleiste
bereits erreichbar; `extruder` je Objekt und je Teil ist Aufgabe 26 und
die eigentliche Sperre fuer echtes Multicolor.

### Fazit Parameter

Von 370 sind **317 erreichbar**, **31 brauchen nichts** (Gruppen a–d),
**14 sind echte Luecken** in Form von fuenf Spezialdialogen und
**4 gehoeren an den Objektbaum**.

---

## 2. Seiten

Der Druckerbaum stimmt seit heute mit dem Desktop ueberein — geprueft
gegen das Belegfoto eines XL-5T:

```
General | Custom G-code | Machine limits | Single extruder MM setup
        | Extruder 1 … 5 | Notes | Dependencies
```

Die Extruderseiten entstehen zur Laufzeit, eine je Eintrag in
`nozzle_diameter`, genau wie `TabPrinter::build_extruder_pages`.

Druck (10 Seiten) und Filament (6 Seiten) sind ebenfalls vollstaendig.
Was auf den Seiten *Dependencies* steht, ist noch leer: dort gehoert der
Abhaengigkeitsbaum hin, den der Desktop als eigenes Widget zeichnet.

---

## 3. Werkzeuge am Modell

PrusaSlicer kennt dreizehn (ohne die beiden SLA-Werkzeuge):

| Werkzeug | PSMobile | Bemerkung |
|---|---|---|
| Verschieben | teilweise | nur Ziehen mit dem Finger, keine Zahleneingabe |
| Skalieren | fehlt | ABI `psm_model_set_scale` liegt bereit |
| Drehen | fehlt | ABI `psm_model_set_rotation` liegt bereit |
| Flach legen | fehlt | Flaeche antippen, Objekt richtet sich danach |
| Schneiden | fehlt | `libslic3r/CutUtils` ist GUI-frei, portierbar |
| Vereinfachen | fehlt | `libslic3r/QuadricEdgeCollapse`, GUI-frei |
| Stuetzen bemalen | fehlt | braucht `TriangleSelector` plus eigenen Pinsel |
| Naht bemalen | fehlt | dito |
| Fuzzy Skin bemalen | fehlt | dito |
| MMU-Farbe bemalen | fehlt | dito, das teuerste Stueck |
| Messen | fehlt | |
| Text praegen | fehlt | braucht Schriftbehandlung |
| SVG praegen | fehlt | |

**Neu in dieser Runde entdeckt:** *Vereinfachen* und *Fuzzy Skin
bemalen* standen in keiner meiner frueheren Listen. Vereinfachen ist
davon das guenstigste — reine Geometrie, kein wx.

---

## 4. Menuebefehle

`MainFrame.cpp` hat 68 Eintraege. Nach Bereichen:

**Datei — vorhanden:** Modell importieren (STL/3MF/OBJ/AMF), G-code
exportieren, G-code senden, Slicen.

**Datei — fehlt:** Neues Projekt, Projekt oeffnen, Projekt speichern,
Projekt speichern unter, STL in imperialen Einheiten importieren,
ZIP-Archiv importieren, Konfiguration importieren/exportieren,
Konfigurationsbuendel importieren/exportieren, Platte als STL/OBJ
exportieren (mit und ohne Stuetzen), Werkzeugwege als OBJ exportieren,
ASCII-G-code nach binaer wandeln und zurueck, STL reparieren.

**Bearbeiten — fehlt vollstaendig:** Alles auswaehlen, Auswahl
aufheben, Auswahl loeschen (nur ueber die Werkzeugleiste), Alles
loeschen (vorhanden), Rueckgaengig, Wiederholen, Kopieren, Einfuegen,
Neu von Datei laden, Suchen.

**Ansicht — vorhanden:** Iso, Oben, Unten, Vorn, Hinten, Links, Rechts,
3D-Editor, Vorschau.

**Fenster — fehlt:** Reiter direkt anspringen (seit heute ueber die
Reiterleiste da), Formenbibliothek, Warteschlange fuer Druckerhosts,
Profile vergleichen.

Von den 68 sind rund **20 sinnvoll uebertragbar**; der Rest ist
Desktop-Eigenheit (Neue Instanz, Konfigurationsordner zeigen,
Tastenkuerzel) oder gehoert zu SLA.

---

## 5. Hauptbildschirm

Das Belegfoto der Desktop-Seitenleiste zeigt: Druckprofil, Filament (bei
MMU fuenf Zeilen mit Farbe), Drucker, dann **Supports**, **Infill**,
**Brim** als direkte Bedienelemente, darunter die Objektliste und
"Slice now".

Bei uns sind Profile und Filamente je Extruder seit heute da. Es fehlen:

* Die drei Schnellzugriffe Supports, Infill, Brim
* Ein Baum statt einer flachen Objektliste
* Betthaftung, Schalenstaerke und Infill-Muster nach dem Vorbild von
  EasyPrint (Belegbild): Infill mit Bild, Decke/Wand/Boden je in
  Millimetern und Schichten

---

## 6. Was seit dem letzten Bericht dazugekommen ist

* G-Code-Vorschau ueber libvgcode, mit Schichtregler
* Material und Farbe je Extruder, samt Farbwaehler
* Alle Einstellungsseiten des Druckers inklusive Retraction
* Reiterleiste Druck / Filament / Drucker
* Aenderungen leben im `edited preset`, damit ein Profilwechsel sie
  nicht mehr kommentarlos wegwirft
* G-Code-Dateiname aus `output_filename_format`; dabei kam heraus, dass
  bgcode laengst lief

**Behobene Fehler:** Bett wechselte beim Druckerwechsel nicht mit;
neu geladenes Objekt war unsichtbar; `\n` stand woertlich in den
G-code-Feldern; Filamentwerte zeigten die ganze Reihe ueber alle Duesen.

---

## 7. Reihenfolge

1. **Objekt-Schnelleinstellungen** mit Zwei-Finger-Skalierung — Aufgabe 25
2. **Schnellzugriffe Infill, Schalen, Stuetzen** im Hauptschirm — 34
3. **Abhaengigkeiten ausgrauen** mit Begruendung — 27
4. **Objektbaum mit Extruder je Teil** — 26, danach ist Multicolor echt
5. **Filamentauswahl mit Suche und Herstellern** — 33
6. **Profiländerungen behalten und speichern**, Nachfrage beim Wechsel — 21
7. **Projekte** neu, oeffnen, speichern als 3MF — 35
8. **Rueckgaengig und Wiederholen** — GUI-frei, guenstig
9. **Vereinfachen, Schneiden, Flach legen** — GUI-freie Geometrie
10. **Bettform, Reinigungsmengen, G-code-Ersetzungen** — die drei
    guenstigen Spezialdialoge
11. **Mehrfachauswahl und Dateiliste** — 39
12. **Reinigungsturm verschieben** — 36
13. **Zwei Oberflaechen Einfach/Experte** — 41, zum Schluss
14. Die Bemal-Werkzeuge — das teure Ende
