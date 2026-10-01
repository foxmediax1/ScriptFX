package net.foxmediax.scriptfx.scriptengine

/** Выполняет один запущенный экземпляр скрипта, тик за тиком. */
class ScriptRunner(
    private var commands: List<ScriptCommand>,
    private val context: ScriptContext,
    private var scriptName: String = "ad-hoc"
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

    /** Аварийная остановка (/scriptfx stop_script). */
    fun stop() {
        if (finished) return
        finished = true
        ScriptFXLog.info("Скрипт '$scriptName' аварийно остановлен командой stop_script")
    }

    fun tick(currentTick: Long) {
        if (finished || currentTick < waitUntilTick) return

        while (index < commands.size) {
            val command = commands[index]
            index++

            val result = try {
                CommandRegistry.execute(command, context)
            } catch (e: Exception) {
                ScriptFXLog.error("Ошибка в скрипте '$scriptName', команда '${command.name}' (строка ${command.lineNumber})", e)
                CommandResult.Continue
            }

            // Скрипт могли остановить прямо во время выполнения команды.
            if (finished) return

            when (result) {
                CommandResult.Continue -> continue
                is CommandResult.Wait -> { waitUntilTick = currentTick + result.ticks; return }
                CommandResult.Stop -> {
                    finished = true
                    ScriptFXLog.info("Скрипт '$scriptName' остановлен (stopscript)")
                    return
                }
                is CommandResult.ContinueWith -> {
                    val next = ScriptManager.loadedScript(result.scriptName)
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