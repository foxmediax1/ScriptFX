package net.foxmediax.scriptfx.scriptengine

/**
 * Выполняет один запущенный экземпляр скрипта, тик за тиком.
 *
 * Раннер не знает про Minecraft: команды выполняет переданный executor,
 * а скрипт для continue_startscript ищет lookup. Поэтому его можно тестировать без игры.
 */
class ScriptRunner(
    private var commands: List<ScriptCommand>,
    private var scriptName: String,
    private val executor: (ScriptCommand) -> CommandResult,
    private val lookup: (String) -> List<ScriptCommand>?
) {
    private var index = 0
    private var waitUntilTick = 0L
    var finished = false
        private set

    /** Имя скрипта, который выполняется сейчас (меняется при continue_startscript). */
    val name: String get() = scriptName

    init {
        ScriptFXLog.info("Скрипт '$scriptName' запущен")
    }

    /** Аварийная остановка. reason попадает в лог. */
    fun stop(reason: String = "командой stop_script") {
        if (finished) return
        finished = true
        ScriptFXLog.info("Скрипт '$scriptName' аварийно остановлен ($reason)")
    }

    fun tick(currentTick: Long) {
        if (finished || currentTick < waitUntilTick) return

        while (index < commands.size) {
            val command = commands[index]
            index++

            val result = try {
                executor(command)
            } catch (e: Exception) {
                ScriptFXLog.error("Ошибка в скрипте '$scriptName', команда '${command.name}' (строка ${command.lineNumber})", e)
                CommandResult.Continue
            }

            // Скрипт могли остановить прямо во время выполнения команды.
            if (finished) return

            when (result) {
                CommandResult.Continue -> continue
                is CommandResult.Wait -> { waitUntilTick = currentTick + result.ticks; return }

                is CommandResult.WaitUntil -> {
                    if (!result.predicate()) {
                        // та же команда на следующем тике
                        index--
                        waitUntilTick = currentTick + 1
                        return
                    }
                    continue
                }
                CommandResult.Stop -> {
                    finished = true
                    ScriptFXLog.info("Скрипт '$scriptName' остановлен (stopscript)")
                    return
                }
                is CommandResult.ContinueWith -> {
                    val next = lookup(result.scriptName)
                    if (next == null) {
                        ScriptFXLog.warn("continue_startscript: '${result.scriptName}' не найден")
                        finished = true
                    } else {
                        ScriptFXLog.info("Скрипт '$scriptName' продолжается как '${result.scriptName}'")
                        commands = next
                        index = 0
                        scriptName = result.scriptName
                    }
                    return
                }
            }
        }
        finished = true
        ScriptFXLog.info("Скрипт '$scriptName' завершён")
    }
}