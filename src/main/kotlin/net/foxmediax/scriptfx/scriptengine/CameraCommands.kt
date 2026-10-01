package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.CameraPayload
import net.minecraft.server.MinecraftServer

/** Команды камеры: cameraINEffect / cameraOUTEffect / cameraBIGText / cameraSMALLText. */
object CameraCommands {

    private const val DEFAULT_FADE = "1.sec"
    private const val DEFAULT_TEXT = "3.sec"
    private const val SMALL_FLAG = "+smalltext"

    fun install(register: (String, CommandHandler) -> Unit) {

        // cameraINEffect "1.sec" — плавно затемняет экран и держит чёрным до cameraOUTEffect.
        register("cameraINEffect") { context, args ->
            val ticks = durationTicks(args.getOrNull(0) ?: DEFAULT_FADE, min = 0)
            send(context, CameraPayload(CameraPayload.FADE_IN, "", ticks, false))
            CommandResult.Continue
        }

        // cameraOUTEffect — плавно убирает чёрный экран (время берётся из cameraINEffect).
        register("cameraOUTEffect") { context, _ ->
            send(context, CameraPayload(CameraPayload.FADE_OUT, "", -1, false))
            CommandResult.Continue
        }

        // cameraBIGText "текст" "3.sec" [+smalltext]
        register("cameraBIGText") { context, args ->
            val text = args.getOrNull(0)
            if (text.isNullOrBlank()) {
                ScriptFXLog.warn("cameraBIGText: не указан текст")
                return@register CommandResult.Continue
            }
            val rest = args.drop(1)
            val joinSmall = rest.any { it.contains(SMALL_FLAG, ignoreCase = true) }
            val timeArg = rest
                .map { it.replace(SMALL_FLAG, "", ignoreCase = true).trim() }
                .firstOrNull { it.isNotEmpty() }
                ?: DEFAULT_TEXT

            send(context, CameraPayload(CameraPayload.BIG, text, durationTicks(timeArg, min = 1), joinSmall))
            CommandResult.Continue
        }

        // cameraSMALLText "текст" ["3.sec"] — после BIG с +smalltext время не нужно.
        register("cameraSMALLText") { context, args ->
            val text = args.getOrNull(0)
            if (text.isNullOrBlank()) {
                ScriptFXLog.warn("cameraSMALLText: не указан текст")
                return@register CommandResult.Continue
            }
            val ticks = durationTicks(args.getOrNull(1) ?: DEFAULT_TEXT, min = 1)
            send(context, CameraPayload(CameraPayload.SMALL, text, ticks, false))
            CommandResult.Continue
        }
    }

    /** Сбрасывает все эффекты камеры у всех игроков (чёрный экран, тексты). */
    fun reset(server: MinecraftServer) {
        val payload = CameraPayload(CameraPayload.RESET, "", 0, false)
        for (player in server.playerList.players) {
            if (ServerPlayNetworking.canSend(player, CameraPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }

    private fun durationTicks(raw: String, min: Int): Int =
        CommandRegistry.parseDurationTicks(raw).toInt().coerceIn(min, 20 * 600)

    private fun send(context: ScriptContext, payload: CameraPayload) {
        val targets = context.player?.let { listOf(it) } ?: context.server.playerList.players
        for (player in targets) {
            if (ServerPlayNetworking.canSend(player, CameraPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }
}