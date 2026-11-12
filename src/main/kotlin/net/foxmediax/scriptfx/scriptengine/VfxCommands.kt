package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.VfxPayload

/**
 * Скриптовые команды кинематографа (поверх CameraCommands).
 *
 * vfx "camera_shake" "1.sec"
 * vfx_stop "camera_shake"
 * cam_shake 0.3 "1.sec"
 * cam_roll "2.sec"
 * cam_letterbox "3.sec"
 * cam_flash "0.5.sec"
 */
object VfxCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        register("vfx") { context, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("vfx: укажи id эффекта, например camera_shake")
                return@register CommandResult.Continue
            }
            val ticks = durationTicks(args.getOrNull(1) ?: "1.sec")
            send(context, VfxPayload(VfxPayload.PLAY, id, ticks, 0f, 0f))
            CommandResult.Continue
        }

        register("vfx_stop") { context, args ->
            val id = args.getOrNull(0)?.removeSurrounding("\"")?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("vfx_stop: укажи id")
                return@register CommandResult.Continue
            }
            send(context, VfxPayload(VfxPayload.STOP, id, 0, 0f, 0f))
            CommandResult.Continue
        }

        register("cam_shake") { context, args ->
            // cam_shake [amplitude] [duration]
            val first = args.getOrNull(0)
            val amp: Float
            val timeArg: String
            if (first != null && first.toFloatOrNull() != null) {
                amp = first.toFloat()
                timeArg = args.getOrNull(1) ?: "1.sec"
            } else {
                amp = 0.25f
                timeArg = first ?: "1.sec"
            }
            val ticks = durationTicks(timeArg)
            send(context, VfxPayload(VfxPayload.SHAKE, "camera_shake", ticks, amp, amp))
            CommandResult.Continue
        }

        register("cam_roll") { context, args ->
            val ticks = durationTicks(args.getOrNull(0) ?: "2.sec")
            send(context, VfxPayload(VfxPayload.ROLL, "camera_roll", ticks, 0f, 0f))
            CommandResult.Continue
        }

        register("cam_letterbox") { context, args ->
            val ticks = durationTicks(args.getOrNull(0) ?: "3.sec")
            send(context, VfxPayload(VfxPayload.LETTERBOX, "letterbox", ticks, 0f, 0f))
            CommandResult.Continue
        }

        register("cam_flash") { context, args ->
            val ticks = durationTicks(args.getOrNull(0) ?: "0.5.sec")
            send(context, VfxPayload(VfxPayload.FLASH, "screen_flash", ticks, 0f, 0f))
            CommandResult.Continue
        }
    }

    private fun durationTicks(raw: String): Int =
        CommandRegistry.parseDurationTicks(raw.removeSurrounding("\"")).toInt().coerceIn(1, 20 * 600)

    private fun send(context: ScriptContext, payload: VfxPayload) {
        val targets = context.player?.let { listOf(it) } ?: context.server.playerList.players
        for (player in targets) {
            if (ServerPlayNetworking.canSend(player, VfxPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload)
            }
        }
    }
}