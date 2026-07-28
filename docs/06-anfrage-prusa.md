# Anfrage an Prusa Research

Stand: 2026-07-28

Der direkte Draht zu Prusa ist da. Diese Datei haelt fest, **was genau**
gefragt werden muss, damit die Antwort hinterher belastbar ist.

> Keine Rechtsberatung. Aber die Punkte unten sind das, woran eine
> Freigabe praktisch scheitert, wenn man sie nicht vorher klaert.

---

## Warum "duerfen wir das?" nicht reicht

Die Frage ist nicht, ob Prusa etwas dagegen hat. Der Konflikt ist
technischer Natur: Apples App-Store-Bedingungen beschraenken die Nutzung
pro Apple-ID, und GPLv3/AGPLv3 § 7 untersagt genau solche zusaetzlichen
Beschraenkungen. Das gilt **unabhaengig davon**, ob der Rechteinhaber
einverstanden ist - solange die Lizenz unveraendert bleibt.

Es braucht also nicht "Erlaubnis", sondern eine **zusaetzliche
Genehmigung nach GPLv3 § 7** (eine sogenannte App-Store-Exception), die
den Lizenztext fuer diesen Fall aufweicht. Das ist ein Einzeiler, aber
er muss ausgesprochen werden.

---

## Die vier Punkte

### 1. App-Store-Exception fuer Prusas eigenen Anteil
Schriftliche Zusatzgenehmigung nach AGPLv3 § 7, die die Verbreitung ueber
App Stores mit nutzungsbeschraenkenden Bedingungen erlaubt. Formulierung
gibt es als Vorlage bei anderen Projekten, die denselben Weg gegangen
sind.

### 2. Wem gehoert der Rest?
**Der wichtigste Punkt, und den koennen nur sie beantworten.**
PrusaSlicer stammt urspruenglich von Slic3r (Alessandro Ranellucci) ab und
hat viele externe Beitragende. Eine Freigabe von Prusa deckt Prusas
Anteil - nicht den der anderen. Und es genuegt **ein einziger**
widersprechender Rechteinhaber, um einen Takedown auszuloesen; genau so
lief der VLC-Fall.

Konkret zu fragen:
- Gibt es eine CLA oder eine Uebertragung der Rechte an Prusa?
- Falls nein: wie schaetzen sie den Fremdanteil in `libslic3r` ein?
- Ist Slic3r-Erbe in den Teilen, die wir tatsaechlich nutzen, noch drin?

Wir nutzen ausschliesslich `libslic3r`, `libnest2d`, `libseqarrange` und
`libvgcode` - **nicht** die GUI-Schicht. Das verkleinert die Frage
erheblich und lohnt sich zu erwaehnen.

### 3. Marke und Name
"Prusa" und "PrusaSlicer" sind Marken. Unabhaengig von der Lizenz:
- Wie sollen wir auf die Herkunft hinweisen, ohne offizielle Herkunft zu
  suggerieren?
- Ist ein Zusatz wie "basiert auf PrusaSlicer (AGPL-3.0), nicht von
  Prusa Research" in ihrem Sinn?
- Gibt es einen Namen, mit dem sie gut leben koennen?

### 4. Wollen sie mitmachen?
Ehrlich gefragt die interessanteste Option. Ein offizieller oder
halboffizieller Segen waere mehr wert als jede Rechtskonstruktion - und
die Vorarbeit ist gemacht: der Kern baut fuer Android, die
Cross-Compile-Patches sind sauber und gehen upstream-tauglich.

---

## Was wir anbieten koennen

Das ist kein reines Bitten. Drei der fuenf Patches verbessern die
Cross-Compile-Faehigkeit von PrusaSlicer ganz allgemein und sind
upstream-tauglich:

- `find_package(CURL)` an `SLIC3R_GUI` koppeln - `libslic3r` enthaelt
  null curl-Referenzen
- dasselbe fuer OpenGL und GLEW
- `IS_CROSS_COMPILE` auch bei `CMAKE_CROSSCOMPILING` setzen; bisher wird
  nur die Apple-Multi-Arch-Konstellation erkannt, weshalb bei jedem
  anderen Cross-Build das fuer die Zielarchitektur uebersetzte
  `encoding-check` auf dem Host ausgefuehrt wird

Details in `patches/README.md`.

---

## Wenn die Antwort "nein" oder "unklar" lautet

Kein Beinbruch, aber es aendert den Auslieferungsweg:
- **Android bleibt unberuehrt** - Play Store und F-Droid sind mit
  GPL-Apps unproblematisch.
- iOS geht ueber TestFlight (Beta, 90 Tage) oder alternative
  EU-Marktplaetze / Web Distribution.

Bewertung der Wege siehe `04-lizenz-und-store.md`.
