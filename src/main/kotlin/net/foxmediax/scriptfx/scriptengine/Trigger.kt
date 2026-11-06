package net.foxmediax.scriptfx.scriptengine

/** Тип события для "trigger_globalplay". */
enum class GlobalPlayEvent {
    ENDER_DRAGON_FIGHT,
    WITHER_BOSS_FIGHT,
    PLAYER_DEATH,
    PLAYER_RESPAWN,
    PLAYER_TP,
    PLAYER_EAT,
    PLAYER_CLICK
}

/**
 * Условие, которое должно выполниться, чтобы триггер сработал.
 */
data class TriggerCondition(
    val key: String,
    val operator: String,      // ==, !=, >, <, >=, <=, has, !has
    val value: String? = null
)

/**
 * Триггер создаётся командой-триггером.
 * body — команды, которые выполнятся при срабатывании.
 */
sealed class Trigger {
    abstract val scriptName: String
    abstract val body: List<ScriptCommand>
    abstract val conditions: List<TriggerCondition>
    abstract val once: Boolean
    abstract val cooldownTicks: Long

    class Checkpoint(
        val x: Double,
        val y: Double,
        val z: Double,
        val dimension: String,
        val radius: Double,
        val onExit: Boolean = false,
        override val scriptName: String,
        override val body: List<ScriptCommand>,
        override val conditions: List<TriggerCondition> = emptyList(),
        override val once: Boolean = false,
        override val cooldownTicks: Long = 0L
    ) : Trigger()

    data class WorldStart(
        override val scriptName: String,
        override val body: List<ScriptCommand>,
        override val conditions: List<TriggerCondition> = emptyList(),
        override val once: Boolean = false,
        override val cooldownTicks: Long = 0L
    ) : Trigger()

    data class GlobalPlay(
        val event: GlobalPlayEvent,
        val itemId: String?,
        override val scriptName: String,
        override val body: List<ScriptCommand>,
        override val conditions: List<TriggerCondition> = emptyList(),
        override val once: Boolean = false,
        override val cooldownTicks: Long = 0L
    ) : Trigger()
}