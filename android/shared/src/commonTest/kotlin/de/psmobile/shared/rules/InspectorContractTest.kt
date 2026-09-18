package de.psmobile.shared.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorContractTest {

    @Test
    fun `no selection targets the project`() {
        assertEquals(
            InspectorTarget(InspectorScope.PROJECT, null),
            InspectorContract.target(null),
        )
    }

    @Test
    fun `selected stable object id targets that object`() {
        assertEquals(
            InspectorTarget(InspectorScope.OBJECT, 42),
            InspectorContract.target(42),
        )
    }
}
