package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Сервер → клиент: какую клавишу ждать для npc_interact_key. */
data class NpcInteractListenPayload(val key: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcInteractListenPayload> =
            CustomPacketPayload.Type(ScriptFX.id("npc_interact_listen"))

        val CODEC: StreamCodec<ByteBuf, NpcInteractListenPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcInteractListenPayload::key,
            ::NpcInteractListenPayload
        )
    }
}