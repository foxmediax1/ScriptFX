package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Все действия системы катсцен (сервер → клиент).
 *
 * action:
 *   START, END, SET, MOVE, LOCK, HUD, LETTERBOX, INTERRUPT_ACK
 */
data class CutscenePayload(
    val action: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val durationTicks: Int = 0,      // для MOVE
    val flag: Boolean = false,       // true/false для LOCK/HUD/LETTERBOX
    val value: Float = 0f,           // высота letterbox (0.0–0.4)
    val worldKey: String = ""        // overworld / nether / end (пока не используется на клиенте)
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val START = "start"
        const val END = "end"
        const val SET = "set"
        const val MOVE = "move"
        const val LOCK = "lock"
        const val HUD = "hud"
        const val LETTERBOX = "letterbox"
        const val INTERRUPT_ACK = "interrupt_ack"
        const val PATH_POINT = "path_point"
        const val FOV = "fov"
        const val LOOKAT = "lookat"

        val TYPE: CustomPacketPayload.Type<CutscenePayload> =
            CustomPacketPayload.Type(ScriptFX.id("cutscene"))

        val CODEC: StreamCodec<ByteBuf, CutscenePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CutscenePayload::action,
            ByteBufCodecs.DOUBLE, CutscenePayload::x,
            ByteBufCodecs.DOUBLE, CutscenePayload::y,
            ByteBufCodecs.DOUBLE, CutscenePayload::z,
            ByteBufCodecs.FLOAT, CutscenePayload::yaw,
            ByteBufCodecs.FLOAT, CutscenePayload::pitch,
            ByteBufCodecs.VAR_INT, CutscenePayload::durationTicks,
            ByteBufCodecs.BOOL, CutscenePayload::flag,
            ByteBufCodecs.FLOAT, CutscenePayload::value,
            ByteBufCodecs.STRING_UTF8, CutscenePayload::worldKey,
            ::CutscenePayload
        )
    }
}