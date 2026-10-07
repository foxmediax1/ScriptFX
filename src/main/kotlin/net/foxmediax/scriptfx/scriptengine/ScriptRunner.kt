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

    init {
        ScriptFXLog.info("Скрипт '$scriptName' запущен")
    }

    fun stop(reason: String = "командой stop_script") {
        if (finished) return
        finished = true
        waitUntilPred = null
        ScriptFXLog.info("Скрипт '$scriptName' аварийно остановлен ($reason)")
        skipDepth = 0
    }

    private var skipDepth = 0

    fun tick(currentTick: Long) {
        if (finished || currentTick < waitUntilTick) return

        waitUntilPred?.let { pred ->
            if (pred()) waitUntilPred = null else return
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
                ScriptFXLog.error("...", e)
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
                CommandResult.Stop -> { /* ... */ return }
                is CommandResult.ContinueWith -> { /* ... */ return }
            }
        }
        finished = true
    }
}