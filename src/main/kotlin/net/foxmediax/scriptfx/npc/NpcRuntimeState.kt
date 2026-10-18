package net.foxmediax.scriptfx.npc

import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class NpcRuntimeState(
    val definitionId: String,
    val entityUuid: UUID,
    var mode: NpcMode,
    var anim: String,
    var invulnerable: Boolean,
    var lookAtPlayer: Boolean,
    var gravity: Boolean,
    var collide: Boolean,
    var nametag: Boolean,
    var silent: Boolean,
    var visible: Boolean = true,
    var displayName: String,
    var maxHealth: Float,
    val variables: MutableMap<String, String> = mutableMapOf()
)

object NpcRuntime {
    /** id → state; один id = один инстанс */
    private val active = ConcurrentHashMap<String, NpcRuntimeState>()

    fun get(id: String): NpcRuntimeState? = active[id]
    fun isSpawned(id: String): Boolean = active.containsKey(id)
    fun put(id: String, state: NpcRuntimeState) { active[id] = state }
    fun remove(id: String): NpcRuntimeState? = active.remove(id)
    fun all(): Map<String, NpcRuntimeState> = active.toMap()

    fun setVar(id: String, key: String, value: String) {
        active[id]?.variables?.set(key, value)
    }

    fun getVar(id: String, key: String): String? =
        active[id]?.variables?.get(key)
}