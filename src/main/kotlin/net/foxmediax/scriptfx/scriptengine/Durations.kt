package net.foxmediax.scriptfx.scriptengine

/**
 * Разбор длительностей вида "1.sec", "0.5.sec", "20.tick", "500.ms".
 * Без зависимостей от Minecraft, поэтому легко тестируется.
 */
object Durations {

    /** Верхний предел: сутки. Защищает от переполнения Int в вызывающем коде (.toInt()). */
    const val MAX_TICKS = 20L * 60 * 60 * 24

    private val PATTERN = Regex("""^(\d+(?:\.\d+)?)(?:\.([a-z]+))?$""", RegexOption.IGNORE_CASE)

    private val SECONDS = setOf("", "s", "sec", "secs", "second", "seconds")
    private val TICKS = setOf("t", "tick", "ticks")
    private val MILLIS = setOf("ms", "milli", "millis")

    /** Возвращает число тиков или null, если строка не распознана. */
    fun parseTicks(raw: String): Long? {
        val match = PATTERN.matchEntire(raw.trim()) ?: return null
        val amount = match.groupValues[1].toDouble()

        val ticks = when (match.groupValues[2].lowercase()) {
            in TICKS -> amount
            in MILLIS -> maxOf(amount / 50.0, 1.0)
            in SECONDS -> amount * 20.0
            else -> return null   // неизвестная единица ("1.min") - не угадываем
        }
        // Округляем, а не отбрасываем дробную часть: 0.29.sec = 5.8 тика -> 6.
        return Math.round(ticks).coerceIn(0L, MAX_TICKS)
    }
}