# EasyPrint Responsive Shell – Design-Spezifikation

**Stand:** 2026-07-31  
**Scope:** Ausschließlich Easy Print auf Android. Advanced Mode bleibt in diesem
Durchgang unverändert.

## Ziel

Easy Print soll sich an den gezeigten Prusa-EasyPrint-Abläufen orientieren:
eine durchgehend dunkle, werkzeugartige Oberfläche statt einer weißen Seite
mit einzelnen Karten. Es soll sich auf einem Handy wie eine einfache
Druckvorbereitung und auf einem großen Tablet wie eine kompakte
Slicer-Arbeitsfläche anfühlen.

## Gemeinsame visuelle Basis

- Der gesamte Easy-Root zeichnet `PrusaColors.Background`; kein System-
  oder Compose-Hintergrund darf sichtbar bleiben.
- Systemleisten werden dunkel und verwenden helle Status-Icons.
- Orange kennzeichnet nur primäre Aktionen, aktive Schritte und den
  abschließenden Print-Button. Es ist keine Kartenhintergrundfarbe.
- Alle primären Touch-Ziele haben mindestens 48 dp Höhe.
- Modell, Drucker, Filament, Supports, Haftung und Print Settings bleiben
  dieselben `SlicerService`-Daten; die neue Oberfläche erzeugt keine zweite
  Session und keine parallelen Profile.

## Handy: kompakter, geführter Ablauf

Bei Compact-Breite (unter 600 dp) besteht Easy Print aus:

1. Einer festen, kompakten Kopfzeile mit Projektzugriff, aktivem Drucker
   und einem Rückweg zum Moduswechsel.
2. Einer zentralen Schrittansicht: Modell → Drucker → Filament → Supports
   und Haftung → Print Settings → Vorschau und Print.
3. Vollflächigen Auswahlseiten bzw. Bottom Sheets für Suche, Drucker,
   Material und Print Settings. Auf der Ausgangsfläche stehen keine langen
   Profil-Listen untereinander.
4. Einer fest angedockten unteren Aktionsleiste mit Preview und Print. Der
   Print-Button erklärt fehlende Voraussetzungen statt stumm deaktiviert zu
   bleiben.

## Tablet: EasyPrint-Arbeitsfläche

Ab Medium-Breite (600 dp) wird die Oberfläche zu einer zweispaltigen,
aber weiterhin einfachen Arbeitsfläche:

- Oben liegt eine horizontale EasyPrint-Toolbar nach der Referenz:
  Projects, aktiver Drucker, Material, Supports, Print Settings, Preview
  und Print.
- In der Mitte steht die Druckbett-/Vorschaufläche als Schwerpunkt.
- Auswahlaktionen öffnen rechts ein kontextuelles, maximal 420 dp breites
  Panel; sie überdecken die Arbeitsfläche nicht komplett.
- Bei großer Tablet-Breite (ab 840 dp) bleibt dieses Panel offen, sobald
  ein Auswahlbereich aktiv ist. Bei 600–839 dp wird es als modal Sheet
  geöffnet.

## Interaktion

- Drucker werden als eingerichtete Modelle gewählt. Die Düse wird erst in
  der Slice-Übersicht gewählt; die technische Profilvariante bleibt intern.
- Suchen filtert sofort und enthält eine klare Leerzustandsaktion zum
  Assistenten, falls noch kein Profil vorhanden ist.
- Supports und Haftung sind verständliche Zwei-Wege-Entscheidungen mit
  kurzer Erklärung; keine technische Parameterwand.
- Beim Drehen des Geräts bleiben aktiver Schritt, Suchtext und Auswahl
  erhalten. Die Darstellung wechselt nur zwischen Compact, Medium und
  Expanded.

## Abgrenzung

- Kein Redesign des Advanced Mode in diesem Vorhaben.
- Kein ZIP-Import, keine neue Cloud-/PrusaLink-Abhängigkeit und keine
  Änderungen am nativen Slice-Kern.

## Abnahme

1. Kein weißer Hintergrund in Easy Print oder den Easy-Auswahlseiten.
2. Handy und Tablet haben unterschiedliche, jeweils passende Layouts.
3. Die in den Referenzen sichtbaren Hauptabläufe sind direkt erreichbar:
   Projekte, Drucker, Material, Supports, Print Settings, Preview, Print.
4. Emulator-Smoke-Test prüft Compact-Hochformat, Compact-Querformat,
   Tablet-Hochformat und Tablet-Querformat inklusive Rotation.
5. Android-Unit-Tests, Lint und Debug-Build bestehen.
