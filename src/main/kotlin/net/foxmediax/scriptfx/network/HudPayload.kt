package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class HudPayload(
    val action: String,
    val hudId: String,
    val elementId: String = "",
    val text: String = ""
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val ON = "on"
        const val OFF = "off"
        const val OFF_ALL = "off_all"
        const val SET = "set"   // динамический текст элемента

        val TYPE: CustomPacketPayload.Type<HudPayload> =
            CustomPacketPayload.Type(ScriptFX.id("hud"))

        val CODEC: StreamCodec<ByteBuf, HudPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HudPayload::action,
            ByteBufCodecs.STRING_UTF8, HudPayload::hudId,
            ByteBufCodecs.STRING_UTF8, HudPayload::elementId,
            ByteBufCodecs.STRING_UTF8, HudPayload::text,
            ::HudPayload
        )
    }
}