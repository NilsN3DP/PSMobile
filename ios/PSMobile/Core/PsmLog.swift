import Foundation
import os

/// Was der Kern und PrusaSlicer selbst zu sagen haben.
///
/// Bis hierher war das ein blinder Fleck: `psm_set_log_callback` gab es
/// in der ABI, Android nutzte es, iOS nicht. Ein Fehlschlag im Kern kam
/// deshalb als ein Satz ohne Begründung an — „3MF-Projekt konnte nicht
/// gespeichert werden" —, obwohl PrusaSlicer den Grund kennt und ihn
/// über sein eigenes Protokoll hinausschreibt.
///
/// Zwei Ziele: `os_log` für die Entwicklung, und ein kleiner Ringpuffer
/// der letzten Warnungen und Fehler, den eine Meldung an den Nutzer
/// anhängen kann. Vierzig Zeilen reichen dafür; wer mehr braucht,
/// schaut in die Konsole.
enum PsmLog {

    private static let sperre = NSLock()
    private static var puffer: [String] = []
    private static let ziel = Logger(subsystem: "de.psmobile", category: "kern")

    /// Einmal vor der ersten Session einhängen.
    static func install() {
        psm_set_log_callback({ stufe, text, _ in
            guard let text else { return }
            PsmLog.aufnehmen(stufe, String(cString: text))
        }, nil)
    }

    /// Die letzten Warnungen und Fehler, älteste zuerst.
    static var recent: [String] {
        sperre.lock(); defer { sperre.unlock() }
        return puffer
    }

    static func clear() {
        sperre.lock(); puffer.removeAll(); sperre.unlock()
    }

    private static func aufnehmen(_ stufe: psm_log_level, _ text: String) {
        switch stufe {
        case PSM_LOG_ERROR: ziel.error("\(text, privacy: .public)")
        case PSM_LOG_WARN:  ziel.warning("\(text, privacy: .public)")
        case PSM_LOG_INFO:  ziel.info("\(text, privacy: .public)")
        default:            ziel.debug("\(text, privacy: .public)")
        }
        guard stufe == PSM_LOG_ERROR || stufe == PSM_LOG_WARN else { return }
        sperre.lock()
        puffer.append(text)
        if puffer.count > 40 { puffer.removeFirst(puffer.count - 40) }
        sperre.unlock()
    }
}
