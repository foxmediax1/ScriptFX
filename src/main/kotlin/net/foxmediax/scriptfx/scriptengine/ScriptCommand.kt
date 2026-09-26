package net.foxmediax.scriptfx.scriptengine

/** Одна разобранная строка скрипта: имя команды + её аргументы (уже без кавычек). */
data class ScriptCommand(
    val name: String,
    val args: List<String>,
    val lineNumber: Int
)