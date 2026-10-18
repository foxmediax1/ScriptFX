package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Клиент сообщает: ролик id закончился (не loop). */
data class MediaEndPayload(
    val videoId: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<MediaEndPayload> =
            CustomPacketPayload.Type(ScriptFX.id("media_end"))

        val CODEC: StreamCodec<ByteBuf, MediaEndPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MediaEndPayload::videoId,
            ::MediaEndPayload
        )
    }
}