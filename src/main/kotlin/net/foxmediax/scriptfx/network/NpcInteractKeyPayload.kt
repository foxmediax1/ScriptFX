package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Клиент → сервер: нажата клавиша взаимодействия с NPC. */
data class NpcInteractKeyPayload(val key: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcInteractKeyPayload> =
            CustomPacketPayload.Type(ScriptFX.id("npc_interact_key"))

        val CODEC: StreamCodec<ByteBuf, NpcInteractKeyPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcInteractKeyPayload::key,
            ::NpcInteractKeyPayload
        )
    }
}