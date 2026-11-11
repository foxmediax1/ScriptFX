package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.hud.HudRegistry
import net.foxmediax.scriptfx.network.HudPayload
import net.minecraft.server.level.ServerPlayer

object HudCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        register("hud_on") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("hud_on: нужен ID_HUD")
                return@register CommandResult.Continue
            }
            HudRegistry.reload()
            if (HudRegistry.get(id) == null) {
                ScriptFXLog.warn("hud_on: HUD '$id' не найден (projects/*/hud/*.json)")
                return@register CommandResult.Continue
            }
            broadcast(ctx, HudPayload(HudPayload.ON, id))
            CommandResult.Continue
        }

        register("hud_off") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("hud_off: нужен ID_HUD")
                return@register CommandResult.Continue
            }
            broadcast(ctx, HudPayload(HudPayload.OFF, id))
            CommandResult.Continue
        }

        register("hud_off_all") { ctx, _ ->
            broadcast(ctx, HudPayload(HudPayload.OFF_ALL, ""))
            CommandResult.Continue
        }
    }

    private fun broadcast(ctx: ScriptContext, payload: HudPayload) {
        val players: Collection<ServerPlayer> =
            ctx.player?.let { listOf(it) } ?: ctx.server.playerList.players
        for (p in players) {
            if (ServerPlayNetworking.canSend(p, HudPayload.TYPE)) {
                ServerPlayNetworking.send(p, payload)
            }
        }
    }
}