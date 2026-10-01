package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Эффекты камеры, сервер -> клиент.
 * action — одна из констант ниже; text — текст (для BIG/SMALL);
 * ticks — длительность в тиках (для FADE_OUT значение -1 = "как у FADE_IN");
 * flag — для BIG: "за мной идёт cameraSMALLText" (+smalltext).
 */
data class CameraPayload(
    val action: String,
    val text: String,
    val ticks: Int,
    val flag: Boolean
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val FADE_IN = "fade_in"
        const val FADE_OUT = "fade_out"
        const val BIG = "big"
        const val SMALL = "small"
        const val RESET = "reset"

        val TYPE: CustomPacketPayload.Type<CameraPayload> =
            CustomPacketPayload.Type(ScriptFX.id("camera_effect"))

        val CODEC: StreamCodec<ByteBuf, CameraPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CameraPayload::action,
            ByteBufCodecs.STRING_UTF8, CameraPayload::text,
            ByteBufCodecs.VAR_INT, CameraPayload::ticks,
            ByteBufCodecs.BOOL, CameraPayload::flag,
            ::CameraPayload
        )
    }
}