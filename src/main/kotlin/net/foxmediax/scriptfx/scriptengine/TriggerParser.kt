package net.foxmediax.scriptfx.scriptengine

/**
 * Находит в скрипте команды-триггеры и регистрирует их в TriggerManager.
 *
 * Поддерживаемый синтаксис:
 *
 * checkpoint x y z dimension radius [once] [on_exit] [cooldown <sec>] [if key op value]...
 * worldstartscript [once] [cooldown <sec>] [if key op value]...
 * trigger_globalplay <event> [item] [once] [cooldown <sec>] [if key op value]...
 */
object TriggerParser {

    val TRIGGER_NAMES = setOf("checkpoint", "worldstartscript", "trigger_globalplay")

    private val OPERATORS = setOf("==", "!=", ">", "<", ">=", "<=", "has", "!has")

    fun parseAll(scriptName: String, commands: List<ScriptCommand>): List<Trigger> {
        val result = mutableListOf<Trigger>()
        commands.forEachIndexed { index, head ->
            if (head.name !in TRIGGER_NAMES) return@forEachIndexed

            // Тело триггера — команды после этой строки до следующего триггера
            val body = commands.drop(index + 1).takeWhile { it.name !in TRIGGER_NAMES }

            val trigger: Trigger? = when (head.name) {
                "checkpoint" -> parseCheckpoint(head, body, scriptName)
                "worldstartscript" -> parseWorldStart(head, body, scriptName)
                "trigger_globalplay" -> parseGlobalPlay(head, body, scriptName)
                else -> null
            }
            if (trigger != null) result.add(trigger)
        }
        return result
    }

    fun registerAll(scriptName: String, commands: List<ScriptCommand>): Int {
        val triggers = parseAll(scriptName, commands)
        triggers.forEach { TriggerManager.register(it) }
        return triggers.size
    }

    // ------------------------------------------------------------------
    // Парсеры
    // ------------------------------------------------------------------

    private fun parseCheckpoint(
        head: ScriptCommand,
        body: List<ScriptCommand>,
        name: String
    ): Trigger.Checkpoint? {
        val x = head.args.getOrNull(0)?.toDoubleOrNull()
        val y = head.args.getOrNull(1)?.toDoubleOrNull()
        val z = head.args.getOrNull(2)?.toDoubleOrNull()
        val dimension = head.args.getOrNull(3) ?: "overworld"
        val radius = head.args.getOrNull(4)?.toDoubleOrNull()

        if (x == null || y == null || z == null || radius == null) {
            ScriptFXLog.warn(
                "checkpoint: некорректные аргументы в скрипте '$name' (строка ${head.lineNumber})"
            )
            return null
        }

        val flags = parseFlags(head.args.drop(5))

        return Trigger.Checkpoint(
            x = x,
            y = y,
            z = z,
            dimension = dimension,
            radius = radius,
            onExit = flags.onExit,
            scriptName = name,
            body = body,
            conditions = flags.conditions,
            once = flags.once,
            cooldownTicks = flags.cooldownTicks
        )
    }

    private fun parseWorldStart(
        head: ScriptCommand,
        body: List<ScriptCommand>,
        name: String
    ): Trigger.WorldStart {
        val flags = parseFlags(head.args)
        return Trigger.WorldStart(
            scriptName = name,
            body = body,
            conditions = flags.conditions,
            once = flags.once,
            cooldownTicks = flags.cooldownTicks
        )
    }

    private fun parseGlobalPlay(
        head: ScriptCommand,
        body: List<ScriptCommand>,
        name: String
    ): Trigger.GlobalPlay? {
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
                ScriptFXLog.warn(
                    "trigger_globalplay: неизвестный тип '$kind' в скрипте '$name' (строка ${head.lineNumber})"
                )
                return null
            }
        }

        // Для player_click второй аргумент — id предмета
        var argIndex = 1
        val itemId = if (event == GlobalPlayEvent.PLAYER_CLICK) {
            val id = head.args.getOrNull(1)
            if (id == null || id in listOf("once", "on_exit", "cooldown", "if")) {
                ScriptFXLog.warn(
                    "trigger_globalplay player_click: не указан id предмета в скрипте '$name' (строка ${head.lineNumber})"
                )
                return null
            }
            argIndex = 2
            id
        } else null

        val flags = parseFlags(head.args.drop(argIndex))

        return Trigger.GlobalPlay(
            event = event,
            itemId = itemId,
            scriptName = name,
            body = body,
            conditions = flags.conditions,
            once = flags.once,
            cooldownTicks = flags.cooldownTicks
        )
    }

    // ------------------------------------------------------------------
    // Разбор флагов: once, on_exit, cooldown <sec>, if key op value
    // ------------------------------------------------------------------

    private data class Flags(
        val once: Boolean = false,
        val onExit: Boolean = false,
        val cooldownTicks: Long = 0L,
        val conditions: List<TriggerCondition> = emptyList()
    )

    private fun parseFlags(args: List<String>): Flags {
        var once = false
        var onExit = false
        var cooldownTicks = 0L
        val conditions = mutableListOf<TriggerCondition>()

        var i = 0
        while (i < args.size) {
            when (val token = args[i].lowercase()) {
                "once" -> {
                    once = true
                    i++
                }
                "on_exit" -> {
                    onExit = true
                    i++
                }
                "cooldown" -> {
                    val sec = args.getOrNull(i + 1)?.toDoubleOrNull()
                    if (sec != null) {
                        cooldownTicks = (sec * 20.0).toLong().coerceAtLeast(1L)
                        i += 2
                    } else {
                        ScriptFXLog.warn("cooldown: ожидалось число секунд после 'cooldown'")
                        i++
                    }
                }
                "if" -> {
                    // if key op value
                    val key = args.getOrNull(i + 1)
                    val op = args.getOrNull(i + 2)
                    val value = args.getOrNull(i + 3)

                    if (key == null || op == null || op !in OPERATORS) {
                        ScriptFXLog.warn("if: некорректное условие (нужно: if key operator value)")
                        i++
                        continue
                    }

                    // Для has / !has значение не обязательно
                    if (op == "has" || op == "!has") {
                        conditions.add(TriggerCondition(key, op, null))
                        i += 3
                    } else {
                        if (value == null) {
                            ScriptFXLog.warn("if: не указано значение для оператора $op")
                            i++
                            continue
                        }
                        conditions.add(TriggerCondition(key, op, value))
                        i += 4
                    }
                }
                else -> {
                    // Неизвестный токен — пропускаем
                    i++
                }
            }
        }

        return Flags(once, onExit, cooldownTicks, conditions)
    }
}