# Stopp-Punkte

Stand: 2026-07-29, nach der Nachtsitzung.

Das hier ist die Liste zum gemeinsamen Durchgehen. Sortiert nach
Dringlichkeit, nicht nach Aufwand.

---

## 1. Entscheidungen, die ich nicht allein treffen sollte

### 1.1 Eigene OAuth-Client-ID fuer Prusa Connect
**Blockiert den Connect-Upload vollstaendig.**

PrusaSlicer spricht mit `account.prusa3d.com` per OAuth2/PKCE, mit der
Client-ID `oamhmhZez7opFosnwzElIgE2oGgI2iJORSkw587O` und dem Redirect
`prusaslicer://login`. Beides gehoert PrusaSlicer. Wir koennen es
technisch mitbenutzen, sollten es aber nicht: gegenueber Prusas
Auth-Server gaeben wir uns damit als PrusaSlicer aus.

Der Upload selbst ist klar - zwei Aufrufe, beide aus `PrusaConnect.cpp`
uebernommen:
1. `POST {host}/app/users/teams/{team_id}/uploads` mit Bearer-Token
2. `PUT {host}/app/teams/{team_id}/files/raw?upload_id=...`

**Zu klaeren mit Prusa** (passt in dieselbe Anfrage wie die Lizenz,
siehe `06-anfrage-prusa.md`): eigene Client-ID plus ein Redirect-Schema
fuer die App.

**Ohne das** geht PrusaLink lokal sofort - der braucht nur einen
API-Key, kein OAuth.

### 1.2 Der Name
"PSMobile" ist Arbeitstitel. Fuer eine Veroeffentlichung braucht es
einen Namen ohne Prusa-Bezug. Markenrechtlich unabhaengig von der
Lizenzfrage.

### 1.3 Wie exakt soll die Werkzeugleiste sein?
Uebernommen sind alle 15 Werkzeuge aus `GLCanvas3D.cpp` in
Originalreihenfolge, mit Original-Icons und -Tooltips. Am Desktop laeuft
sie **waagerecht ueber** dem Bett - ich habe sie **senkrecht links**
gesetzt, weil 15 Knoepfe a 56 dp quer die halbe Bettbreite fressen.
Das ist die eine bewusste Abweichung. Umdrehen ist eine Zeile.

---

## 2. Was ich nicht uebernehmen konnte

### 2.1 25 Parameter fehlen in den Einstellungen
Der Extraktor liest `Tab.cpp` statisch. An 25 Stellen fuegt PrusaSlicer
Optionen ueber Variablen ein (`optgroup->append_single_option_line(option)`)
statt ueber einen Literalnamen. Die kann kein statischer Leser aufloesen.

Sichtbar wird das an leeren Gruppen, etwa "Horizontale Konturhuellen".

**Optionen**: von Hand nachtragen (ueberschaubar, aber Nachbau), oder
die Struktur zur Laufzeit aus einer laufenden PrusaSlicer-Instanz
abgreifen (sauberer, deutlich mehr Aufwand).

### 2.2 Der Drucker-Tab ist unvollstaendig
`TabPrinter::build_fff()` liefert 4 Seiten. Die Extruder-Seiten baut
PrusaSlicer dynamisch nach Extruderzahl (`build_extruder_pages`), die
fehlen.

### 2.3 wxWidgets-Widgets
Grundsaetzlich nicht portierbar, siehe E-01. Betrifft Dialoge,
Menueleiste, die dreispaltige Parametertabelle.

---

## 3. Bekannte Fehler und Luecken

| Punkt | Zustand |
| --- | --- |
| Objekt verschieben | **Fehlt.** Auswaehlen geht, bewegen nicht. Braucht Gizmos oder wenigstens Ziehen in der Bettebene. |
| Objekt skalieren/drehen | Fehlt, dito. |
| Startzeit | ~14 s beim allerersten Start (Ressourcen entpacken), danach je nach Druckerauswahl 2-4 s. |
| Filamentgewicht | Stimmt jetzt, sobald ein Filamentprofil gewaehlt ist. |
| G-Code-Vorschau | Fehlt komplett (M6, `libvgcode`). |
| Undo/Redo | Werkzeuge sind sichtbar, aber inaktiv. |
| Ausschneiden/Einfuegen | dito. |
| Layer-Editing | dito. |
| iOS | Vorbereitet, nie gebaut - braucht den Mac. |
| Objektliste | Zeigt nur Name und Groesse. Die Baumstruktur mit Volumen und Modifikatoren fehlt. |

---

## 4. Was in dieser Sitzung fertig wurde

