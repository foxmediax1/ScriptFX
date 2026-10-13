package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.network.MediaPayload
import net.minecraft.server.level.ServerPlayer

object MediaCommands {

    private val savedModes =
        java.util.concurrent.ConcurrentHashMap<java.util.UUID, net.minecraft.world.level.GameType>()

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
            var x = 0f;
            var y = 0f;
            var w = 0f;
            var h = 0f
            val a1 = args.getOrNull(1)?.lowercase()
            if (a1 != null && a1 != "fullscreen" && args.size >= 5) {
                x = args[1].toFloatOrNull() ?: 0f
                y = args[2].toFloatOrNull() ?: 0f
                w = args[3].toFloatOrNull() ?: 1f
                h = args[4].toFloatOrNull() ?: 1f
            }
            broadcast(ctx, MediaPayload.videoPlay(id, x, y, w, h, preview = false))
            for (p in targets(ctx)) enterSpectator(p)
            CommandResult.Continue
        }

        register("video_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || id.equals("all", true)) {
                broadcast(ctx, MediaPayload.videoStopAll())
            } else {
                broadcast(ctx, MediaPayload.videoStop(id))
                for (p in targets(ctx)) leaveSpectator(p)
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

    fun onVideoEnded(player: net.minecraft.server.level.ServerPlayer, videoId: String) {
        val id = videoId.trim()
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                player, net.foxmediax.scriptfx.network.MediaPayload.TYPE
            )
        ) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                if (id.isEmpty()) net.foxmediax.scriptfx.network.MediaPayload.videoStopAll()
                else net.foxmediax.scriptfx.network.MediaPayload.videoStop(id)
            )
        }
        leaveSpectator(player)
        ScriptFXLog.info("Media: video ended '$id' by ${player.scoreboardName}")
    }

    private fun enterSpectator(p: net.minecraft.server.level.ServerPlayer) {
        if (savedModes.containsKey(p.uuid)) return
        // 26.1: подставь актуальное получение режима, если gameMode() другое
        val current = try {
            p.gameMode().let { gm ->
                // часто: gm.gameModeForPlayer
                val m = gm.javaClass.methods.firstOrNull {
                    it.name == "getGameModeForPlayer" || it.name == "getGameMode" || it.name == "gameModeForPlayer"
                }
                when {
                    m != null && m.parameterCount == 0 ->
                        m.invoke(gm) as net.minecraft.world.level.GameType

                    else -> net.minecraft.world.level.GameType.SURVIVAL
                }
            }
        } catch (_: Throwable) {
            net.minecraft.world.level.GameType.SURVIVAL
        }
        savedModes[p.uuid] = current
        p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
        ScriptFXLog.info("Media: ${p.scoreboardName} → SPECTATOR (was $current)")
    }

    fun leaveSpectator(p: net.minecraft.server.level.ServerPlayer) {
        val prev = savedModes.remove(p.uuid) ?: return
        p.setGameMode(prev)
        ScriptFXLog.info("Media: ${p.scoreboardName} → $prev (restore)")
    }

    fun onPlayerLeave(p: net.minecraft.server.level.ServerPlayer) {
        leaveSpectator(p)
    }

    private fun targets(ctx: ScriptContext): List<ServerPlayer> {
        val one = ctx.player
        if (one != null) return listOf(one)
        return ctx.server.playerList.players.toList()
    }
}