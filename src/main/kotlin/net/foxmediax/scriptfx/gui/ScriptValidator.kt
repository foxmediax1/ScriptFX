package net.foxmediax.scriptfx.gui

import net.foxmediax.scriptfx.scriptengine.ScriptParser
import net.foxmediax.scriptfx.scriptengine.TriggerParser

internal object ScriptValidator {

    data class Result(val ok: Boolean, val message: String)

    fun pluralizeCommands(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "команд"
            mod10 == 1 -> "команда"
            mod10 in 2..4 -> "команды"
            else -> "команд"
        }
        return "$n $word"
    }

    fun validate(text: String, isKnown: (String) -> Boolean): Result {
        if (text.isBlank()) return Result(true, "Пустой скрипт")

        val commands = ScriptParser.parse(text)
        if (commands.isEmpty()) return Result(true, "Пустой скрипт")

        commands.forEachIndexed { index, command ->
            val isTriggerHeader = index == 0 && command.name in TriggerParser.TRIGGER_NAMES
            if (!isTriggerHeader && !isKnown(command.name)) {
                return Result(false, "⚠ Неизвестная команда '${command.name}' (строка ${command.lineNumber})")
            }
        }
        return Result(true, "✓ Скрипт корректен (${pluralizeCommands(commands.size)})")
    }
}