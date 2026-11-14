package net.foxmediax.scriptfx.client.chat

import net.foxmediax.scriptfx.client.ScriptFXChat
import net.foxmediax.scriptfx.client.ChatComponentAccessor
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft

object ChatCleaner {

    fun clearFromChat() {
        val mc = Minecraft.getInstance()
        val chat = mc.gui?.chat ?: return
        val accessor = chat as? ChatComponentAccessor ?: run {
            ScriptFXLog.warn("ChatCleaner: ChatComponentAccessor not applied")
            return
        }
        try {
            // allMessages: content — Component, можно проверить маркер.
            accessor.scriptfxGetAllMessages().removeIf { ScriptFXChat.isScriptFX(it.content) }

            // trimmedMessages: content — FormattedCharSequence, маркер не проверить.
            // Очищаем весь список, чтобы убрать визуальные строки.
            accessor.scriptfxGetTrimmedMessages().clear()
        } catch (t: Throwable) {
            ScriptFXLog.warn("ChatCleaner: ${t.message}")
        }
    }
}