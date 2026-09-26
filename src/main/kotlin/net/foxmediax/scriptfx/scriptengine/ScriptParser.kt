package net.foxmediax.scriptfx.scriptengine

/**
 * Превращает текст .sfxs файла в список команд.
 * Формат строки: имя_команды арг1 арг2 "аргумент с пробелами".
 * "#" и "//" в начале строки — комментарий, пустые строки пропускаются.
 */
object ScriptParser {

    fun parse(text: String): List<ScriptCommand> {
        val commands = mutableListOf<ScriptCommand>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed

            val tokens = tokenize(line)
            if (tokens.isEmpty()) return@forEachIndexed

            commands.add(ScriptCommand(tokens.first(), tokens.drop(1), index + 1))
        }
        return commands
    }

    private fun tokenize(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ' ' && !inQuotes -> {
                    if (current.isNotEmpty()) { tokens.add(current.toString()); current.clear() }
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}