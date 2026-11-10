package net.foxmediax.scriptfx.scriptengine

data class ScriptCommand(
    val name: String,
    val args: List<String>,
    val lineNumber: Int
)