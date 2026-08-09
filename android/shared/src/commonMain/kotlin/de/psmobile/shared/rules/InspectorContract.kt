package de.psmobile.shared.rules

enum class InspectorScope {
    PROJECT,
    OBJECT,
}

data class InspectorTarget(
    val scope: InspectorScope,
    val objectId: Int?,
)

object InspectorContract {

    fun target(selectedObjectId: Int?): InspectorTarget = selectedObjectId?.let {
        InspectorTarget(InspectorScope.OBJECT, it)
    } ?: InspectorTarget(InspectorScope.PROJECT, null)
}
