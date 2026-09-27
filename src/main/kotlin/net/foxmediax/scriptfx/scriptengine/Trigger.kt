package net.foxmediax.scriptfx.scriptengine

/** Тип события для "trigger_globalplay". */
enum class GlobalPlayEvent {
    ENDER_DRAGON_FIGHT, WITHER_BOSS_FIGHT, PLAYER_DEATH, PLAYER_RESPAWN,
    PLAYER_TP, PLAYER_EAT, PLAYER_CLICK
}

/**
 * Триггер — это .sfxs файл, у которого первая строка — служебная команда.
 * scriptName — имя файла (для логов), body — команды ПОСЛЕ первой строки,
 * которые выполняются как обычный скрипт при срабатывании.
 */
sealed class Trigger {
    abstract val scriptName: String
    abstract val body: List<ScriptCommand>

    data class Checkpoint(
        val x: Double, val y: Double, val z: Double,
        val dimension: String, val radius: Double,
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class WorldStart(
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class Weather(
        val kind: String, // "rain" | "thunder"
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class TimeCycle(
        val ticks: Long,   // конкретный tick дня, если label не "day"/"night"
        val label: String, // "day" | "night" | сырое значение (для лога)
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class GlobalPlay(
        val event: GlobalPlayEvent,
        val itemId: String?, // только для PLAYER_CLICK
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()
}