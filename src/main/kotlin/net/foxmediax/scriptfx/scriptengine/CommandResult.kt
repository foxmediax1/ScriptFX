package net.foxmediax.scriptfx.scriptengine

/** Что делать ScriptRunner'у после выполнения одной команды. */
sealed class CommandResult {
    object Continue : CommandResult()
    data class Wait(val ticks: Long) : CommandResult()
    data class WaitUntil(val predicate: () -> Boolean) : CommandResult()
    /** Пропустить тело if { ... } до парной `}`. */
    object SkipBlock : CommandResult()
    object Stop : CommandResult()
    data class ContinueWith(val scriptName: String) : CommandResult()
}