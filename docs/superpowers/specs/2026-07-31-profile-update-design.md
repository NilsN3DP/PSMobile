Exit code: 0
Wall time: 0.4 seconds
Output:
# Android-Profilupdates – Design

## Ziel

Die Android-App sucht bei jedem Start nebenläufig nach einer neueren,
offiziellen Prusa-Profilbasis. Neue Drucker-, Düsen-, Filament- und
Printprofile – insbesondere CORE One INDX 4T/8T – sollen ohne neues APK
verfügbar werden. Die App muss offline vollständig benutzbar bleiben und
darf weder laufende Slices noch ungespeicherte Projekt- oder
Profiländerungen gefährden.

## Nicht im Umfang

- Firmware-, App- oder Slicer-Binärupdates.
- Updates während eines laufenden Slice-Jobs aktivieren.
- PrusaLink-Liveabgleich oder INDX-Hardwareverwaltung.
- Stille automatische Installation ohne Nutzerentscheidung.

## Quellen und Vertrauen

Die ausgelieferte Profilbasis bleibt der unveränderliche Offline-Fallback.
Ein Updatekanal liefert ein versionsiertes Manifest und das zugehörige
Profilpaket ausschließlich per HTTPS. Das Manifest enthält mindestens
Version, Abrufadresse, SHA-256 des Pakets, minimale unterstützte
libslic3r-Version sowie eine nutzerlesbare Liste der Neuerungen.

Vor der Installation prüft die App:

1. HTTPS-Transport und erwarteten Host.
2. Format und Versionsschema des Manifests.
3. SHA-256 des heruntergeladenen Pakets.
4. `min_slic3r_version` der neuen Profilbasis gegen die eingebaute
   Slicer-Version.
5. Dass das Paket eine vollständige, lesbare Profilbasis enthält.

Fehlt eine Prüfung oder schlägt sie fehl, bleibt der letzte als gültig
markierte Stand aktiv. Fehlermeldungen werden protokolliert, aber der
Arbeitsbereich bleibt offen und blockiert nicht.

## Speicherung und Aktivierung

Es existieren drei getrennte Zustände im App-Speicher:

- **fallback:** aus dem APK entpackte Profilbasis;
- **active:** zuletzt vollständig geprüfte und aktivierte Profilbasis;
- **staged:** vollständig heruntergeladenes, geprüftes, aber noch nicht
  aktiviertes Update.

Downloads werden zunächst in ein temporäres Verzeichnis geschrieben und
erst nach allen Prüfungen atomar nach `staged` verschoben. Beim Aktivieren
wird `active` atomar ersetzt; die bisherige `active`-Version bleibt als
Rollback verfügbar, bis die neue Slicer-Sitzung erfolgreich geöffnet ist.

Der Start prüft nur den Manifest-Header nebenläufig. Die UI startet mit
`active` oder, falls diese nicht existiert, `fallback`; ein fehlendes Netz
oder ein Timeout verzögern den Start nicht.

## Nutzerablauf

Wenn eine unbekannte, kompatible Version vorliegt, zeigt die App genau
einmal pro Start einen Dialog:

> Neue Drucker- und Materialprofile verfügbar
>
> [Neuerungen aus dem Manifest]
>
> **Jetzt aktualisieren** · **Später** · **Erst beim nächsten Update
> fragen**

- **Jetzt aktualisieren** lädt, prüft und staged das Paket.
- **Später** unterdrückt den Dialog nur für den aktuellen App-Start;
  dieselbe Version wird beim nächsten Start wieder angeboten.
- **Erst beim nächsten Update fragen** merkt die angebotene Version als
  übersprungen; erst eine höhere Version meldet sich erneut.

Nach erfolgreichem Download zeigt die App:

> Profile aktualisiert
>
> **Jetzt verwenden** · **Beim Neustart**

Bei **Beim Neustart** wird `staged` beim nächsten App-Start aktiviert.

Bei **Jetzt verwenden** läuft derselbe Schutzablauf wie bei einem
Projekt- oder Profilwechsel:

1. Ein laufender Slice blockiert die sofortige Aktivierung mit einer
   verständlichen Erklärung; das Update bleibt staged.
2. Bei geladenem Projekt oder ungespeicherten Profiländerungen erscheint
   die bestehende Entscheidung **Speichern unter**, **Änderungen
   übertragen**, **Verwerfen** oder **Abbrechen**.
3. **Abbrechen** belässt Sitzung und Profilbasis unverändert.
4. Erst nach einer bestätigten sicheren Entscheidung wird die
   `SlicerService`-Sitzung kontrolliert geschlossen, die staged
   Profilbasis aktiviert und eine neue Sitzung mit dem laufenden Projekt
   geöffnet.
5. Scheitert das Öffnen der neuen Sitzung, rollt die App auf den
   bisherigen aktiven Profilstand zurück und erklärt den Fehler.

## INDX und ColorMix

Die eingespielte aktuelle Profilbasis liefert CORE One INDX 4T/8T und
deren Druck-, Filament-, Toolchange- und Bettgeometrieprofile. Sie ist
Voraussetzung, aber nicht Ersatz, für die separat zu planende
Android-ColorMix-Oberfläche.

ColorMix verwendet virtuelle Extruder: Zwei oder drei reale Düsen werden
in einem wiederkehrenden Schichtmuster gewichtet kombiniert; außerdem
sind Höhengradienten möglich. Die vorhandene MMU-Flächenbemalung bleibt
eine direkte physische Düsenwahl und darf virtuelle Rezepte nicht
überschreiben.

## Architektur

`ResourceInstaller` wird zu einer Profilbasis-Verwaltung erweitert. Ein
kleiner `ProfileUpdateRepository` kapselt Manifestabruf, Download,
Prüfungen, atomare Dateivorgänge und die Persistenz von
"übersprungener"/"angebotener" Version. `SlicerService` erhält nur eine
klare Operation zum kontrollierten Wechsel der Ressourcenbasis; die
Compose-Oberfläche beobachtet einen expliziten Updatezustand und besitzt
keine Dateisystemlogik.

## Tests und Abnahme

- Unit-Tests für Versionsvergleich, Skip-/Später-Entscheidungen,
  Kompatibilitätsprüfung, Prüfsummenfehler, atomaren Fallback und die
  Aktivierungsentscheidung.
- Instrumentierter Test mit lokaler HTTPS/HTTP-Testquelle für Erfolg,
  Offline-Start, korruptes Paket und Rollback.
- Emulatorablauf: neue Version finden, alle drei Erstentscheidungen,
  Download, beide Aktivierungsentscheidungen, ungespeicherte
  Projekt-/Profiländerungen, Abbrechen und Slice-Blockierung.
- INDX 4T/8T-Profil laden, mehrfarbiges Modell mit mehreren realen
  Extrudern slicen und erzeugten Toolchange-G-Code kontrollieren.

## Erfolgsbedingungen

- Ein Start ohne Netz ist höchstens so schnell wie heute und vollständig
  nutzbar.
- Kein fehlerhaftes oder inkompatibles Paket wird aktiv.
- Kein laufender Slice und keine ungespeicherte Arbeit gehen durch ein
  Profilupdate verloren.
- Der Nutzer steuert Angebot, Installation und Aktivierungszeitpunkt
  explizit.

