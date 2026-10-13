package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.network.MediaPayload
import net.minecraft.server.level.ServerPlayer

object MediaCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        // sound_play <id> [volume] [pitch] [loop|true]
        register("sound_play") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("sound_play: нужен id (файл audio/<id>.ogg)")
                return@register CommandResult.Continue
            }
            MediaRegistry.reload()
            if (MediaRegistry.sound(id) == null) {
                ScriptFXLog.warn("sound_play: '$id' не найден в projects/*/audio/")
                return@register CommandResult.Continue
            }
            val vol = args.getOrNull(1)?.toFloatOrNull() ?: 1f
            val pitch = args.getOrNull(2)?.toFloatOrNull() ?: 1f
            val loopRaw = args.getOrNull(3)?.lowercase()
            val loop = loopRaw == "true" || loopRaw == "loop" || loopRaw == "1"

            broadcast(ctx, MediaPayload.soundPlay(id, vol, pitch, loop))
            CommandResult.Continue
        }

        // sound_stop [id|all]
        register("sound_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || id.equals("all", ignoreCase = true)) {
                broadcast(ctx, MediaPayload.soundStopAll())
            } else {
                broadcast(ctx, MediaPayload.soundStop(id))
            }
            CommandResult.Continue
        }

        register("video_play") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("video_play: нужен id (папка video/<id>/)")
                return@register CommandResult.Continue
            }
            MediaRegistry.reload()
            if (MediaRegistry.video(id) == null) {
                ScriptFXLog.warn("video_play: '$id' не найден в projects/*/video/")
                return@register CommandResult.Continue
            }
            var x = 0f; var y = 0f; var w = 0f; var h = 0f
            val a1 = args.getOrNull(1)?.lowercase()
            if (a1 != null && a1 != "fullscreen" && args.size >= 5) {
                x = args[1].toFloatOrNull() ?: 0f
                y = args[2].toFloatOrNull() ?: 0f
                w = args[3].toFloatOrNull() ?: 1f
                h = args[4].toFloatOrNull() ?: 1f
            }
            broadcast(ctx, MediaPayload.videoPlay(id, x, y, w, h, preview = false))
            CommandResult.Continue
        }

        register("video_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || id.equals("all", true)) {
                broadcast(ctx, MediaPayload.videoStopAll())
            } else {
                broadcast(ctx, MediaPayload.videoStop(id))
            }
            CommandResult.Continue
        }
    }

    private fun broadcast(ctx: ScriptContext, payload: MediaPayload) {
        val players: Collection<ServerPlayer> =
            ctx.player?.let { listOf(it) } ?: ctx.server.playerList.players
        for (p in players) {
            if (ServerPlayNetworking.canSend(p, MediaPayload.TYPE)) {
                ServerPlayNetworking.send(p, payload)
            }
        }
    }
}