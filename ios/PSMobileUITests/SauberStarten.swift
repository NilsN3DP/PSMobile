import XCTest

/// Die App starten, ohne sie als abgestuerzt zu hinterlassen.
///
/// `XCUIApplication.terminate()` schiesst die App aus dem Vordergrund ab.
/// Fuer die Absturzheuristik sieht das aus wie ein Absturz: beim
/// naechsten Start steht die Frage "The app didn't close normally last
/// time" mitten im Bild, und solange sie steht, ist kein Bedienelement
/// darunter treffbar. Die Tests meldeten daraufhin Dinge wie "Projekte
/// ist nicht treffbar" - was nichts ueber das Layout sagte, sondern
/// ueber den vorigen Testfall.
///
/// Einmal kurz nach Hause, dann beenden: damit laeuft `scenePhase ==
/// .background` durch, die Sitzung gilt als sauber beendet, und der
/// naechste Start kommt ohne Rueckfrage.
///
/// Das Gegenstueck auf Android steht in `tools/rundgang-android.sh`
/// (KEYCODE_HOME vor dem force-stop) und in `PsmUiTest.starte`.
extension XCUIApplication {

    /// Ein Bett-Knopf (`bed.add`, `bed.card.N`, `bed.remove.N`, `bed.lock.N`).
    ///
    /// Auf dem Telefon liegen die Betten nicht in einer Leiste, sondern in
    /// einem Blatt hinter der Bettkarte `bed.selector.active` - der Knopf
    /// gibt es erst, wenn das Blatt offen ist. Auf dem iPad aendert das
    /// nichts (keine Bettkarte). Gegenstueck: `oeffneKompakteBettauswahl`
    /// in `PsmUiTest.kt` (iPhone 16, 15.09.2026: vier Bett-Tests rot).
    func bett(_ kennung: String) -> XCUIElement {
        let knopf = buttons[kennung]
        if !knopf.exists, buttons["bed.selector.active"].exists {
            buttons["bed.selector.active"].tap()
            _ = knopf.waitForExistence(timeout: 3)
        }
        return knopf
    }

    /// Das Bettblatt wieder zu, wenn es offen ist - es ist modal, und was
    /// ausserhalb liegt (Schiene, Seite), ist erst danach wieder da.
    func bettBlattZu() {
        let zu = buttons["bed.selector.close"]
        if zu.exists {
            zu.tap()
            _ = otherElements["bed.selector.sheet"].waitForNonExistence(timeout: 3)
        }
    }

    func sauberStarten(_ argumente: [String]) {
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 1.5)
        terminate()
        launchArguments = argumente
        launch()
        // Kurz setzen lassen. Ein Tipp in der ersten Sekunde nach dem
        // Start verpuffte in den Laeufen vom 12.09.2026 immer wieder
        // (OBJECTS blieb zu, das Projekte-Panel ging nicht auf) - der
        // Kern richtet in dieser Sekunde Profile ein, und die Ansicht
        // baut sich dabei noch um. Ein Mensch tippt auch nicht in die
        // Startanimation hinein.
        Thread.sleep(forTimeInterval: 1.0)
        // Die Absturz-Rueckfrage, die hier vom 12.09.2026 frueh bis
        // mittags weggetippt wurde, gibt es nicht mehr - Abstuerze melden
        // TestFlight und der Store. Der Umweg ueber Home bleibt trotzdem:
        // so laeuft scenePhase == .background durch und der Arbeitsstand
        // wird gesichert, wie bei einem Menschen.
    }
}
