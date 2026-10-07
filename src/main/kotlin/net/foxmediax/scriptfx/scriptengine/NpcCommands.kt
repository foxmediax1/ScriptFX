package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.npc.NpcManager
import net.minecraft.server.level.ServerLevel

object NpcCommands {
    fun install(register: (String, CommandHandler) -> Unit) {

        // npc_spawn "id" x y z "anim" "mode"
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
    }
}