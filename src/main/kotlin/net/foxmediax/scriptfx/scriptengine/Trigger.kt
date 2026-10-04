package net.foxmediax.scriptfx.scriptengine

/** Тип события для "trigger_globalplay". */
enum class GlobalPlayEvent {
    ENDER_DRAGON_FIGHT, WITHER_BOSS_FIGHT, PLAYER_DEATH, PLAYER_RESPAWN,
    PLAYER_TP, PLAYER_EAT, PLAYER_CLICK
}

/**
 * Триггер создаётся командой-триггером (checkpoint / worldstartscript / trigger_globalplay),
 * которая может стоять на любой строке скрипта.
 * scriptName — имя файла (для логов), body — команды ПОСЛЕ строки с триггером
 * до следующего триггера (или до конца скрипта); они выполняются как обычный скрипт при срабатывании.
 */
sealed class Trigger {
    abstract val scriptName: String
    abstract val body: List<ScriptCommand>

    class Checkpoint(
        val x: Double, val y: Double, val z: Double,
        val dimension: String, val radius: Double,
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class WorldStart(
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()

    data class GlobalPlay(
        val event: GlobalPlayEvent,
        val itemId: String?, // только для PLAYER_CLICK
        override val scriptName: String, override val body: List<ScriptCommand>
    ) : Trigger()
}