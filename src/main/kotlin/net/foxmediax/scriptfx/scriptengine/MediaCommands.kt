package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.media.MediaRegistry
import net.foxmediax.scriptfx.media.Mp4Decoder
import net.foxmediax.scriptfx.network.MediaPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MediaCommands {

    private val savedModes = ConcurrentHashMap<UUID, GameType>()

    /** videoId -> игроки, которые ещё не прислали MediaEnd (т.е. ещё смотрят). */
    private val pendingVideoEnds = ConcurrentHashMap<String, MutableSet<UUID>>()

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

        // video_play <id> [x y w h|fullscreen]
        // video_play <id> [x y w h|fullscreen]
        // Скрипт стоппится до конца ролика (все целевые игроки прислали MediaEnd).
        register("video_play") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("video_play: нужен id (папка video/<id>/)")
                return@register CommandResult.Continue
            }

            MediaRegistry.reload()
            val meta = MediaRegistry.video(id)
            if (meta == null) {
                ScriptFXLog.warn("video_play: '$id' не найден в projects/*/video/")
                return@register CommandResult.Continue
            }

            var x = 0f
            var y = 0f
            var w = 0f
            var h = 0f
            val a1 = args.getOrNull(1)?.lowercase()
            if (a1 != null && a1 != "fullscreen" && args.size >= 5) {
                x = args[1].toFloatOrNull() ?: 0f
                y = args[2].toFloatOrNull() ?: 0f
                w = args[3].toFloatOrNull() ?: 1f
                h = args[4].toFloatOrNull() ?: 1f
            }

            // Декодируем в фоне, чтобы не фризить серверный тик
            val future = Mp4Decoder.ensureDecodedAsync(meta)
            if (!future.isDone) {
                ScriptFXLog.info("video_play '$id': декодируется в фоне (ffmpeg)...")
            }

            var sent = false
            var failed = false

            CommandResult.WaitUntil {
                // 1) Ждём ffmpeg
                if (!future.isDone) {
                    return@WaitUntil false
                }

                // 2) Один раз отправляем видео клиентам
                if (!sent) {
                    sent = true

                    val decoded = try {
                        future.get()
                    } catch (t: Throwable) {
                        ScriptFXLog.warn("video_play '$id': ошибка future: ${t.message}")
                        null
                    }

                    if (decoded == null) {
                        ScriptFXLog.warn("video_play '$id': декодирование не удалось (проверьте ffmpeg)")
                        failed = true
                        return@WaitUntil true
                    }

                    val players = targets(ctx)
                    if (players.isEmpty()) {
                        ScriptFXLog.warn("video_play '$id': нет игроков — пропускаю ожидание")
                        failed = true
                        return@WaitUntil true
                    }

                    // Запоминаем зрителей: скрипт ждёт, пока набор опустеет
                    pendingVideoEnds[id] = players.map { it.uuid }.toMutableSet()

                    broadcast(ctx, MediaPayload.videoPlay(id, x, y, w, h, preview = false))

                    for (p in players) enterSpectator(p)

                    ScriptFXLog.info(
                        "video_play '$id' запущено для ${players.size} игрок(ов), " +
                                "скрипт ждёт окончания"
                    )
                }

                if (failed) return@WaitUntil true

                // 3) Ждём, пока все досмотрят (или video_stop / уход игроков очистят pending)
                val waiting = pendingVideoEnds[id]
                val done = waiting == null || waiting.isEmpty()
                if (done) {
                    // на всякий случай убрать пустой set
                    pendingVideoEnds.remove(id)
                    ScriptFXLog.info("video_play '$id': просмотр завершён, скрипт продолжает")
                }
                done
            }
        }

        // video_stop [id|all]
        register("video_stop") { ctx, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty() || id.equals("all", true)) {
                broadcast(ctx, MediaPayload.videoStopAll())
                pendingVideoEnds.clear()
                for (p in targets(ctx)) leaveSpectator(p)
            } else {
                broadcast(ctx, MediaPayload.videoStop(id))
                pendingVideoEnds.remove(id)
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

    /**
     * Клиент прислал MediaEnd. Стоп шлём только когда ВСЕ зрители этого видео досмотрели.
     */
    fun onVideoEnded(player: ServerPlayer, videoId: String) {
        val id = videoId.trim()
        val waiting = pendingVideoEnds[id]
        if (waiting != null) {
            if (!waiting.remove(player.uuid)) {
                // повторный/неизвестный пакет — игнорируем
                return
            }
            if (waiting.isNotEmpty()) {
                ScriptFXLog.info("Media: '$id' досмотрел ${player.scoreboardName}, ждём ещё ${waiting.size}")
                leaveSpectator(player)
                return
            }
            pendingVideoEnds.remove(id)
        }

        // все досмотрели (или видео шло без учёта) — шлём стоп всем
        if (ServerPlayNetworking.canSend(player, MediaPayload.TYPE)) {
            ServerPlayNetworking.send(
                player,
                if (id.isEmpty()) MediaPayload.videoStopAll() else MediaPayload.videoStop(id)
            )
        }
        leaveSpectator(player)
        ScriptFXLog.info("Media: video ended '$id' by ${player.scoreboardName}")
    }

    /** Вызывается при SERVER_STOPPING: чистим состояние. */
    fun onServerStopping() {
        pendingVideoEnds.clear()
        savedModes.clear()
    }

    private fun enterSpectator(p: ServerPlayer) {
        if (savedModes.containsKey(p.uuid)) return
        val current = try {
            p.gameMode().let { gm ->
                val m = gm.javaClass.methods.firstOrNull {
                    it.name == "getGameModeForPlayer" || it.name == "getGameMode" || it.name == "gameModeForPlayer"
                }
                when {
                    m != null && m.parameterCount == 0 -> m.invoke(gm) as GameType
                    else -> GameType.SURVIVAL
                }
            }
        } catch (_: Throwable) {
            GameType.SURVIVAL
        }
        savedModes[p.uuid] = current
        p.setGameMode(GameType.SPECTATOR)
        ScriptFXLog.info("Media: ${p.scoreboardName} → SPECTATOR (was $current)")
    }

    fun leaveSpectator(p: ServerPlayer) {
        val prev = savedModes.remove(p.uuid) ?: return
        p.setGameMode(prev)
        ScriptFXLog.info("Media: ${p.scoreboardName} → $prev (restore)")
    }

    fun onPlayerLeave(p: ServerPlayer) {
        // игрок ушёл во время видео: считаем, что «досмотрел», чтобы не висеть в pending
        val it = pendingVideoEnds.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.value.remove(p.uuid)
            if (e.value.isEmpty()) {
                it.remove()
                ScriptFXLog.info("Media: pending '${e.key}' очищен (игроки вышли)")
            }
        }
        leaveSpectator(p)
    }

    private fun targets(ctx: ScriptContext): List<ServerPlayer> {
        val one = ctx.player
        if (one != null) return listOf(one)
        return ctx.server.playerList.players.toList()
    }
}