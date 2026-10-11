package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Полный список квестов игрока (JSON), сервер -> клиент. */
data class QuestSyncPayload(val json: String) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<QuestSyncPayload> =
            CustomPacketPayload.Type(ScriptFX.id("quest_sync"))

        val CODEC: StreamCodec<ByteBuf, QuestSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(262144), QuestSyncPayload::json,
            ::QuestSyncPayload
        )
    }
}

/** Уведомление слева сверху. kind: started / updated / completed / failed. */
data class QuestToastPayload(
    val kind: String,
    val title: String,
    val text: String
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<QuestToastPayload> =
            CustomPacketPayload.Type(ScriptFX.id("quest_toast"))

        val CODEC: StreamCodec<ByteBuf, QuestToastPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, QuestToastPayload::kind,
            ByteBufCodecs.STRING_UTF8, QuestToastPayload::title,
            ByteBufCodecs.STRING_UTF8, QuestToastPayload::text,
            ::QuestToastPayload
        )
    }
}