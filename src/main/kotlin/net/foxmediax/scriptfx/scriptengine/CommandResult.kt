package net.foxmediax.scriptfx.scriptengine

sealed class CommandResult {
    object Continue : CommandResult()
    data class Wait(val ticks: Long) : CommandResult()
    /** Ждать каждый тик, пока predicate() == true */
    data class WaitUntil(val predicate: () -> Boolean) : CommandResult()
    object Stop : CommandResult()
    data class ContinueWith(val scriptName: String) : CommandResult()
}