package net.foxmediax.scriptfx.scriptengine

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import java.util.EnumMap
import java.util.UUID

/** Хранит и проверяет все зарегистрированные триггеры. */
object TriggerManager {

    /** Как часто (в тиках) искать дракона и визера. 20 = раз в секунду. */
    private const val FIGHT_CHECK_INTERVAL = 20

    private val checkpoints = mutableListOf<Trigger.Checkpoint>()
    private val worldStarts = mutableListOf<Trigger.WorldStart>()
    private val globalPlay =
        EnumMap<GlobalPlayEvent, MutableList<Trigger.GlobalPlay>>(GlobalPlayEvent::class.java)

    /** Какие чекпоинты сейчас "содержат" игрока. */
    private val insideCheckpoint = HashMap<UUID, MutableSet<Trigger.Checkpoint>>()

    // Для ender_dragon_fight / wither_boss_fight: фронт "не было -> появился" по каждому миру.
    private val wasDragonPresent = HashMap<ServerLevel, Boolean>()
    private val wasWitherPresent = HashMap<ServerLevel, Boolean>()

    // Для player_eat: сытость игрока на прошлой проверке.
    private val lastFoodLevel = HashMap<UUID, Int>()

    private var tickCounter = 0L

    fun clear() {
        checkpoints.clear()
        worldStarts.clear()
        globalPlay.clear()
        insideCheckpoint.clear()
        wasDragonPresent.clear()
        wasWitherPresent.clear()
        lastFoodLevel.clear()
    }

    fun register(trigger: Trigger) {
        when (trigger) {
            is Trigger.Checkpoint -> checkpoints.add(trigger)
            is Trigger.WorldStart -> worldStarts.add(trigger)
            is Trigger.GlobalPlay ->
                globalPlay.getOrPut(trigger.event) { mutableListOf() }.add(trigger)
        }
    }

    private fun triggersFor(event: GlobalPlayEvent): List<Trigger.GlobalPlay> =
        globalPlay[event] ?: emptyList()

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    fun onServerTick(server: MinecraftServer) {
        tickCounter++
        if (checkpoints.isNotEmpty()) checkCheckpoints(server)
        if (globalPlay.isNotEmpty()) {
            checkGlobalEntityFights(server)
            checkPlayerEat(server)
        }
    }

    fun onPlayerJoin(server: MinecraftServer, player: ServerPlayer) {
        lastFoodLevel.remove(player.uuid)
        worldStarts.forEach { fire(it.body, server, player, it.scriptName) }
    }

    /** Вызывать из ServerPlayConnectionEvents.DISCONNECT. */
    fun onPlayerLeave(uuid: UUID) {
        lastFoodLevel.remove(uuid)
        insideCheckpoint.remove(uuid)
    }

    fun onPlayerDeath(server: MinecraftServer, player: ServerPlayer) {
        triggersFor(GlobalPlayEvent.PLAYER_DEATH)
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    fun onPlayerRespawn(server: MinecraftServer, player: ServerPlayer) {
        // После респауна сытость прыгает вверх - это не еда.
        lastFoodLevel.remove(player.uuid)
        triggersFor(GlobalPlayEvent.PLAYER_RESPAWN)
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    /** Вызывается из миксина ServerPlayer#teleportTo(...) при телепортации игрока. */
    fun onPlayerTeleport(server: MinecraftServer, player: ServerPlayer) {
        // Служебные телепортации катсцены (движение камеры, возврат) не считаем.
        if (CutsceneManager.isInCutscene(player)) return
        triggersFor(GlobalPlayEvent.PLAYER_TP)
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    /** Вызывается из UseItemCallback (Fabric API), когда игрок использует предмет. */
    fun onPlayerClick(server: MinecraftServer, player: ServerPlayer, itemId: String) {
        triggersFor(GlobalPlayEvent.PLAYER_CLICK)
            .filter { matchesItemId(it.itemId, itemId) }
            .forEach { fire(it.body, server, player, it.scriptName) }
    }

    private fun matchesItemId(expected: String?, actual: String): Boolean {
        if (expected == null) return false
        val normalized = if (":" in expected) expected else "minecraft:$expected"
        return normalized.equals(actual, ignoreCase = true)
    }

    // ------------------------------------------------------------------
    // Проверки по тикам
    // ------------------------------------------------------------------

    private fun checkCheckpoints(server: MinecraftServer) {
        val players = server.playerList.players
        if (players.isEmpty()) return

        for (checkpoint in checkpoints) {
            val level = CommandRegistry.resolveLevel(server, checkpoint.dimension) ?: continue
            val radiusSq = checkpoint.radius * checkpoint.radius

            for (player in players) {
                // Во время катсцены состояние не трогаем: игрок "физически" в точке камеры.
                if (CutsceneManager.isInCutscene(player)) continue

                val inside = player.level() == level &&
                        player.position().distanceToSqr(checkpoint.x, checkpoint.y, checkpoint.z) <= radiusSq

                val set = insideCheckpoint.getOrPut(player.uuid) { HashSet() }
                if (inside) {
                    if (set.add(checkpoint)) {
                        fire(checkpoint.body, server, player, checkpoint.scriptName)
                    }
                } else {
                    set.remove(checkpoint)
                }
            }
        }
    }

    /** ender_dragon_fight / wither_boss_fight: по фронту "появился" в каждом мире. */
    private fun checkGlobalEntityFights(server: MinecraftServer) {
        val dragonTriggers = triggersFor(GlobalPlayEvent.ENDER_DRAGON_FIGHT)
        val witherTriggers = triggersFor(GlobalPlayEvent.WITHER_BOSS_FIGHT)
        if (dragonTriggers.isEmpty() && witherTriggers.isEmpty()) return
        if (tickCounter % FIGHT_CHECK_INTERVAL != 0L) return

        for (level in server.allLevels) {
            val playersInLevel = server.playerList.players.filter { it.level() == level }
            if (playersInLevel.isEmpty()) continue   // некому показывать, состояние не трогаем

            if (dragonTriggers.isNotEmpty() && level.dimension() == Level.END) {
                val present = level.getEntities(EntityType.ENDER_DRAGON) { it.isAlive }.isNotEmpty()
                if (present && wasDragonPresent[level] != true) {
                    dragonTriggers.forEach { t ->
                        playersInLevel.forEach { p -> fire(t.body, server, p, t.scriptName) }
                    }
                }
                wasDragonPresent[level] = present
            }

            if (witherTriggers.isNotEmpty()) {
                val present = level.getEntities(EntityType.WITHER) { it.isAlive }.isNotEmpty()
                if (present && wasWitherPresent[level] != true) {
                    witherTriggers.forEach { t ->
                        playersInLevel.forEach { p -> fire(t.body, server, p, t.scriptName) }
                    }
                }
                wasWitherPresent[level] = present
            }
        }
    }

    /** player_eat: по росту сытости игрока между тиками. */
    private fun checkPlayerEat(server: MinecraftServer) {
        val eatTriggers = triggersFor(GlobalPlayEvent.PLAYER_EAT)
        if (eatTriggers.isEmpty()) return

        for (player in server.playerList.players) {
            val current = player.foodData.foodLevel
            val previous = lastFoodLevel[player.uuid]
            if (previous != null && current > previous) {
                eatTriggers.forEach { t -> fire(t.body, server, player, t.scriptName) }
            }
            lastFoodLevel[player.uuid] = current
        }
    }

    private fun fire(
        body: List<ScriptCommand>,
        server: MinecraftServer,
        player: ServerPlayer,
        scriptName: String
    ) {
        ScriptManager.runAdHoc(body, ScriptContext(server, player), scriptName)
    }
}