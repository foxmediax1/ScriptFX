package net.foxmediax.scriptfx.scriptengine

class ScriptRunner(
    private var commands: List<ScriptCommand>,
    private var scriptName: String,
    private val executor: (ScriptCommand) -> CommandResult,
    private val lookup: (String) -> List<ScriptCommand>?
) {
    private var index = 0
    private var waitUntilTick = 0L
    private var waitUntilPred: (() -> Boolean)? = null

    var finished = false
        private set

    val name: String get() = scriptName

    private var skipDepth = 0

    init {
        ScriptFXLog.info("Скрипт '$scriptName' запущен")
    }

    fun stop(reason: String = "командой stop_script") {
        if (finished) return
        finished = true
        waitUntilPred = null
        skipDepth = 0
        ScriptFXLog.info("Скрипт '$scriptName' аварийно остановлен ($reason)")
    }

    fun tick(currentTick: Long) {
        if (finished || currentTick < waitUntilTick) return

        waitUntilPred?.let { pred ->
            val done = try {
                pred()
            } catch (e: Exception) {
                ScriptFXLog.error("WaitUntil в '$scriptName' бросил исключение — считаем условие выполненным", e)
                true
            }
            if (done) waitUntilPred = null else return
        }

        while (index < commands.size) {
            val command = commands[index]
            index++

            if (skipDepth > 0) {
                when {
                    command.name == "if" -> skipDepth++
                    command.name == "}" -> skipDepth--
                }
                continue
            }

            val result = try {
                executor(command)
            } catch (e: Exception) {
                ScriptFXLog.error("Ошибка в '$scriptName' (строка ${command.lineNumber})", e)
                CommandResult.Continue
            }
            if (finished) return

            when (result) {
                CommandResult.Continue -> continue
                is CommandResult.Wait -> {
                    waitUntilTick = currentTick + result.ticks
                    return
                }
                is CommandResult.WaitUntil -> {
                    waitUntilPred = result.predicate
                    return
                }
                CommandResult.SkipBlock -> {
                    skipDepth = 1
                    continue
                }
                CommandResult.Stop -> {
                    finished = true
                    ScriptFXLog.info("Скрипт '$scriptName' завершён (stopscript)")
                    return
                }
                is CommandResult.ContinueWith -> {
                    val next = lookup(result.scriptName)
                    if (next == null) {
                        ScriptFXLog.warn("continue_startscript: '${result.scriptName}' не найден — '$scriptName' завершён")
                        finished = true
                        return
                    }
                    commands = next
                    scriptName = result.scriptName
                    index = 0
                    skipDepth = 0
                    ScriptFXLog.info("Скрипт продолжен как '$scriptName'")
                    return
                }
            }
        }
        finished = true
        ScriptFXLog.info("Скрипт '$scriptName' завершён")
    }
}