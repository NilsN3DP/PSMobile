# Eine App, zwei Plattformen — wie Android und iOS zusammenbleiben

Stand: 2026-08-02. Entscheidungsvorlage, noch nicht umgesetzt.

Die Frage ist berechtigt: sobald zwei Oberflächen nebeneinander
entstehen, laufen sie auseinander. Ein Fehler wird auf einer Seite
behoben, eine Einstellung nur dort ergänzt, und nach einem halben Jahr
sind es zwei Programme, die sich ähnlich sehen.

Dieses Dokument sagt, wie groß das Problem tatsächlich ist, was schon
dagegen hilft, und welche drei Wege es gibt.

## Wie viel ist heute schon gemeinsam

Gezählt am 2026-08-02:

| Teil | Zeilen | Geteilt? |
| --- | ---: | --- |
| Kern und Viewport, C/C++ | 7 915 | ja, vollständig |
| Compose-Oberfläche, Kotlin | 11 807 | nein |
| Android-gebunden: Service, JNI-Hülle, Activity | 3 454 | nein |
| Reines Kotlin: Regeln und Zustände | 1 764 | könnte, tut es aber nicht |
| SwiftUI-Gerüst | 544 | — |

Dazu kommt, was gar kein Quelltext ist und deshalb ohnehin nicht
driften kann: die 330 Einstellungen auf 23 Seiten stehen als
`tabs.json`, die Beschriftungen kommen aus PrusaSlicers eigenem
Übersetzungskatalog, die Symbole sind dessen SVGs. Das ist der Kern von
E-12 und der Grund, warum die Einstellungsseiten auf iOS keine Zeile
neu geschrieben werden müssten — nur der Renderer.

**Das eigentliche Driftrisiko sind also die 11 807 Zeilen Compose.**

## Was heute schon dagegen hilft

Der Kern ist die einzige Wahrheit über Geometrie, Profile und Slicing.
Eine Änderung dort — etwa die Platzsuche für neue Objekte oder die
Überlaufregel aufs nächste Bett von heute — wirkt auf beiden Seiten
automatisch, weil beide dieselbe `libpsmobile_core` benutzen. Die C-ABI
ist die einzige Grenze.

Der Viewport ebenso: Bett, Modelle, Gizmos, G-Code-Vorschau sind
gemeinsames C++ mit GLES-2-Shadern aus PrusaSlicer. iOS bekommt
denselben Code.

## Die 1 764 Zeilen, die schon neutral sind

Bemerkenswert: sechzehn Dateien enthalten die Regeln der Oberfläche und
haben **keinen einzigen Android- oder Compose-Import**:

`SimpleModeState` · `EasyModeState` · `EasyModeLayout` ·
`SimpleModeLayout` · `SimpleWorkspaceState` · `SimpleModelSheetState` ·
`FavoriteSettings` · `AdhesionAdvice` · `ImportSelection` ·
`SelectionModel` · `BedLockPolicy` · `NumberCodec` · `UiScale` ·
`ExtruderBankState` · `LayerProfileEditorState` · `SpecialValueCodec`

Darin steckt, wann Anordnen etwas bewirkt, ab wann ein Objekt einen
Rand braucht, was bei einer 3MF in einer Sammelauswahl passiert, wie
Favoriten sortiert werden, wie die Dichte auf kleinen Fenstern
gestaucht wird. Alles Regeln, die auf beiden Plattformen gleich sein
müssen — und alle bereits ohne Gerät testbar.

Sie liegen nur im falschen Ordner.

## Drei Wege

### A — Regeln teilen, Oberflächen getrennt

Ein Kotlin-Multiplatform-Modul `shared` mit `commonMain`. Die sechzehn
Dateien ziehen dorthin um, samt ihrer Tests. Android bindet das Modul
wie bisher; iOS bekommt daraus ein Framework, das Swift direkt benutzen
kann.

*Aufwand:* klein. Es ist im Wesentlichen Dateien verschieben und ein
Gradle-Modul anlegen. Die Tests laufen weiter, nur eben als
`commonTest`.

