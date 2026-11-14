package net.foxmediax.scriptfx.client

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component

/**
 * Запись сообщений скриптов (print / printNPC) в историю ванильного чата.
 * На HUD они не показываются (см. ChatComponentHudMixin), но видны в окне чата.
 */
object ScriptChatHistory {

    /** Метка, по которой миксин отличает наши сообщения. */
    const val LOG_TAG = "scriptfx"

    private val TAG = GuiMessageTag(0, null, null, LOG_TAG)

    /** Вызывать из основного потока клиента. */
    fun add(component: Component) {
        val chat = Minecraft.getInstance().gui.chat
        ((chat as Any) as ChatComponentAccessor)
    }
}