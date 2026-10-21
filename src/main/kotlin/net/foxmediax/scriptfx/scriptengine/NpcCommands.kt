package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.npc.NpcDialogWait
import net.foxmediax.scriptfx.npc.NpcInteractWait
import net.foxmediax.scriptfx.npc.NpcManager
import net.foxmediax.scriptfx.npc.NpcRuntime
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Relative
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

object NpcCommands {

    private val WORLD_KEYS = setOf("overworld", "nether", "end")

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
                val angle = if (angles.size < 2) {
                    parseAngle(t, if (angles.isEmpty()) ref?.yRot else ref?.xRot)
                } else null
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
                words.getOrNull(0).orEmpty(),
                words.getOrNull(1).orEmpty(),
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

            entity.setLookAtPlayer(false)
            facePoint(entity, x, y, z)
            ScriptFXLog.info("see_npc_K '$id' → $x $y $z")
            CommandResult.Continue
        }

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

        register("npc_interact_key") { ctx, args ->
            val keyName = args.getOrNull(0)?.removeSurrounding("\"")?.uppercase() ?: "X"
            val player = ctx.player ?: ctx.server.playerList.players.firstOrNull()
            if (player == null) {
                ScriptFXLog.warn("npc_interact_key: нет игроков на сервере")
                return@register CommandResult.Continue
            }
            val uuid = player.uuid
            NpcInteractWait.begin(uuid, keyName)
            ServerPlayNetworking.send(
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
            if (npc == null) {
                ScriptFXLog.warn("npc_dialog_hud: NPC '$npcId' не найден в мире (диалог без камеры)")
            }

            npc?.dialogFocus = uuid

            // Точка возврата игрока (тело не двигаем — подлёт только на клиенте)
            val backX = serverPlayer.x
            val backY = serverPlayer.y
            val backZ = serverPlayer.z
            val backYaw = serverPlayer.yRot
            val backPitch = serverPlayer.xRot
            val backLevel = level

            // Только чанки у NPC, без silentTp — иначе клиентская катсцена «съедается»
            if (npc != null) {
                forceChunks(level, npc.x, npc.z, 4)
                forceChunks(level, backX, backZ, 2)
            }

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
                val p = ctx.server.playerList.getPlayer(uuid)
                if (p != null) {
                    // на случай если клиент/лаг сдвинул — вернуть точно
                    silentTp(p, backLevel, backX, backY, backZ, backYaw, backPitch)
                }
                ctx.vars["dialog_button"] = btn.toString()
                ScriptFXLog.info("dialog_button = $btn")
                return true
            }

            fun step(): Boolean {
                val p = ctx.server.playerList.getPlayer(uuid) ?: return finish(0)
                if (npc != null && npc.isAlive) {
                    forceChunks(p.level() as ServerLevel, npc.x, npc.z, 3)
                }
                return if (NpcDialogWait.isDone(uuid)) finish(NpcDialogWait.chosen(uuid)) else false
            }

            CommandResult.WaitUntil { step() }
        }

        register("if") { ctx, args ->
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

        register("npc_walk") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || args.size < 4) {
                ScriptFXLog.warn("npc_walk: нужно npc_walk \"id\" x y z [speed]")
                return@register CommandResult.Continue
            }
            val ref = ctx.player
            val x = parseCoord(args[1].removeSurrounding("\""), ref?.x)
            val y = parseCoord(args[2].removeSurrounding("\""), ref?.y)
            val z = parseCoord(args[3].removeSurrounding("\""), ref?.z)
            val speed = args.getOrNull(4)?.removeSurrounding("\"")?.toFloatOrNull() ?: 0.25f
            if (x == null || y == null || z == null) {
                ScriptFXLog.warn("npc_walk: неверные координаты")
                return@register CommandResult.Continue
            }
            val level = (ref?.level() as? ServerLevel) ?: ctx.server.overworld()
            val npc = findNpc(level, id)
            if (npc == null) {
                ScriptFXLog.warn("npc_walk: NPC '$id' не найден")
                return@register CommandResult.Continue
            }
            npc.startWalk(x, y, z, speed, "running")
            ScriptFXLog.info("npc_walk '$id' → $x $y $z speed=$speed")
            CommandResult.Continue
        }

        register("npc_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("npc_stop: нужно npc_stop \"id\"")
                return@register CommandResult.Continue
            }
            val level = (ctx.player?.level() as? ServerLevel) ?: ctx.server.overworld()
            val npc = findNpc(level, id) ?: run {
                ScriptFXLog.warn("npc_stop: NPC '$id' не найден")
                return@register CommandResult.Continue
            }
            npc.stopWalk(true)
            CommandResult.Continue
        }

        register("npc_hand") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            val itemId = args.getOrNull(1)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("npc_hand: нужно npc_hand \"id\" \"item_id\"")
                return@register CommandResult.Continue
            }
            val level = (ctx.player?.level() as? ServerLevel) ?: ctx.server.overworld()
            val npc = findNpc(level, id) ?: run {
                ScriptFXLog.warn("npc_hand: NPC '$id' не найден")
                return@register CommandResult.Continue
            }
            if (itemId.isEmpty()) {
                npc.clearHeldItem()
                ScriptFXLog.info("npc_hand '$id' cleared")
            } else {
                val stack = parseItemStack(itemId, 1)
                if (stack.isEmpty) {
                    ScriptFXLog.warn("npc_hand: неизвестный предмет '$itemId'")
                    return@register CommandResult.Continue
                }
                npc.setHeldItem(stack)
                ScriptFXLog.info("npc_hand '$id' = $itemId")
            }
            CommandResult.Continue
        }

        register("npc_drop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            val itemId = args.getOrNull(1)?.removeSurrounding("\"")?.trim().orEmpty()
            val count = args.getOrNull(2)?.removeSurrounding("\"")?.toIntOrNull()?.coerceIn(0, 100) ?: 1
            if (id.isEmpty() || itemId.isEmpty()) {
                ScriptFXLog.warn("npc_drop: нужно npc_drop \"id\" \"item_id\" count")
                return@register CommandResult.Continue
            }
            val level = (ctx.player?.level() as? ServerLevel) ?: ctx.server.overworld()
            val npc = findNpc(level, id) ?: run {
                ScriptFXLog.warn("npc_drop: NPC '$id' не найден")
                return@register CommandResult.Continue
            }
            if (count == 0) return@register CommandResult.Continue
            val stack = parseItemStack(itemId, 1)
            if (stack.isEmpty) {
                ScriptFXLog.warn("npc_drop: неизвестный предмет '$itemId'")
                return@register CommandResult.Continue
            }
            npc.dropItems(stack, count)
            ScriptFXLog.info("npc_drop '$id' $itemId x$count")
            CommandResult.Continue
        }

        register("}") { _, _ -> CommandResult.Continue }
    }

    private fun forceChunks(level: ServerLevel, x: Double, z: Double, radius: Int) {
        val cx = Mth.floor(x) shr 4
        val cz = Mth.floor(z) shr 4
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                runCatching { level.getChunk(cx + dx, cz + dz) }
            }
        }
    }

    private fun silentTp(
        player: ServerPlayer,
        level: ServerLevel,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float
    ) {
        forceChunks(level, x, z, 4)
        player.teleportTo(
            level, x, y, z,
            emptySet<Relative>(),
            yaw, pitch,
            true
        )
        player.resetFallDistance()
        player.setDeltaMovement(Vec3.ZERO)
        runCatching { level.chunkSource.chunkMap.move(player) }
    }

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
        val state = NpcRuntime.get(id)
        if (state != null) {
            val e = level.getEntity(state.entityUuid) as? ScriptNpcEntity
            if (e != null) return e
        }
        return level.getAllEntities()
            .filterIsInstance<ScriptNpcEntity>()
            .find { it.npcId == id }
    }

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
        entity.setYBodyRot(y)
        entity.setYHeadRot(y)
    }

    private fun parseAngle(raw: String, base: Float?): Float? {
        if (raw.startsWith("~")) {
            if (base == null) return null
            val off = raw.drop(1)
            return base + (if (off.isEmpty()) 0f else off.toFloatOrNull() ?: return null)
        }
        return raw.toFloatOrNull()
    }

    private fun parseItemStack(id: String, count: Int): ItemStack {
        val key = try {
            if (id.contains(":")) Identifier.parse(id)
            else Identifier.fromNamespaceAndPath("minecraft", id.trim())
        } catch (_: Exception) {
            return ItemStack.EMPTY
        }

        val holder = BuiltInRegistries.ITEM.get(key).orElse(null) ?: return ItemStack.EMPTY
        val item = holder.value()
        if (item === net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY

        val max = item.defaultMaxStackSize
        return ItemStack(item, count.coerceIn(1, max))
    }
}