package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Клиент → сервер: выбрана кнопка 1..5. */
data class NpcDialogChoicePayload(val button: Int) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcDialogChoicePayload> =
            CustomPacketPayload.Type(ScriptFX.id("npc_dialog_choice"))

        val CODEC: StreamCodec<ByteBuf, NpcDialogChoicePayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcDialogChoicePayload::button,
            ::NpcDialogChoicePayload
        )
    }
}