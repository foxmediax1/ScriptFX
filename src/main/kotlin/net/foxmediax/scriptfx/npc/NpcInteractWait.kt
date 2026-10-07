package net.foxmediax.scriptfx.npc

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NpcInteractWait {
    private data class Wait(val key: String, @Volatile var done: Boolean = false)
    private val waiting = ConcurrentHashMap<UUID, Wait>()

    fun begin(playerId: UUID, key: String) {
        waiting[playerId] = Wait(key.uppercase())
    }

    fun isDone(playerId: UUID): Boolean = waiting[playerId]?.done == true

    fun clear(playerId: UUID) {
        waiting.remove(playerId)
    }

    fun notifyKey(playerId: UUID, key: String) {
        val w = waiting[playerId] ?: return
        if (w.key.equals(key, ignoreCase = true)) {
            w.done = true
        }
    }
}