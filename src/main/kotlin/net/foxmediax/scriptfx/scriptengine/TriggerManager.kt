package net.foxmediax.scriptfx.scriptengine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.util.UUID

/** Хранит и проверяет все зарегистрированные триггеры. */
object TriggerManager {

    private val checkpoints = mutableListOf<Trigger.Checkpoint>()
    private val worldStarts = mutableListOf<Trigger.WorldStart>()
    private val globalPlayTriggers = mutableListOf<Trigger.GlobalPlay>()

    private val playersInsideCheckpoint = mutableSetOf<Pair<UUID, Trigger.Checkpoint>>()

    // Для ender_dragon_fight / wither_boss_fight — фронт "не было -> появился" по каждому миру.
    private val wasDragonPresent = mutableMapOf<ServerLevel, Boolean>()
    private val wasWitherPresent = mutableMapOf<ServerLevel, Boolean>()

    // Для player_eat — отслеживаем сытость каждого игрока между тиками.
    private val lastFoodLevel = mutableMapOf<UUID, Int>()

    // Огромный AABB для поиска сущностей по всему загруженному миру.
    private val WORLD_WIDE_AABB = AABB(
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
    )

    fun clear() {
        checkpoints.clear(); worldStarts.clear(); globalPlayTriggers.clear()
        playersInsideCheckpoint.clear()
        wasDragonPresent.clear(); wasWitherPresent.clear(); lastFoodLevel.clear()
    }

    fun register(trigger: Trigger) {
        when (trigger) {
            is Trigger.Checkpoint -> checkpoints.add(trigger)
            is Trigger.WorldStart -> worldStarts.add(trigger)
            is Trigger.GlobalPlay -> globalPlayTriggers.add(trigger)
        }
    }

    fun onServerTick(server: MinecraftServer) {
        if (checkpoints.isNotEmpty()) checkCheckpoints(server)
        if (globalPlayTriggers.isNotEmpty()) {
            checkGlobalEntityFights(server)
            checkPlayerEat(server)
        }
    }

    fun onPlayerJoin(server: MinecraftServer, player: ServerPlayer) {
        worldStarts.forEach { fire(it.body, server, player, it.scriptName) }
    }

    fun onPlayerDeath(server: MinecraftServer, player: ServerPlayer) {
        globalPlayTriggers.filter { it.event == GlobalPlayEvent.PLAYER_DEATH }
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    fun onPlayerRespawn(server: MinecraftServer, player: ServerPlayer) {
        globalPlayTriggers.filter { it.event == GlobalPlayEvent.PLAYER_RESPAWN }
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    /** Вызывается из миксина ServerPlayer#teleportTo(...) при любой телепортации игрока. */
    fun onPlayerTeleport(server: MinecraftServer, player: ServerPlayer) {
        globalPlayTriggers.filter { it.event == GlobalPlayEvent.PLAYER_TP }
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    /** Вызывается из UseItemCallback (Fabric API), когда игрок использует предмет. */
    fun onPlayerClick(server: MinecraftServer, player: ServerPlayer, itemId: String) {
        globalPlayTriggers
            .filter { it.event == GlobalPlayEvent.PLAYER_CLICK && matchesItemId(it.itemId, itemId) }
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    private fun matchesItemId(expected: String?, actual: String): Boolean {
        if (expected == null) return false
        val normalized = if (":" in expected) expected else "minecraft:$expected"
        return normalized.equals(actual, ignoreCase = true)
    }

    private fun checkCheckpoints(server: MinecraftServer) {
        for (player in server.playerList.players) {
            for (checkpoint in checkpoints) {
                val level = CommandRegistry.resolveLevel(server, checkpoint.dimension) ?: continue
                val inside = player.level() == level &&
                        player.position().distanceToSqr(checkpoint.x, checkpoint.y, checkpoint.z) <=
                        checkpoint.radius * checkpoint.radius

                val key = player.uuid to checkpoint
                val wasInside = key in playersInsideCheckpoint

                when {
                    inside && !wasInside -> {
                        playersInsideCheckpoint.add(key)
                        fire(checkpoint.body, server, player, checkpoint.scriptName)
                    }
                    !inside && wasInside -> playersInsideCheckpoint.remove(key)
                }
            }
        }
    }

    /** trigger_globalplay ender_dragon_fight / wither_boss_fight — по фронту "появился" в каждом мире. */
    private fun checkGlobalEntityFights(server: MinecraftServer) {
        val dragonTriggers = globalPlayTriggers.filter { it.event == GlobalPlayEvent.ENDER_DRAGON_FIGHT }
        val witherTriggers = globalPlayTriggers.filter { it.event == GlobalPlayEvent.WITHER_BOSS_FIGHT }
        if (dragonTriggers.isEmpty() && witherTriggers.isEmpty()) return

        for (level in server.allLevels) {
            val playersInLevel = server.playerList.players.filter { it.level() == level }

            if (dragonTriggers.isNotEmpty()) {
                val dragonPresent = level.dimension() == Level.END &&
                        level.getEntitiesOfClass(EnderDragon::class.java, WORLD_WIDE_AABB).any { it.isAlive }
                if (dragonPresent && wasDragonPresent[level] != true) {
                    dragonTriggers.forEach { t -> playersInLevel.forEach { p -> fire(t.body, server, p, t.scriptName) } }
                }
                wasDragonPresent[level] = dragonPresent
            }

            if (witherTriggers.isNotEmpty()) {
                val witherPresent = level.getEntitiesOfClass(WitherBoss::class.java, WORLD_WIDE_AABB).any { it.isAlive }
                if (witherPresent && wasWitherPresent[level] != true) {
                    witherTriggers.forEach { t -> playersInLevel.forEach { p -> fire(t.body, server, p, t.scriptName) } }
                }
                wasWitherPresent[level] = witherPresent
            }
        }
    }

    /** trigger_globalplay player_eat — по росту сытости игрока между тиками. */
    private fun checkPlayerEat(server: MinecraftServer) {
        val eatTriggers = globalPlayTriggers.filter { it.event == GlobalPlayEvent.PLAYER_EAT }
        if (eatTriggers.isEmpty()) return

        for (player in server.playerList.players) {
            val currentLevel = player.foodData.foodLevel
            val previousLevel = lastFoodLevel[player.uuid]
            if (previousLevel != null && currentLevel > previousLevel) {
                eatTriggers.forEach { t -> fire(t.body, server, player, t.scriptName) }
            }
            lastFoodLevel[player.uuid] = currentLevel
        }
    }

    private fun fire(body: List<ScriptCommand>, server: MinecraftServer, player: ServerPlayer, scriptName: String) {
        ScriptManager.runAdHoc(body, ScriptContext(server, player), scriptName)
    }
}