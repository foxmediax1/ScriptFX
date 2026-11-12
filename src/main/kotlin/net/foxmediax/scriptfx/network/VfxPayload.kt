package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клиент: кинематографические эффекты (VFXWeaver или fallback).
 * action: play | stop | shake | roll | letterbox | flash
 */
data class VfxPayload(
    val action: String,
    val effectId: String,
    val ticks: Int,
    val p0: Float,
    val p1: Float
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val PLAY = "play"
        const val STOP = "stop"
        const val SHAKE = "shake"
        const val ROLL = "roll"
        const val LETTERBOX = "letterbox"
        const val FLASH = "flash"

        val TYPE: CustomPacketPayload.Type<VfxPayload> =
            CustomPacketPayload.Type(ScriptFX.id("vfx"))

        val CODEC: StreamCodec<ByteBuf, VfxPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VfxPayload::action,
            ByteBufCodecs.STRING_UTF8, VfxPayload::effectId,
            ByteBufCodecs.VAR_INT, VfxPayload::ticks,
            ByteBufCodecs.FLOAT, VfxPayload::p0,
            ByteBufCodecs.FLOAT, VfxPayload::p1,
            ::VfxPayload
        )
    }
}