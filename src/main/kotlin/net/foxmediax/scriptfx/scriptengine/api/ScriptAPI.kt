package net.foxmediax.scriptfx.scriptengine.api

import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.ScriptRunner
import net.minecraft.network.chat.Component

/**
 * Простой API, который доступен внутри скриптов.
 */
class ScriptAPI(
    private val ctx: ScriptContext,
    private val runner: ScriptRunner? = null
) {

    // ==================== СООБЩЕНИЯ ====================

    fun send(message: String) {
        ctx.player?.sendSystemMessage(Component.literal(message))
    }

    fun sendAll(message: String) {
        ctx.server.playerList.broadcastSystemMessage(Component.literal(message), false)
    }

    // ==================== ГЛОБАЛЬНЫЕ STATES ====================

    fun set(key: String, value: Any) {
        ctx.setState(key, value)
    }

    fun get(key: String): Any? = ctx.getState(key)

    fun getNumber(key: String, default: Double = 0.0): Double =
        ctx.getStateNumber(key, default)

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        ctx.getStateBoolean(key, default)

    fun getString(key: String, default: String = ""): String =
        ctx.getStateString(key, default)

    fun add(key: String, amount: Double) {
        ctx.addState(key, amount)
    }

    fun has(key: String): Boolean = ctx.hasState(key)

    fun remove(key: String) {
        ctx.removeState(key)
    }

    // ==================== STATES ИГРОКА ====================

    fun setPlayer(key: String, value: Any) {
        ctx.setPlayerState(key, value)
    }

    fun getPlayer(key: String): Any? = ctx.getPlayerState(key)

    fun getPlayerNumber(key: String, default: Double = 0.0): Double =
        ctx.getPlayerStateNumber(key, default)

    fun getPlayerBoolean(key: String, default: Boolean = false): Boolean =
        ctx.getPlayerStateBoolean(key, default)

    fun getPlayerString(key: String, default: String = ""): String =
        ctx.getPlayerStateString(key, default)

    fun addPlayer(key: String, amount: Double) {
        ctx.addPlayerState(key, amount)
    }

    fun hasPlayerState(key: String): Boolean = ctx.hasPlayerState(key)

    fun removePlayer(key: String) {
        ctx.removePlayerState(key)
    }

    // ==================== ИНФОРМАЦИЯ ====================

    fun playerName(): String = ctx.player?.name?.string ?: ""

    fun hasPlayer(): Boolean = ctx.hasPlayer()

    // ==================== ЛОКАЛЬНЫЕ ПЕРЕМЕННЫЕ ====================

    fun setVar(key: String, value: Any) {
        ctx.setVar(key, value)
    }

    fun getVar(key: String): Any? = ctx.getVar(key)

    fun getVarNumber(key: String, default: Double = 0.0): Double =
        ctx.getVarNumber(key, default)

    fun getVarString(key: String, default: String = ""): String =
        ctx.getVarString(key, default)

    // ==================== УПРАВЛЕНИЕ ====================

    /**
     * Задержка в секундах.
     * Возвращает специальный результат, который ScriptRunner понимает как Wait.
     */
    fun wait(seconds: Double): WaitSignal {
        val ticks = (seconds * 20.0).toLong().coerceAtLeast(1L)
        return WaitSignal(ticks)
    }

    /**
     * Задержка в тиках.
     */
    fun waitTicks(ticks: Long): WaitSignal {
        return WaitSignal(ticks.coerceAtLeast(1L))
    }

    /** Остановить текущий скрипт */
    fun stop() {
        runner?.stop("вызов stop() из скрипта")
    }
}

/**
 * Сигнал ожидания. Используется, чтобы ScriptRunner мог поставить скрипт на паузу.
 */
class WaitSignal(val ticks: Long)