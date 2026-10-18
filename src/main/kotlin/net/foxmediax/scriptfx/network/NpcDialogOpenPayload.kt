package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сервер → клиент: открыть диалог.
 * buttons — до 5 строк, пустые в конце не шлём.
 */
data class NpcDialogOpenPayload(
    val npcId: String,
    val text: String,
    val button1: String,
    val button2: String,
    val button3: String,
    val button4: String,
    val button5: String,
    val npcEntityId: Int
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<NpcDialogOpenPayload> =
            CustomPacketPayload.Type(ScriptFX.id("npc_dialog_open"))

        val CODEC: StreamCodec<ByteBuf, NpcDialogOpenPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::npcId,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::text,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::button1,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::button2,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::button3,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::button4,
            ByteBufCodecs.STRING_UTF8, NpcDialogOpenPayload::button5,
            ByteBufCodecs.VAR_INT, NpcDialogOpenPayload::npcEntityId,
            ::NpcDialogOpenPayload
        )
    }
}