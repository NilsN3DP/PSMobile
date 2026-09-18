package de.psmobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterCompatibilityPolicyTest {
    private data class Link(val name: String, val profile: String)

    private val links = listOf(
        Link("MK4", "PrusaResearch:MK4:0.4"),
        Link("XL", "PrusaResearch:XL:0.4"),
    )

    @Test fun onlyMatchingProfileIsReturned() {
        assertEquals(
            listOf("MK4"),
            PrinterCompatibilityPolicy.matching(links, "PrusaResearch:MK4:0.4") { it.profile }
                .map { it.name },
        )
    }

    @Test fun noMatchDoesNotFallBackToAllPrinters() {
        assertEquals(
            emptyList<Link>(),
            PrinterCompatibilityPolicy.matching(links, "PrusaResearch:COREONE:0.4") { it.profile },
        )
    }

    @Test fun emptyProfileReturnsNoTargets() {
        assertEquals(
            emptyList<Link>(),
            PrinterCompatibilityPolicy.matching(links, "") { it.profile },
        )
    }
}
