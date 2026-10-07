package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.npc.NpcDialogWait
import net.foxmediax.scriptfx.npc.NpcInteractWait
import net.foxmediax.scriptfx.npc.NpcManager
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.minecraft.server.level.ServerLevel
import net.foxmediax.scriptfx.npc.NpcRuntime

object NpcCommands {
    fun install(register: (String, CommandHandler) -> Unit) {

        register("npc_spawn") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"") ?: return@register CommandResult.Continue
            val x = args.getOrNull(1)?.toDoubleOrNull()
            val y = args.getOrNull(2)?.toDoubleOrNull()
            val z = args.getOrNull(3)?.toDoubleOrNull()
            val anim = args.getOrNull(4)?.removeSurrounding("\"") ?: "idle"
            val mode = args.getOrNull(5)?.removeSurrounding("\"") ?: "interact"
            if (x == null || y == null || z == null) {
                ScriptFXLog.warn("npc_spawn: нужны id x y z [anim] [mode]")
                return@register CommandResult.Continue
            }
            val level = (ctx.player?.level() ?: ctx.server.overworld()) as ServerLevel
            NpcManager.spawn(level, id, x, y, z, anim, mode)
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

            // --- камера к NPC ---
            val level = serverPlayer.level() as ServerLevel
            val runtime = NpcRuntime.get(npcId)
            val npcEntity = runtime?.let { level.getEntity(it.entityUuid) } as? ScriptNpcEntity

            if (npcEntity != null) {
                if (!CutsceneManager.isInCutscene(serverPlayer)) {
                    CutsceneManager.start(ctx.server, "player", listOf(serverPlayer))
                    CutsceneManager.setLock(serverPlayer, true)
                    CutsceneManager.setHud(serverPlayer, true)
                }
                val t = 0.55
                val cx = serverPlayer.x + (npcEntity.x - serverPlayer.x) * t
                val cy = npcEntity.eyeY
                val cz = serverPlayer.z + (npcEntity.z - serverPlayer.z) * t
                val yaw = (kotlin.math.atan2(
                    -(npcEntity.x - cx),
                    npcEntity.z - cz
                ) * (180.0 / Math.PI)).toFloat()
                CutsceneManager.setCamera(serverPlayer, cx, cy, cz, yaw, 0f, "")
                CutsceneManager.setFov(serverPlayer, 40f, 8)
            } else {
                ScriptFXLog.warn("npc_dialog_hud: NPC '$npcId' не найден в мире для камеры")
            }

            NpcDialogWait.begin(serverPlayer.uuid)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                serverPlayer,
                net.foxmediax.scriptfx.network.NpcDialogOpenPayload(
                    npcId, text,
                    buttons[0], buttons[1], buttons[2], buttons[3], buttons[4]
                )
            )

            val uuid = serverPlayer.uuid
            CommandResult.WaitUntil {
                if (NpcDialogWait.isDone(uuid)) {
                    val btn = NpcDialogWait.chosen(uuid)
                    ctx.vars["dialog_button"] = btn.toString()
                    NpcDialogWait.clear(uuid)
                    ScriptFXLog.info("dialog_button = $btn")
                    true
                } else false
            }
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
}