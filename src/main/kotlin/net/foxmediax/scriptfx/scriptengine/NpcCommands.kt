package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.npc.NpcDialogWait
import net.foxmediax.scriptfx.npc.NpcInteractWait
import net.foxmediax.scriptfx.npc.NpcManager
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.server.level.ServerLevel
import kotlin.math.atan2
import kotlin.math.hypot
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking

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

        register("see_npc_K") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || args.size < 4) {
                ScriptFXLog.warn("see_npc_K: нужно see_npc_K \"id\" x y z")
                return@register CommandResult.Continue
            }

            val ref = ctx.player
            val x = parseCoord(args[1], ref?.x)
            val y = parseCoord(args[2], ref?.y)
            val z = parseCoord(args[3], ref?.z)
            if (x == null || y == null || z == null) {
                ScriptFXLog.warn("see_npc_K: неверные координаты")
                return@register CommandResult.Continue
            }

            val level = (ref?.level() ?: ctx.server.overworld()) as ServerLevel
            val entity = findNpc(level, id)
            if (entity == null) {
                ScriptFXLog.warn("see_npc_K: NPC '$id' не найден")
                return@register CommandResult.Continue
            }

            // чтобы tick() с LookAt не перебил поворот
            entity.setLookAtPlayer(false)
            facePoint(entity, x, y, z)
            ScriptFXLog.info("see_npc_K '$id' → $x $y $z")
            CommandResult.Continue
        }

        // see_npc_C "id" yaw [pitch]  — поворот по горизонтали (и опционально pitch)
        register("see_npc_C") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            val yawRaw = args.getOrNull(1)?.removeSurrounding("\"")?.trim()
            if (id.isEmpty() || yawRaw == null) {
                ScriptFXLog.warn("see_npc_C: нужно see_npc_C \"id\" градус [pitch]")
                return@register CommandResult.Continue
            }

            val yaw = yawRaw.toFloatOrNull()
            if (yaw == null) {
                ScriptFXLog.warn("see_npc_C: градус должен быть числом, получено '$yawRaw'")
                return@register CommandResult.Continue
            }
            val pitch = args.getOrNull(2)?.removeSurrounding("\"")?.toFloatOrNull() ?: 0f

            val level = (ctx.player?.level() ?: ctx.server.overworld()) as ServerLevel
            val entity = findNpc(level, id)
            if (entity == null) {
                ScriptFXLog.warn("see_npc_C: NPC '$id' не найден")
                return@register CommandResult.Continue
            }

            entity.setLookAtPlayer(false)
            faceYawPitch(entity, yaw, pitch)
            ScriptFXLog.info("see_npc_C '$id' yaw=$yaw pitch=$pitch")
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

            // --- разбор аргументов (без изменений) ---
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

            val uuid = serverPlayer.uuid
            val level = serverPlayer.level() as ServerLevel
            val npc = findNpc(level, npcId)
            if (npc == null) ScriptFXLog.warn("npc_dialog_hud: NPC '$npcId' не найден в мире (диалог без камеры и обводки)")

            // NPC поворачивается к игроку; камеру ведёт клиент (NpcDialogClient)
            npc?.dialogFocus = uuid

            NpcDialogWait.begin(uuid)
            ServerPlayNetworking.send(
                serverPlayer,
                net.foxmediax.scriptfx.network.NpcDialogOpenPayload(
                    npcId, text, buttons[0], buttons[1], buttons[2], buttons[3], buttons[4],
                    npc?.id ?: -1
                )
            )

            fun finish(btn: Int): Boolean {
                npc?.dialogFocus = null
                NpcDialogWait.clear(uuid)
                ctx.vars["dialog_button"] = btn.toString()
                ScriptFXLog.info("dialog_button = $btn")
                return true
            }

            fun step(): Boolean {
                // игрок вышел из игры: не вешаем скрипт
                ctx.server.playerList.getPlayer(uuid) ?: return finish(0)
                return if (NpcDialogWait.isDone(uuid)) finish(NpcDialogWait.chosen(uuid)) else false
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

    private fun findNpc(level: ServerLevel, id: String): ScriptNpcEntity? {
        val state = net.foxmediax.scriptfx.npc.NpcRuntime.get(id)
        if (state != null) {
            val e = level.getEntity(state.entityUuid) as? ScriptNpcEntity
            if (e != null) return e
        }
        return level.getAllEntities()
            .filterIsInstance<ScriptNpcEntity>()
            .find { it.npcId == id }
    }

    /** Повернуть NPC лицом к точке (x,y,z в мире). */
    private fun facePoint(entity: ScriptNpcEntity, x: Double, y: Double, z: Double) {
        val dx = x - entity.x
        val dy = y - entity.eyeY
        val dz = z - entity.z
        val horiz = hypot(dx, dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(-atan2(dy, horiz)).toFloat().coerceIn(-90f, 90f)
        faceYawPitch(entity, yaw, pitch)
    }

    private fun faceYawPitch(entity: ScriptNpcEntity, yaw: Float, pitch: Float) {
        val y = yaw
        val p = pitch.coerceIn(-90f, 90f)
        entity.yRot = y
        entity.xRot = p
        entity.yRotO = y
        entity.xRotO = p
        entity.yHeadRot = y
        entity.yBodyRot = y
        entity.yHeadRotO = y
        entity.yBodyRotO = y
        // синхронизация с клиентом
        entity.setYBodyRot(y)
        entity.setYHeadRot(y)
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