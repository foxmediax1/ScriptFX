package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.CutscenePayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CutsceneManager {

    private data class SavedPos(
        val level: ServerLevel,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    )

    private data class MoveAnim(
        val fromX: Double, val fromY: Double, val fromZ: Double,
        val fromYaw: Float, val fromPitch: Float,
        val toX: Double, val toY: Double, val toZ: Double,
        val toYaw: Float, val toPitch: Float,
        val startTick: Long,
        val durationTicks: Int
    )

    private data class PathPoint(
        val x: Double, val y: Double, val z: Double,
        val yaw: Float, val pitch: Float,
        val durationTicks: Int
    )

    private data class State(
        val mode: String,
        val locked: Boolean = false,
        val hudHidden: Boolean = false,
        val letterbox: Boolean = false,
        val letterboxHeight: Float = 0.15f,
        val savedPos: SavedPos? = null,
        val move: MoveAnim? = null,
        val path: MutableList<PathPoint> = mutableListOf(),
        val pathBuilding: Boolean = false,
        val fov: Float = 70f,
        val cameraPlaced: Boolean = false
    )

    private val active = ConcurrentHashMap<UUID, State>()
    private var tickCounter = 0L

    fun isInCutscene(player: ServerPlayer): Boolean = active.containsKey(player.uuid)

    /** Вызывать каждый серверный тик из ScriptFXServer */
    fun tick(server: MinecraftServer) {
        tickCounter++
        for ((uuid, state) in active) {
            val move = state.move ?: continue
            val player = server.playerList.getPlayer(uuid) ?: continue

            val elapsed = (tickCounter - move.startTick).toInt()

            if (elapsed >= move.durationTicks) {
                // Конец движения
                teleportQuiet(player, player.level() as ServerLevel, move.toX, move.toY, move.toZ, move.toYaw, move.toPitch)
                active[uuid] = state.copy(move = null)
                if (active[uuid]?.path?.isNotEmpty() == true) {
                    startNextPathPoint(player)
                }
                continue
            }

            val t = elapsed.toFloat() / move.durationTicks
            val s = t * t * (3f - 2f * t) // smoothstep

            val x = lerp(move.fromX, move.toX, s)
            val y = lerp(move.fromY, move.toY, s)
            val z = lerp(move.fromZ, move.toZ, s)
            val yaw = lerpAngle(move.fromYaw, move.toYaw, s)
            val pitch = lerp(move.fromPitch, move.toPitch, s)

            teleportQuiet(player, player.level() as ServerLevel, x, y, z, yaw, pitch)
        }
    }

    fun start(server: MinecraftServer, mode: String, targets: List<ServerPlayer>) {
        for (player in targets) {
            // Повторный старт не должен перезаписывать точку возврата.
            if (active.containsKey(player.uuid)) continue
            val level = player.level() as ServerLevel
            val saved = SavedPos(level, player.x, player.y, player.z, player.yRot, player.xRot)
            active[player.uuid] = State(mode = mode, savedPos = saved)
            send(player, CutscenePayload(action = CutscenePayload.START))
        }
    }

    fun abort(player: ServerPlayer) {
        active.remove(player.uuid) ?: return
        send(player, CutscenePayload(action = CutscenePayload.END))
    }

    fun endFor(player: ServerPlayer) {
        val state = active[player.uuid] ?: return
        // Телепортируем, пока игрок ещё "в катсцене": миксин player_tp это проигнорирует.
        state.savedPos?.let { pos ->
            teleportQuiet(player, pos.level, pos.x, pos.y, pos.z, pos.yaw, pos.pitch)
        }
        active.remove(player.uuid)
        send(player, CutscenePayload(action = CutscenePayload.END))
    }

    fun end(server: MinecraftServer, players: Collection<ServerPlayer>) {
        players.forEach { endFor(it) }
    }

    fun endAll(server: MinecraftServer) {
        active.keys.toList().forEach { uuid ->
            server.playerList.getPlayer(uuid)?.let { endFor(it) }
        }
        active.clear()
    }

    fun setCamera(
        player: ServerPlayer,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        worldKey: String = ""
    ) {
        if (!isInCutscene(player)) return

        val level = resolveLevel(player.level().server, worldKey) ?: player.level() as ServerLevel
        // Останавливаем текущее движение
        active.computeIfPresent(player.uuid) { _, s -> s.copy(move = null) }

        teleportQuiet(player, level, x, y, z, yaw, pitch)

        send(player, CutscenePayload(
            action = CutscenePayload.SET,
            x = x, y = y, z = z, yaw = yaw, pitch = pitch, worldKey = worldKey
        ))
        active.computeIfPresent(player.uuid) { _, s -> s.copy(move = null, cameraPlaced = true) }
    }

    fun moveCamera(
        player: ServerPlayer,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        durationTicks: Int
    ) {
        if (!isInCutscene(player)) return

        val duration = durationTicks.coerceAtLeast(1)
        val move = MoveAnim(
            fromX = player.x, fromY = player.y, fromZ = player.z,
            fromYaw = player.yRot, fromPitch = player.xRot,
            toX = x, toY = y, toZ = z,
            toYaw = yaw, toPitch = pitch,
            startTick = tickCounter,
            durationTicks = duration
        )
        active.computeIfPresent(player.uuid) { _, s -> s.copy(move = move) }

        send(player, CutscenePayload(
            action = CutscenePayload.MOVE,
            x = x, y = y, z = z, yaw = yaw, pitch = pitch,
            durationTicks = duration
        ))

        active.computeIfPresent(player.uuid) { _, s -> s.copy(move = move, cameraPlaced = true) }
    }

    fun setLock(player: ServerPlayer, locked: Boolean) {
        if (!isInCutscene(player)) return
        active.computeIfPresent(player.uuid) { _, s -> s.copy(locked = locked) }
        send(player, CutscenePayload(action = CutscenePayload.LOCK, flag = locked))
    }

    fun setHud(player: ServerPlayer, hide: Boolean) {
        if (!isInCutscene(player)) return
        active.computeIfPresent(player.uuid) { _, s -> s.copy(hudHidden = hide) }
        send(player, CutscenePayload(action = CutscenePayload.HUD, flag = hide))
    }

    fun setLetterbox(player: ServerPlayer, enabled: Boolean, height: Float = 0.15f) {
        if (!isInCutscene(player)) return
        val h = height.coerceIn(0f, 0.4f)
        active.computeIfPresent(player.uuid) { _, s -> s.copy(letterbox = enabled, letterboxHeight = h) }
        send(player, CutscenePayload(action = CutscenePayload.LETTERBOX, flag = enabled, value = h))
    }

    fun onInterrupt(player: ServerPlayer) {
        endFor(player)
        ScriptFXLog.info("Катсцена прервана игроком ${player.scoreboardName}")
    }

    fun resolveTargets(context: ScriptContext, mode: String): List<ServerPlayer> =
        when (mode.lowercase()) {
            "all" -> context.server.playerList.players
            else -> context.player?.let { listOf(it) } ?: context.server.playerList.players
        }

    // ---- helpers ----

    private fun teleportQuiet(
        player: ServerPlayer,
        level: ServerLevel,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float
    ) {
        player.teleportTo(level, x, y, z, emptySet<Relative>(), yaw, pitch, true)
        player.resetFallDistance()
        player.setDeltaMovement(Vec3.ZERO)
    }

    private fun resolveLevel(server: MinecraftServer, worldKey: String): ServerLevel? {
        if (worldKey.isBlank()) return null
        return when (worldKey.lowercase()) {
            "overworld" -> server.overworld()
            "nether" -> server.getLevel(Level.NETHER)
            "end" -> server.getLevel(Level.END)
            else -> null
        }
    }

    private fun send(player: ServerPlayer, payload: CutscenePayload) {
        if (ServerPlayNetworking.canSend(player, CutscenePayload.TYPE)) {
            ServerPlayNetworking.send(player, payload)
        }
    }

    private fun lerp(a: Double, b: Double, t: Float) = a + (b - a) * t
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var diff = (to - from) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return from + diff * t
    }

    fun pathStart(player: ServerPlayer) {
        active.computeIfPresent(player.uuid) { _, s ->
            s.copy(path = mutableListOf(), pathBuilding = true, move = null)
        }
    }

    fun pathPoint(
        player: ServerPlayer,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float,
        durationTicks: Int
    ) {
        active.computeIfPresent(player.uuid) { _, s ->
            if (!s.pathBuilding) return@computeIfPresent s
            s.path.add(PathPoint(x, y, z, yaw, pitch, durationTicks.coerceAtLeast(1)))
            s
        }
    }

    fun pathEnd(player: ServerPlayer): Int {
        val state = active[player.uuid] ?: return 0
        if (!state.pathBuilding || state.path.isEmpty()) {
            active.computeIfPresent(player.uuid) { _, s -> s.copy(pathBuilding = false) }
            return 0
        }
        val total = state.path.sumOf { it.durationTicks }
        active[player.uuid] = state.copy(pathBuilding = false, path = state.path.toMutableList())
        startNextPathPoint(player)
        return total
    }

    private fun startNextPathPoint(player: ServerPlayer) {
        val state = active[player.uuid] ?: return
        if (state.path.isEmpty()) {
            active[player.uuid] = state.copy(move = null)
            return
        }
        val point = state.path.removeAt(0)
        val move = MoveAnim(
            fromX = player.x, fromY = player.y, fromZ = player.z,
            fromYaw = player.yRot, fromPitch = player.xRot,
            toX = point.x, toY = point.y, toZ = point.z,
            toYaw = point.yaw, toPitch = point.pitch,
            startTick = tickCounter,
            durationTicks = point.durationTicks
        )
        active[player.uuid] = state.copy(move = move)

        send(player, CutscenePayload(
            action = CutscenePayload.MOVE,
            x = point.x, y = point.y, z = point.z,
            yaw = point.yaw, pitch = point.pitch,
            durationTicks = point.durationTicks
        ))

        active[player.uuid] = state.copy(move = move, cameraPlaced = true)
    }

    fun setFov(player: ServerPlayer, fov: Float, durationTicks: Int) {
        if (!isInCutscene(player)) return
        active.computeIfPresent(player.uuid) { _, s -> s.copy(fov = fov) }
        send(player, CutscenePayload(
            action = CutscenePayload.FOV,
            value = fov,
            durationTicks = durationTicks.coerceAtLeast(0)
        ))
    }

    fun lookAt(
        player: ServerPlayer,
        tx: Double, ty: Double, tz: Double,
        durationTicks: Int
    ) {
        val state = active[player.uuid] ?: return

        // Клиент стартует камеру на уровне глаз, а после camera_set/move - в точке камеры.
        val ox = player.x
        val oy = if (state.cameraPlaced) player.y else player.eyeY
        val oz = player.z

        val dx = tx - ox
        val dy = ty - oy
        val dz = tz - oz
        val horiz = kotlin.math.sqrt(dx * dx + dz * dz)

        val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-kotlin.math.atan2(dy, horiz)).toFloat()

        if (durationTicks <= 0) {
            setCamera(player, ox, oy, oz, yaw, pitch)
        } else {
            moveCamera(player, ox, oy, oz, yaw, pitch, durationTicks)
        }
    }

    private fun resolveActive(context: ScriptContext): List<ServerPlayer> {
        val inCutscene = context.server.playerList.players
            .filter { CutsceneManager.isInCutscene(it) }
        val bound = context.player
        return if (bound != null) inCutscene.filter { it == bound } else inCutscene
    }
}