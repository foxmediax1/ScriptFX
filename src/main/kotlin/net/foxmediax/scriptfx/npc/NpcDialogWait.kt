package net.foxmediax.scriptfx.npc

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NpcDialogWait {
    private data class Wait(@Volatile var button: Int = 0)
    private val waiting = ConcurrentHashMap<UUID, Wait>()

    fun begin(playerId: UUID) {
        waiting[playerId] = Wait(0)
    }

    fun isDone(playerId: UUID): Boolean = (waiting[playerId]?.button ?: 0) > 0

    fun chosen(playerId: UUID): Int = waiting[playerId]?.button ?: 0

    fun clear(playerId: UUID) {
        waiting.remove(playerId)
    }

    fun notifyChoice(playerId: UUID, button: Int) {
        val w = waiting[playerId] ?: return
        if (button in 1..5) w.button = button
    }
}