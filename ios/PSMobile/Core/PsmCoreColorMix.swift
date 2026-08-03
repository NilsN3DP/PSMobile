import Foundation

extension PsmCore {
    func colorMixJson() throws -> String {
        var buffer = [CChar](repeating: 0, count: 65_536)
        let result = buffer.withUnsafeMutableBufferPointer {
            psm_colormix_get_json(raw, $0.baseAddress!, $0.count)
        }
        try check(result, "ColorMix lesen")
        return String(cString: buffer)
    }

    func setColorMixJson(_ source: String) throws {
        try check(psm_colormix_set_json(raw, source), "ColorMix speichern")
    }
}
