package net.foxmediax.scriptfx.npc

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntitySpawnReason
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog

object NpcManager {

    fun spawn(
        level: ServerLevel,
        id: String,
        x: Double, y: Double, z: Double,
        anim: String,
        modeRaw: String,
        yaw: Float = 0f,
        pitch: Float = 0f
    ): Boolean {
        if (NpcRuntime.isSpawned(id)) {
            ScriptFXLog.warn("npc_spawn: NPC '$id' уже в мире")
            return false
        }
        val found = NpcRegistry.findById(id)
        if (found == null) {
            ScriptFXLog.warn("npc_spawn: определение '$id' не найдено (.fxnpc)")
            return false
        }
        val (_, def) = found
        val mode = NpcMode.from(modeRaw.ifBlank { def.defaultMode })

        val entity = NpcEntities.SCRIPT_NPC.create(level, EntitySpawnReason.COMMAND) ?: return false
        entity.setMode(mode)
        entity.setPos(x, y, z)
        entity.yRot = yaw
        entity.xRot = pitch
        entity.yHeadRot = yaw
        entity.yBodyRot = yaw
        entity.npcId = id
        entity.modelPath = def.model
        entity.texturePath = def.texture
        entity.animationPath = def.animation
        entity.setAnim(anim.ifBlank { def.defaultAnim })
        entity.setInvulnerableFlag(def.invulnerable)
        entity.setLookAtPlayer(def.lookAtPlayer)
        entity.setNoGravity(!def.gravity)
        entity.isNoAi = true // пока без AI-пути
        if (def.nametag) {
            entity.customName = net.minecraft.network.chat.Component.literal(def.displayName)
            entity.isCustomNameVisible = true
        }
        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            ?.baseValue = def.maxHealth.toDouble()
        entity.health = def.maxHealth

        if (!level.addFreshEntity(entity)) return false

        NpcRuntime.put(
            id,
            NpcRuntimeState(
                definitionId = id,
                entityUuid = entity.uuid,
                mode = mode,
                anim = entity.currentAnim(),
                invulnerable = def.invulnerable,
                lookAtPlayer = def.lookAtPlayer,
                gravity = def.gravity,
                collide = def.collide,
                nametag = def.nametag,
                silent = def.silent,
                displayName = def.displayName,
                maxHealth = def.maxHealth
            )
        )
        ScriptFXLog.info("NPC '$id' заспавнен at $x $y $z mode=${mode.id}")
        return true
    }

    fun despawn(level: ServerLevel, id: String): Boolean {
        val state = NpcRuntime.remove(id) ?: run {
            ScriptFXLog.warn("npc_despawn: '$id' не найден в мире")
            return false
        }
        val entity = level.getEntity(state.entityUuid)
        entity?.discard()
        // на всякий случай поиск по id
        level.getAllEntities().filterIsInstance<ScriptNpcEntity>()
            .filter { it.npcId == id }
            .forEach { it.discard() }
        ScriptFXLog.info("NPC '$id' удалён")
        return true
    }

    fun repack(level: ServerLevel, id: String, args: List<String>): Boolean {
        val state = NpcRuntime.get(id) ?: run {
            ScriptFXLog.warn("npc_repack: '$id' не в мире")
            return false
        }
        val entity = level.getEntity(state.entityUuid) as? ScriptNpcEntity
            ?: level.getAllEntities().filterIsInstance<ScriptNpcEntity>().find { it.npcId == id }
            ?: return false

        var i = 0
        while (i < args.size) {
            val a = args[i]
            when {
                a.equals("Y_damage", true) -> {
                    state.invulnerable = false; entity.setInvulnerableFlag(false)
                }
                a.equals("N_damage", true) -> {
                    state.invulnerable = true; entity.setInvulnerableFlag(true)
                }
                a.equals("Y_look", true) -> {
                    state.lookAtPlayer = true; entity.setLookAtPlayer(true)
                }
                a.equals("N_look", true) -> {
                    state.lookAtPlayer = false; entity.setLookAtPlayer(false)
                }
                a.equals("Y_gravity", true) -> {
                    state.gravity = true; entity.setNoGravity(false)
                }
                a.equals("N_gravity", true) -> {
                    state.gravity = false; entity.setNoGravity(true)
                }
                a.equals("Y_nametag", true) -> {
                    state.nametag = true; entity.isCustomNameVisible = true
                }
                a.equals("N_nametag", true) -> {
                    state.nametag = false; entity.isCustomNameVisible = false
                }
                a.equals("Y_visible", true) -> {
                    state.visible = true; entity.isInvisible = false
                }
                a.equals("N_visible", true) -> {
                    state.visible = false; entity.isInvisible = true
                }
                a.equals("Y_silent", true) -> { state.silent = true }
                a.equals("N_silent", true) -> { state.silent = false }
                a.equals("Y_collide", true) -> {
                    state.collide = true; entity.noPhysics = false
                }
                a.equals("N_collide", true) -> {
                    state.collide = false; entity.noPhysics = true
                }
                a.startsWith("mode", true) -> {
                    val value = if (a.contains(":")) a.substringAfter(":").trim()
                    else args.getOrNull(++i) ?: ""
                    state.mode = NpcMode.from(value)
                }
                a.startsWith("anim", true) -> {
                    val value = if (a.contains("=") || a.contains(":")) {
                        a.substringAfter("=").substringAfter(":").trim().removeSurrounding("\"")
                    } else args.getOrNull(++i)?.removeSurrounding("\"") ?: "idle"
                    state.anim = value
                    entity.setAnim(value)
                }
                a.startsWith("hp", true) -> {
                    val value = if (a.contains(" ")) a.substringAfter(" ").toFloatOrNull()
                    else args.getOrNull(++i)?.toFloatOrNull()
                    if (value != null) {
                        state.maxHealth = value
                        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                            ?.baseValue = value.toDouble()
                        entity.health = value
                    }
                }
                a.startsWith("name", true) -> {
                    val value = if (a.contains("=") || a.contains(":")) {
                        a.substringAfter("=").substringAfter(":").trim().removeSurrounding("\"")
                    } else args.getOrNull(++i)?.removeSurrounding("\"") ?: state.displayName
                    state.displayName = value
                    entity.customName = net.minecraft.network.chat.Component.literal(value)
                }
                a.startsWith("speed", true) -> {
                    val value = args.getOrNull(++i)?.toDoubleOrNull()
                    if (value != null) {
                        entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                            ?.baseValue = value
                    }
                }

                a.startsWith("mode", true) -> {
                    val value = if (a.contains(":")) a.substringAfter(":").trim()
                    else args.getOrNull(++i) ?: ""
                    val m = NpcMode.from(value)
                    state.mode = m
                    entity.setMode(m)
                }
            }
            i++
        }
        return true
    }
}