package net.foxmediax.scriptfx.gui

internal data class DocumentationSubsection(val name: String)

internal data class DocumentationSection(
    val name: String,
    val children: List<DocumentationSubsection> = emptyList()
)

internal data class DocumentationSectionHit(val rect: Rect, val section: String)
internal data class DocumentationExpandHit(val rect: Rect, val section: String)
internal data class DocumentationSubsectionHit(val rect: Rect, val section: String, val subsection: String)