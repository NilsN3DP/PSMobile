package de.psmobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDisplayNameTest {
    @Test fun `cache path and timestamp do not leak into import confirmation`() {
        assertEquals(
            "PSMobile Test Cube.3mf",
            importDisplayName("/data/user/0/de.psmobile/cache/import/7221171563878-PSMobile Test Cube.3mf (embedded profile)"),
        )
    }

    @Test fun `plain profile name remains readable`() {
        assertEquals("Prusa CORE One 0.4", importDisplayName("Prusa CORE One 0.4"))
    }
}