- **Viewport (M4)**: GLES-Renderer mit PrusaSlicers eigenen ES-Shadern,
  Bett aus der Druckerkonfiguration, Auswahl in Gruen, Gesten,
  Ansichtsleiste.
- **Extraktor (E-12)**: 20 Einstellungsseiten, 247 Parameter,
  15 Werkzeuge, 21 Sprachen mit 103446 Eintraegen, 37 Original-Icons.
- **Einstellungen**: vollstaendiger Bildschirm mit Originalstruktur,
  Original-Tooltips und dem Simple/Advanced/Expert-Filter aus
  `PrintConfig`.
- **Ersteinrichtung**: Druckerauswahl beim ersten Start. Mit einem
  MK4S bleiben 10 Drucker, 6 Druckprofile und 175 Filamente uebrig
  statt 221/520/5762.
- **Sprache**: Englisch als Standard, 21 weitere umschaltbar.
- **Profilfilter**: Nur zum gewaehlten Drucker passende Profile.

---

## 5. Arbeitsliste - hier weitermachen

**Diese Datei ist die Uebergabe.** Wer kalt hier hereinkommt, liest
`README.md`, `docs/entscheidungen.md` (vor allem E-12: uebernehmen statt
nachbauen) und dann diese Liste.

### Bauen und pruefen

```bash
# Kern (Docker auf localunraid, SSH-Key ~/.ssh/unraid_aipp)
ANDROID_ABI=x86_64 bash build/scripts/build-core.sh
bash build/scripts/stage-native.sh x86_64
bash build/scripts/build-apk.sh

# Auf dem Emulator PSM_Tablet pruefen
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Nach Aenderungen am Kern **immer** `stage-native.sh` vor `build-apk.sh` -
sonst liegt die alte .so im APK.

### Offen, nach Wert sortiert

- [x] **Objekt verschieben** - Ziehen in der Bettebene, fertig.
- [ ] **Objekt skalieren und drehen** - Gizmos oder numerische Felder in
      der Seitenleiste, wie PrusaSlicers Object Manipulation.
      `psm_model_set_rotation` und `psm_model_set_scale` gibt es schon im
      ABI, es fehlt nur die Oberflaeche.
- [x] **PrusaLink** - Verwaltung, Verbindungstest, Upload gebaut.
      **Aber nie gegen ein echtes Geraet gelaufen** - im Emulator gibt es
      keinen Drucker. Erster Test mit einem echten Drucker steht aus.
- [ ] **Filter "nur eingerichtete Drucker"** - Der Schalter existiert und
      wird gespeichert, filtert aber noch nichts. Ansatzpunkt:
      `SlicerService.refreshPresets()` gegen
      `PrinterStore.all(context).map { it.presetName }` filtern, wenn
      `PrinterStore.onlyLinked(context)` gesetzt ist.
- [x] **Gesendete Dateien sichern** - ueber das Storage Access Framework,
      damit auch eingebundene Netzlaufwerke gehen. Ebenfalls ungetestet.
- [ ] **API-Schluessel verschluesselt ablegen** - liegt derzeit in
      gewoehnlichen SharedPreferences. Vor einer Veroeffentlichung auf
      EncryptedSharedPreferences umstellen.
- [ ] **Simple-Modus vereinfachen** - derzeit nur eine kuerzere Liste.
      Ziel: Qualitaet, Material, Fuellung, Stuetzen, Haftung, dann
      slicen. Der volle Baum bleibt einen Fingertipp entfernt.
- [ ] **G-Code-Vorschau** (M6, `libvgcode`, ~5600 LOC, schon portabel).
- [ ] **Objektliste vertiefen** - Baum mit Volumen und Modifikatoren.
- [ ] **Die 25 fehlenden Parameter** - Entscheidung noetig, siehe 2.1.
- [ ] **Connect-Upload** - erst wenn die Client-ID geklaert ist, siehe 1.1.
- [ ] **Stift-Painting** - das Alleinstellungsmerkmal, siehe unten.

### Warum Stift-Painting das Ziel ist

Prusa EasyPrint ist ein Cloud-Slicer: ~60 s Rechenzeit je Platte,
Tageslimit, grosse Modelle werden abgelehnt, kein vollstaendiger
Einstellungsbaum. PSMobile slict auf dem Geraet - keine dieser Grenzen.

Bei allen anderen Funktionen sind wir bestenfalls gleichwertig zum
Desktop. Supports, Naht und MMU-Farben mit dem Stift aufzumalen ist die
einzige Funktion, bei der die mobile Version **besser** ist als der
Desktop. Darauf sollte das Projekt hinauslaufen.
