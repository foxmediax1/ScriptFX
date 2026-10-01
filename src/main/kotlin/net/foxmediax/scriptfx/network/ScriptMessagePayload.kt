package net.foxmediax.scriptfx.network

import io.netty.buffer.ByteBuf
import net.foxmediax.scriptfx.ScriptFX
import net.foxmediax.scriptfx.scriptengine.AvatarLoader
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Сообщение скрипта (print / printNPC), сервер -> клиент.
 * speaker — пустая строка, если это обычный print.
 * avatar — PNG-байты, пустой массив = без аватарки.
 * remark — ремарка в скобках после имени ("кричит"), может быть пустой.
 */
data class ScriptMessagePayload(
    val text: String,
    val speaker: String,
    val speakerColor: String,
    val textColor: String,
    val avatar: ByteArray = ByteArray(0),
    val remark: String = ""
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    /** Вид для обычного чата: [Имя] [ремарка]: текст */
    fun toComponent(): Component {
        val textFormat = ChatFormatting.getByName(textColor) ?: ChatFormatting.WHITE
        val body = Component.literal(text).withStyle(textFormat)
        if (speaker.isEmpty()) return body

        val speakerFormat = ChatFormatting.getByName(speakerColor) ?: ChatFormatting.WHITE
        val tag = if (remark.isBlank()) "[$speaker]" else "[$speaker] [$remark]"
        return Component.literal("$tag: ").withStyle(speakerFormat).append(body)
    }

    companion object {
        val TYPE: CustomPacketPayload.Type<ScriptMessagePayload> =
            CustomPacketPayload.Type(ScriptFX.id("script_message"))

        val CODEC: StreamCodec<ByteBuf, ScriptMessagePayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ScriptMessagePayload::text,
            ByteBufCodecs.STRING_UTF8, ScriptMessagePayload::speaker,
            ByteBufCodecs.STRING_UTF8, ScriptMessagePayload::speakerColor,
            ByteBufCodecs.STRING_UTF8, ScriptMessagePayload::textColor,
            ByteBufCodecs.byteArray(AvatarLoader.MAX_BYTES), ScriptMessagePayload::avatar,
            ByteBufCodecs.STRING_UTF8, ScriptMessagePayload::remark,
            ::ScriptMessagePayload
        )
    }
}