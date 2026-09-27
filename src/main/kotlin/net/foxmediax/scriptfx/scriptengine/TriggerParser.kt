package net.foxmediax.scriptfx.scriptengine

/** Разбирает первую строку скрипта и решает, триггер это или обычный скрипт. */
object TriggerParser {

    val TRIGGER_NAMES = setOf(
        "checkpoint", "worldstartscript", "weather", "timecycles", "trigger_globalplay"
    )

    /** Возвращает Trigger и регистрирует его в TriggerManager, либо null если это не триггер. */
    fun tryRegister(scriptName: String, commands: List<ScriptCommand>): Trigger? {
        val head = commands.firstOrNull() ?: return null
        if (head.name !in TRIGGER_NAMES) return null
        val body = commands.drop(1)

        val trigger = when (head.name) {
            "checkpoint" -> parseCheckpoint(head, body, scriptName)
            "worldstartscript" -> Trigger.WorldStart(scriptName, body)
            "weather" -> parseWeather(head, body, scriptName)
            "timecycles" -> parseTimeCycle(head, body, scriptName)
            "trigger_globalplay" -> parseGlobalPlay(head, body, scriptName)
            else -> null
        } ?: return null

        TriggerManager.register(trigger)
        return trigger
    }

    private fun parseCheckpoint(head: ScriptCommand, body: List<ScriptCommand>, name: String): Trigger.Checkpoint? {
        val x = head.args.getOrNull(0)?.toDoubleOrNull()
        val y = head.args.getOrNull(1)?.toDoubleOrNull()
        val z = head.args.getOrNull(2)?.toDoubleOrNull()
        val dimension = head.args.getOrNull(3) ?: "overworld"
        val radius = head.args.getOrNull(4)?.toDoubleOrNull()
        if (x == null || y == null || z == null || radius == null) {
            ScriptFXLog.warn("checkpoint: некорректные аргументы в скрипте '$name' (строка ${head.lineNumber})")
            return null
        }
        return Trigger.Checkpoint(x, y, z, dimension, radius, name, body)
    }

    private fun parseWeather(head: ScriptCommand, body: List<ScriptCommand>, name: String): Trigger.Weather? {
        val kind = head.args.getOrNull(0)?.lowercase()
        if (kind != "rain" && kind != "thunder") {
            ScriptFXLog.warn("weather: ожидался 'rain' или 'thunder' в скрипте '$name' (строка ${head.lineNumber})")
            return null
        }
        return Trigger.Weather(kind, name, body)
    }

    private fun parseTimeCycle(head: ScriptCommand, body: List<ScriptCommand>, name: String): Trigger.TimeCycle? {
        val raw = head.args.getOrNull(0) ?: run {
            ScriptFXLog.warn("timecycles: не указано время в скрипте '$name' (строка ${head.lineNumber})")
            return null
        }
        val ticks = when (raw.lowercase()) {
            "day", "night" -> -1L
            else -> raw.toLongOrNull() ?: run {
                ScriptFXLog.warn("timecycles: некорректное значение '$raw' в скрипте '$name'")
                return null
            }
        }
        return Trigger.TimeCycle(ticks, raw.lowercase(), name, body)
    }

    private fun parseGlobalPlay(head: ScriptCommand, body: List<ScriptCommand>, name: String): Trigger.GlobalPlay? {
        val kind = head.args.getOrNull(0)?.lowercase()
        val event = when (kind) {
            "player_death" -> GlobalPlayEvent.PLAYER_DEATH
            "player_respawn" -> GlobalPlayEvent.PLAYER_RESPAWN
            "player_tp" -> GlobalPlayEvent.PLAYER_TP
            "player_eat" -> GlobalPlayEvent.PLAYER_EAT
            "player_click" -> GlobalPlayEvent.PLAYER_CLICK
            "ender_dragon_fight" -> GlobalPlayEvent.ENDER_DRAGON_FIGHT
            "wither_boss_fight" -> GlobalPlayEvent.WITHER_BOSS_FIGHT
            else -> {
                ScriptFXLog.warn("trigger_globalplay: неизвестный тип '$kind' в скрипте '$name' (строка ${head.lineNumber})")
                return null
            }
        }

        val itemId = head.args.getOrNull(1)
        if (event == GlobalPlayEvent.PLAYER_CLICK && itemId == null) {
            ScriptFXLog.warn("trigger_globalplay player_click: не указан id предмета в скрипте '$name' (строка ${head.lineNumber})")
            return null
        }

        return Trigger.GlobalPlay(event, itemId, name, body)
    }
}