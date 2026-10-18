package net.foxmediax.scriptfx.scriptengine

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.ScriptMessagePayload
import net.minecraft.ChatFormatting

/** Отправка сообщений скриптов игрокам (серверная сторона). */
object ScriptMessenger {

    fun send(
        context: ScriptContext,
        text: String,
        speaker: String = "",
        speakerColor: ChatFormatting? = null,
        textColor: ChatFormatting? = null,
        avatarFile: String = "",
        remark: String = ""
    ) {
        val avatar = avatarFile.takeIf { it.isNotEmpty() }
            ?.let { AvatarLoader.load(it) }
            ?: ByteArray(0)

        val payload = ScriptMessagePayload(
            text, speaker, speakerColor?.name ?: "", textColor?.name ?: "", avatar, remark
        )
        val targets = context.player?.let { listOf(it) } ?: context.server.playerList.players

        for (player in targets) {
            if (ServerPlayNetworking.canSend(player, ScriptMessagePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload)
            } else {
                // У клиента нет мода — обычное сообщение в чат.
                player.sendSystemMessage(payload.toComponent())
            }
        }
    }
}