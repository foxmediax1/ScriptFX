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

    /** Какие once-триггеры уже сработали у игрока. */
    private val firedOnce = HashMap<UUID, MutableSet<Trigger>>()

    /** Кулдауны: ключ = playerUUID + identityHashCode(trigger) */
    private val cooldowns = HashMap<String, Long>()

    // Для ender_dragon_fight / wither_boss_fight
    private val wasDragonPresent = HashMap<ServerLevel, Boolean>()
    private val wasWitherPresent = HashMap<ServerLevel, Boolean>()

    // Для player_eat
    private val lastFoodLevel = HashMap<UUID, Int>()

    private var tickCounter = 0L

    fun clear() {
        checkpoints.clear()
        worldStarts.clear()
        globalPlay.clear()
        insideCheckpoint.clear()
        firedOnce.clear()
        cooldowns.clear()
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
    // Проверка once / cooldown / conditions
    // ------------------------------------------------------------------

    private fun canFire(trigger: Trigger, player: ServerPlayer): Boolean {
        // Условия (States)
        if (!TriggerConditions.check(trigger.conditions, player)) return false

        // Once
        if (trigger.once) {
            val set = firedOnce.getOrPut(player.uuid) { HashSet() }
            if (trigger in set) return false
        }

        // Cooldown
        if (trigger.cooldownTicks > 0) {
            val key = cooldownKey(player.uuid, trigger)
            val last = cooldowns[key]
            if (last != null && tickCounter - last < trigger.cooldownTicks) {
                return false
            }
        }

        return true
    }

    private fun markFired(trigger: Trigger, player: ServerPlayer) {
        if (trigger.once) {
            firedOnce.getOrPut(player.uuid) { HashSet() }.add(trigger)
        }
        if (trigger.cooldownTicks > 0) {
            cooldowns[cooldownKey(player.uuid, trigger)] = tickCounter
        }
    }

    private fun cooldownKey(uuid: UUID, trigger: Trigger): String =
        "$uuid:${System.identityHashCode(trigger)}"

    private fun fire(trigger: Trigger, server: MinecraftServer, player: ServerPlayer) {
        if (!canFire(trigger, player)) return
        markFired(trigger, player)
        ScriptManager.runAdHoc(
            trigger.body,
            ScriptContext(server, player, triggerType = "trigger", scriptName = trigger.scriptName),
            trigger.scriptName
        )
    }

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
        worldStarts.forEach { trigger ->
            fire(trigger, server, player)
        }
    }

    fun onPlayerLeave(uuid: UUID) {
        lastFoodLevel.remove(uuid)
        insideCheckpoint.remove(uuid)
        // once и cooldown оставляем (чтобы once не сбрасывался при перезаходе)
    }

    fun onPlayerDeath(server: MinecraftServer, player: ServerPlayer) {
        triggersFor(GlobalPlayEvent.PLAYER_DEATH).forEach { fire(it, server, player) }
    }

    fun onPlayerRespawn(server: MinecraftServer, player: ServerPlayer) {
        lastFoodLevel.remove(player.uuid)
        triggersFor(GlobalPlayEvent.PLAYER_RESPAWN).forEach { fire(it, server, player) }
    }

    fun onPlayerTeleport(server: MinecraftServer, player: ServerPlayer) {
        if (CutsceneManager.isInCutscene(player)) return
        triggersFor(GlobalPlayEvent.PLAYER_TP).forEach { fire(it, server, player) }
    }

    fun onPlayerClick(server: MinecraftServer, player: ServerPlayer, itemId: String) {
        triggersFor(GlobalPlayEvent.PLAYER_CLICK)
            .filter { matchesItemId(it.itemId, itemId) }
            .forEach { fire(it, server, player) }
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
                if (CutsceneManager.isInCutscene(player)) continue

                val inside = player.level() == level &&
                        player.position().distanceToSqr(checkpoint.x, checkpoint.y, checkpoint.z) <= radiusSq

                val set = insideCheckpoint.getOrPut(player.uuid) { HashSet() }

                if (inside) {
                    // Вошли в зону
                    if (set.add(checkpoint) && !checkpoint.onExit) {
                        fire(checkpoint, server, player)
                    }
                } else {
                    // Вышли из зоны
                    if (set.remove(checkpoint) && checkpoint.onExit) {
                        fire(checkpoint, server, player)
                    }
                }
            }
        }
    }

    private fun checkGlobalEntityFights(server: MinecraftServer) {
        val dragonTriggers = triggersFor(GlobalPlayEvent.ENDER_DRAGON_FIGHT)
        val witherTriggers = triggersFor(GlobalPlayEvent.WITHER_BOSS_FIGHT)
        if (dragonTriggers.isEmpty() && witherTriggers.isEmpty()) return
        if (tickCounter % FIGHT_CHECK_INTERVAL != 0L) return

        for (level in server.allLevels) {
            val playersInLevel = server.playerList.players.filter { it.level() == level }
            if (playersInLevel.isEmpty()) continue

            if (dragonTriggers.isNotEmpty() && level.dimension() == Level.END) {
                val present = level.getEntities(EntityType.ENDER_DRAGON) { it.isAlive }.isNotEmpty()
                if (present && wasDragonPresent[level] != true) {
                    dragonTriggers.forEach { t ->
                        playersInLevel.forEach { p -> fire(t, server, p) }
                    }
                }
                wasDragonPresent[level] = present
            }

            if (witherTriggers.isNotEmpty()) {
                val present = level.getEntities(EntityType.WITHER) { it.isAlive }.isNotEmpty()
                if (present && wasWitherPresent[level] != true) {
                    witherTriggers.forEach { t ->
                        playersInLevel.forEach { p -> fire(t, server, p) }
                    }
                }
                wasWitherPresent[level] = present
            }
        }
    }

    private fun checkPlayerEat(server: MinecraftServer) {
        val eatTriggers = triggersFor(GlobalPlayEvent.PLAYER_EAT)
        if (eatTriggers.isEmpty()) return

        for (player in server.playerList.players) {
            val current = player.foodData.foodLevel
            val previous = lastFoodLevel[player.uuid]
            if (previous != null && current > previous) {
                eatTriggers.forEach { t -> fire(t, server, player) }
            }
            lastFoodLevel[player.uuid] = current
        }
    }
}