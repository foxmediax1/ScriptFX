package net.foxmediax.scriptfx.scriptengine

/** Находит в скрипте команды-триггеры (на любой строке) и регистрирует их в TriggerManager. */
object TriggerParser {

    /** Команды-триггеры. weather и timecycles сюда не входят — это обычные команды-действия. */
    val TRIGGER_NAMES = setOf("checkpoint", "worldstartscript", "trigger_globalplay")

    /** Регистрирует все триггеры скрипта. Возвращает, сколько триггеров зарегистрировано. */
    fun registerAll(scriptName: String, commands: List<ScriptCommand>): Int {
        var count = 0
        commands.forEachIndexed { index, head ->
            if (head.name !in TRIGGER_NAMES) return@forEachIndexed

            // Тело триггера — команды после этой строки до следующего триггера (или до конца скрипта).
            val body = commands.drop(index + 1).takeWhile { it.name !in TRIGGER_NAMES }

            val trigger: Trigger? = when (head.name) {
                "checkpoint" -> parseCheckpoint(head, body, scriptName)
                "worldstartscript" -> Trigger.WorldStart(scriptName, body)
                "trigger_globalplay" -> parseGlobalPlay(head, body, scriptName)
                else -> null
            }
            if (trigger != null) {
                TriggerManager.register(trigger)
                count++
            }
        }
        return count
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