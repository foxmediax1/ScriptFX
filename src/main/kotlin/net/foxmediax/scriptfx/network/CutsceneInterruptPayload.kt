package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Клиент → сервер: игрок нажал Ctrl+Alt+End */
data class CutsceneInterruptPayload(
    val unused: Boolean = true
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<CutsceneInterruptPayload> =
            CustomPacketPayload.Type(ScriptFX.id("cutscene_interrupt"))

        val CODEC: StreamCodec<ByteBuf, CutsceneInterruptPayload> =
            StreamCodec.unit(CutsceneInterruptPayload())
    }
}