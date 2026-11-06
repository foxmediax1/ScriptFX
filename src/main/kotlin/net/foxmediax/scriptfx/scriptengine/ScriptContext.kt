package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.core.states.StateStorage
import net.foxmediax.scriptfx.core.states.StatesManager
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * Контекст выполнения скрипта.
 * Содержит информацию о сервере, игроке, сущности-источнике
 * и даёт удобный доступ к States.
 */
class ScriptContext(
    val server: MinecraftServer,
    val player: ServerPlayer? = null,
    val subject: Entity? = null,          // кто вызвал / на ком сработало
    val `object`: Entity? = null,         // дополнительная сущность (если есть)
    val triggerType: String = "unknown",  // откуда запущен скрипт
    val scriptName: String = "unknown"
) {
    /** Локальные переменные текущего запуска скрипта */
    val vars: MutableMap<String, Any> = mutableMapOf()

    // ==================== STATES ====================

    /** Глобальные состояния сервера */
    val globalStates: StateStorage
        get() = StatesManager.global()

    /** Состояния текущего игрока (если есть) */
    val playerStates: StateStorage?
        get() = player?.let { StatesManager.player(it) }

    // --- Глобальные ---

    fun getState(key: String): Any? = globalStates.get(key)

    fun getStateNumber(key: String, default: Double = 0.0): Double =
        globalStates.getNumber(key, default)

    fun getStateBoolean(key: String, default: Boolean = false): Boolean =
        globalStates.getBoolean(key, default)

    fun getStateString(key: String, default: String = ""): String =
        globalStates.getString(key, default)

    fun setState(key: String, value: Any) {
        globalStates.set(key, value)
    }

    fun addState(key: String, amount: Double) {
        globalStates.add(key, amount)
    }

    fun removeState(key: String) {
        globalStates.remove(key)
    }

    fun hasState(key: String): Boolean = globalStates.has(key)

    // --- Персональные (игрока) ---

    fun getPlayerState(key: String): Any? = playerStates?.get(key)

    fun getPlayerStateNumber(key: String, default: Double = 0.0): Double =
        playerStates?.getNumber(key, default) ?: default

    fun getPlayerStateBoolean(key: String, default: Boolean = false): Boolean =
        playerStates?.getBoolean(key, default) ?: default

    fun getPlayerStateString(key: String, default: String = ""): String =
        playerStates?.getString(key, default) ?: default

    fun setPlayerState(key: String, value: Any) {
        playerStates?.set(key, value)
    }

    fun addPlayerState(key: String, amount: Double) {
        playerStates?.add(key, amount)
    }

    fun removePlayerState(key: String) {
        playerStates?.remove(key)
    }

    fun hasPlayerState(key: String): Boolean = playerStates?.has(key) == true

    // ==================== УДОБНЫЕ МЕТОДЫ ====================

    fun hasPlayer(): Boolean = player != null

    fun send(message: String) {
        player?.sendSystemMessage(Component.literal(message))
    }

    fun sendToAll(message: String) {
        server.playerList.broadcastSystemMessage(Component.literal(message), false)
    }

    fun getPlayerName(): String? = player?.name?.string

    fun getPlayerUUID(): UUID? = player?.uuid

    // ==================== ЛОКАЛЬНЫЕ ПЕРЕМЕННЫЕ ====================

    fun setVar(key: String, value: Any) {
        vars[key] = value
    }

    fun getVar(key: String): Any? = vars[key]

    fun getVarString(key: String, default: String = ""): String =
        vars[key]?.toString() ?: default

    fun getVarNumber(key: String, default: Double = 0.0): Double {
        return when (val v = vars[key]) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            is Boolean -> if (v) 1.0 else 0.0
            else -> default
        }
    }

    fun hasVar(key: String): Boolean = vars.containsKey(key)

    // ==================== КОПИРОВАНИЕ ====================

    /**
     * Создаёт новый контекст на основе текущего,
     * но с другим игроком / скриптом / триггером.
     */
    fun copy(
        newPlayer: ServerPlayer? = this.player,
        newSubject: Entity? = this.subject,
        newObject: Entity? = this.`object`,
        newTriggerType: String = this.triggerType,
        newScriptName: String = this.scriptName
    ): ScriptContext {
        return ScriptContext(
            server = server,
            player = newPlayer,
            subject = newSubject,
            `object` = newObject,
            triggerType = newTriggerType,
            scriptName = newScriptName
        ).also {
            it.vars.putAll(this.vars)
        }
    }
}