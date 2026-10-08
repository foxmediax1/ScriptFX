package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.npc.NpcDialogWait
import net.foxmediax.scriptfx.npc.NpcInteractWait
import net.foxmediax.scriptfx.npc.NpcManager
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.server.level.ServerLevel
import net.foxmediax.scriptfx.npc.NpcRuntime
import kotlin.math.atan2
import kotlin.math.hypot
import net.foxmediax.scriptfx.network.NpcDialogOpenPayload
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object NpcCommands {

    private const val DIALOG_CAM_DISTANCE = 1.8   // блоков от NPC до камеры
    private const val DIALOG_CAM_FOV = 55f        // FOV во время диалога
    private const val DIALOG_MOVE_TICKS = 16      // 0.8 сек на подъезд и на возврат

    private val DIALOG_CAM_SIDE_OFFSETS = doubleArrayOf(25.0, -25.0, 0.0, 55.0, -55.0, 85.0, -85.0)
    private const val DIALOG_CAM_WALL_MARGIN = 0.35   // отступ камеры от стены
    private const val DIALOG_CAM_MIN_DISTANCE = 0.45  // ближе к NPC камеру не ставим
    private val WORLD_KEYS = setOf("overworld", "nether", "end")

    private class CamPose(val x: Double, val y: Double, val z: Double, val yaw: Float, val pitch: Float)

    fun install(register: (String, CommandHandler) -> Unit) {

        register("npc_spawn") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || args.size < 4) {
                ScriptFXLog.warn("npc_spawn: нужны id x y z [поворот наклон] [мир] [анимация] [режим]")
                return@register CommandResult.Continue
            }

            val ref = ctx.player ?: ctx.server.playerList.players.firstOrNull()
            val x = parseCoord(args[1], ref?.x)
            val y = parseCoord(args[2], ref?.y)
            val z = parseCoord(args[3], ref?.z)
            if (x == null || y == null || z == null) {
                ScriptFXLog.warn("npc_spawn: неверные координаты (для ~ нужен игрок в мире)")
                return@register CommandResult.Continue
            }

            val angles = mutableListOf<Float>()
            var worldKey: String? = null
            val words = mutableListOf<String>()
            for (raw in args.drop(4)) {
                val t = raw.removeSurrounding("\"").trim()
                val angle = if (angles.size < 2) parseAngle(t, if (angles.isEmpty()) ref?.yRot else ref?.xRot) else null
                when {
                    t.lowercase() in WORLD_KEYS -> worldKey = t
                    angle != null -> angles.add(angle)
                    else -> words.add(t)
                }
            }

            val level = if (worldKey != null) {
                CommandRegistry.resolveLevel(ctx.server, worldKey)
                    ?: run {
                        ScriptFXLog.warn("npc_spawn: мир '$worldKey' не найден")
                        return@register CommandResult.Continue
                    }
            } else {
                (ref?.level() ?: ctx.server.overworld()) as ServerLevel
            }

            NpcManager.spawn(
                level, id, x, y, z,
                words.getOrNull(0).orEmpty(),      // анимация ("" = из шаблона)
                words.getOrNull(1).orEmpty(),      // режим ("" = из шаблона)
                angles.getOrElse(0) { 0f },
                angles.getOrElse(1) { 0f }
            )
            CommandResult.Continue
        }

        register("npc_despawn") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"") ?: return@register CommandResult.Continue
            val level = (ctx.player?.level() ?: ctx.server.overworld()) as ServerLevel
            NpcManager.despawn(level, id)
            CommandResult.Continue
        }

        register("npc_repack") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"") ?: return@register CommandResult.Continue
            val rest = args.drop(1)
            val level = (ctx.player?.level() ?: ctx.server.overworld()) as ServerLevel
            NpcManager.repack(level, id, rest)
            CommandResult.Continue
        }

        // npc_interact_key "X" — скрипт ждёт нажатия (клиент шлёт пакет)
        register("npc_interact_key") { ctx, args ->
            val keyName = args.getOrNull(0)?.removeSurrounding("\"")?.uppercase() ?: "X"
            val player = ctx.player ?: ctx.server.playerList.players.firstOrNull()
            if (player == null) {
                ScriptFXLog.warn("npc_interact_key: нет игроков на сервере")
                return@register CommandResult.Continue
            }
            val uuid = player.uuid
            NpcInteractWait.begin(uuid, keyName)
            // сказать клиенту, какую клавишу слушать
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                net.foxmediax.scriptfx.network.NpcInteractListenPayload(keyName)
            )
            CommandResult.WaitUntil {
                if (NpcInteractWait.isDone(uuid)) {
                    NpcInteractWait.clear(uuid)
                    true
                } else false
            }
        }

        register("npc_dialog_hud") { ctx, args ->
            val serverPlayer = ctx.player
                ?: ctx.server.playerList.players.firstOrNull()
                ?: return@register CommandResult.Continue

            // --- разбор аргументов (как было) ---
            val joined = args.joinToString(" ")
            val parts = joined.split("::").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) {
                ScriptFXLog.warn("npc_dialog_hud: пустые аргументы")
                return@register CommandResult.Continue
            }
            val npcId = parts[0].removeSurrounding("\"").trim()
            var text = ""
            val buttons = MutableList(5) { "" }
            for (p in parts.drop(1)) {
                when {
                    p.startsWith("dialog_window_text", ignoreCase = true) -> {
                        text = p.substringAfter("dialog_window_text").trim()
                            .removePrefix("=").trim()
                            .removeSurrounding("\"")
                    }
                    p.startsWith("button", ignoreCase = true) -> {
                        val num = p.removePrefix("button").removePrefix("Button")
                            .takeWhile { it.isDigit() }.toIntOrNull()
                        val value = p.substringAfter("=").trim().removeSurrounding("\"")
                        if (num != null && num in 1..5) buttons[num - 1] = value
                    }
                }
            }

            // --- NPC и исходная поза камеры ---
            val uuid = serverPlayer.uuid
            val level = serverPlayer.level() as ServerLevel
            val npc = NpcRuntime.get(npcId)?.let { level.getEntity(it.entityUuid) } as? ScriptNpcEntity
            if (npc == null) ScriptFXLog.warn("npc_dialog_hud: NPC '$npcId' не найден в мире для камеры")

            val wasInCutscene = CutsceneManager.isInCutscene(serverPlayer)
            val origin = CamPose(
                serverPlayer.x,
                if (wasInCutscene) serverPlayer.y else serverPlayer.eyeY,
                serverPlayer.z, serverPlayer.yRot, serverPlayer.xRot
            )
            val startedHere = npc != null && !wasInCutscene

            if (npc != null) {
                if (startedHere) {
                    CutsceneManager.start(ctx.server, "player", listOf(serverPlayer))
                    CutsceneManager.setLock(serverPlayer, true)
                    CutsceneManager.setHud(serverPlayer, true)
                }

                npc.dialogFocus = uuid

                val toPlayerYaw = Math.toDegrees(atan2(-(origin.x - npc.x), origin.z - npc.z))
                val (cx, cz) = pickDialogCamera(level, npc, toPlayerYaw)
                val yaw = Math.toDegrees(atan2(-(npc.x - cx), npc.z - cz)).toFloat()

                CutsceneManager.moveCamera(serverPlayer, cx, npc.eyeY, cz, yaw, 0f, DIALOG_MOVE_TICKS)
                CutsceneManager.setFov(serverPlayer, DIALOG_CAM_FOV, DIALOG_MOVE_TICKS)
            }

            NpcDialogWait.begin(uuid)
            val payload = net.foxmediax.scriptfx.network.NpcDialogOpenPayload(
                npcId, text, buttons[0], buttons[1], buttons[2], buttons[3], buttons[4],
                npc?.id ?: -1
            )

            var phase = 0   // 0 = подъезд, 1 = ждём выбор, 2 = возврат камеры
            var phaseEnd = ctx.server.tickCount + (if (npc != null) DIALOG_MOVE_TICKS + 2 else 0)
            var chosen = 0

            fun finish(btn: Int): Boolean {
                npc?.dialogFocus = null
                NpcDialogWait.clear(uuid)
                ctx.vars["dialog_button"] = btn.toString()
                ScriptFXLog.info("dialog_button = $btn")
                return true
            }

            fun step(): Boolean {
                val p = ctx.server.playerList.getPlayer(uuid) ?: return finish(0)
                if (npc != null && !CutsceneManager.isInCutscene(p)) return finish(0)
                val now = ctx.server.tickCount
                when (phase) {
                    0 -> if (now >= phaseEnd) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload)
                        phase = 1
                    }
                    1 -> if (NpcDialogWait.isDone(uuid)) {
                        chosen = NpcDialogWait.chosen(uuid)
                        if (npc == null) return finish(chosen)
                        CutsceneManager.moveCamera(p, origin.x, origin.y, origin.z, origin.yaw, origin.pitch, DIALOG_MOVE_TICKS)
                        CutsceneManager.setFov(p, 0f, DIALOG_MOVE_TICKS)
                        phaseEnd = now + DIALOG_MOVE_TICKS + 2
                        phase = 2
                    }
                    2 -> if (now >= phaseEnd) {
                        if (startedHere) CutsceneManager.endFor(p)
                        return finish(chosen)
                    }
                }
                return false
            }

            CommandResult.WaitUntil { step() }
        }



        register("if") { ctx, args ->
            // ожидаем: dialog_button == 1 {
            val tokens = args
                .flatMap { it.split(" ").map(String::trim) }
                .map { it.removeSuffix("{").trim() }
                .filter { it.isNotEmpty() && it != "{" }

            if (tokens.size < 3 || tokens[1] != "==") {
                ScriptFXLog.warn("if: нужно 'if dialog_button == N {', args=$args")
                return@register CommandResult.Continue
            }
            val leftName = tokens[0]
            val right = tokens[2]
            val left = ctx.vars[leftName] ?: ""
            val ok = left == right
            ScriptFXLog.info("if: $leftName('$left') == '$right' -> $ok")
            if (ok) CommandResult.Continue else CommandResult.SkipBlock
        }

        register("}") { _, _ -> CommandResult.Continue }
    }

    /** Выбирает точку камеры перед NPC так, чтобы она не оказалась внутри блока. */
    private fun pickDialogCamera(level: ServerLevel, npc: ScriptNpcEntity, toPlayerYaw: Double): Pair<Double, Double> {
        val from = Vec3(npc.x, npc.eyeY, npc.z)
        var bestDist = -1.0
        var bestX = npc.x
        var bestZ = npc.z

        for (off in DIALOG_CAM_SIDE_OFFSETS) {
            val a = Math.toRadians(toPlayerYaw + off)
            val dirX = -sin(a)
            val dirZ = cos(a)
            val to = Vec3(from.x + dirX * DIALOG_CAM_DISTANCE, from.y, from.z + dirZ * DIALOG_CAM_DISTANCE)

            val hit = level.clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc))
            val free = if (hit.type == HitResult.Type.MISS) DIALOG_CAM_DISTANCE else hit.location.distanceTo(from)
            val dist = if (free >= DIALOG_CAM_DISTANCE) DIALOG_CAM_DISTANCE
            else maxOf(free - DIALOG_CAM_WALL_MARGIN, DIALOG_CAM_MIN_DISTANCE)

            if (dist > bestDist) {
                bestDist = dist
                bestX = from.x + dirX * dist
                bestZ = from.z + dirZ * dist
            }
            if (free >= DIALOG_CAM_DISTANCE) break   // первый полностью свободный вариант
        }
        return bestX to bestZ
    }

    /** "12.5", "~", "~3", "~-2" -> число. base нужен только для ~. */
    private fun parseCoord(raw: String, base: Double?): Double? {
        val t = raw.removeSurrounding("\"").trim()
        if (t.startsWith("~")) {
            if (base == null) return null
            val off = t.drop(1)
            return base + (if (off.isEmpty()) 0.0 else off.toDoubleOrNull() ?: return null)
        }
        return t.toDoubleOrNull()
    }

    /** "90", "~", "~180" -> угол в градусах. */
    private fun parseAngle(raw: String, base: Float?): Float? {
        if (raw.startsWith("~")) {
            if (base == null) return null
            val off = raw.drop(1)
            return base + (if (off.isEmpty()) 0f else off.toFloatOrNull() ?: return null)
        }
        return raw.toFloatOrNull()
    }
}