*Was es löst:* die Regeln können nicht mehr auseinanderlaufen. Wenn auf
Android „Anordnen" ab zwei Objekten freigegeben wird, gilt das auf iOS
auch — nicht weil jemand daran denkt, sondern weil es dieselbe Funktion
ist.

*Was es nicht löst:* jeder Bildschirm muss trotzdem zweimal gebaut
werden. Die 11 807 Zeilen bleiben doppelt.

### B — Compose Multiplatform

Compose läuft seit einiger Zeit auch auf iOS. Die Oberfläche ist
bereits Compose — sie müsste nicht neu geschrieben, sondern umgezogen
werden.

*Aufwand:* groß, aber begrenzt und absehbar. Drei Stellen sind echte
Arbeit:

Der **Viewport** hängt an einer `SurfaceView` und an JNI. Auf iOS
bräuchte es eine `UIKitView` um eine `CAEAGLLayer` oder `MTKView`.
Compose Multiplatform kann fremde Views einbetten, der Weg existiert.

Die **native Bindung** läuft über JNI. Auf iOS wäre es
Kotlin/Native-cinterop direkt gegen `psmobile_core.h`. Dass die C-ABI
schon sauber getrennt ist, ist genau die Voraussetzung dafür — sie war
nie nur eine Aufräumaktion.

Das **Drumherum** — Dateiauswahl über SAF, Foreground-Service,
Keystore, Android-Dokumentanbieter — braucht je Plattform eine eigene
Umsetzung hinter einer gemeinsamen Schnittstelle (`expect`/`actual`).
Das sind die 3 454 android-gebundenen Zeilen.

*Was es löst:* eine Oberfläche. Ein neuer Bildschirm entsteht einmal
und ist auf beiden Geräten da. Drift ist nicht mehr möglich, weil es
nichts mehr gibt, was driften könnte.

*Risiko:* Compose Multiplatform auf iOS ist jünger als auf Android.
Bedienung fühlt sich nicht automatisch wie eine iOS-App an — Zurück-
Geste, Bildlaufverhalten, Systemleisten muss man bewusst nachziehen.

### C — Zwei Oberflächen, dagegen ein Abgleich

Alles bleibt wie es ist, aber `docs/feature-matrix.json` bekommt je
Eintrag einen Stand für Android und einen für iOS, und
`feature-report.py --check` meldet, wo beide auseinandergehen.

*Aufwand:* sehr klein.

*Was es löst:* nichts — es macht den Abstand nur sichtbar. Das ist
mehr wert als nichts, aber es ersetzt keine Lösung.

## Empfehlung

**A jetzt, B als Ziel, C nebenbei.**

A ist so günstig, dass es sich unabhängig von allem anderen lohnt: die
Regeln liegen ohnehin schon plattformneutral da, sie stehen nur im
Android-Modul. Das ist eine Verschiebung, keine Umschreibung — und
danach ist der Teil, der am ehesten unbemerkt auseinanderläuft, gegen
Drift gesichert.

B ist die eigentliche Antwort auf die Frage. Die Voraussetzung dafür
wurde ungeplant schon geschaffen: die Oberfläche ist Compose, die
native Grenze ist eine saubere C-ABI, die Einstellungen sind Daten und
kein Quelltext. Der Zeitpunkt dafür ist aber nicht jetzt, sondern wenn
die Android-Seite steht — sonst zieht man eine Oberfläche um, die sich
noch täglich ändert.

C kostet fast nichts und sollte gemacht werden, sobald auf iOS
überhaupt etwas läuft. Vorher gäbe es nichts zu vergleichen.

## Was das für die nächsten Schritte heißt

Der iPad-Prototyp aus `12-ios-vorbereitung.md` bleibt richtig: er zeigt,
ob der Kern auf iOS-Hardware baut und läuft. Das ist die Voraussetzung
für jeden der drei Wege, denn ohne lauffähigen Kern nützt die schönste
gemeinsame Oberfläche nichts.

Erst danach lohnt die Entscheidung zwischen A und B — und zwar mit dem
Wissen, wie sich Compose Multiplatform auf einem echten iPad anfühlt,
statt nach Aktenlage.
