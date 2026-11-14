package net.foxmediax.scriptfx.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature

/**
 * Запись сообщений скриптов (print / printNPC) в историю ванильного чата.
 * На HUD они не показываются (см. ChatComponentHudMixin), но видны в окне чата (T).
 */
object ScriptChatHistory {

    /** Метка, по которой миксин отличает наши сообщения. */
    const val LOG_TAG = "scriptfx"

    private val TAG = GuiMessageTag(0, null, null, LOG_TAG)

    /** Вызывать из основного потока клиента. */
    fun add(component: Component) {
        val chat = Minecraft.getInstance().gui.chat
        try {
            // private void addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)
            val m = ChatComponent::class.java.getDeclaredMethod(
                "addMessage",
                Component::class.java,
                MessageSignature::class.java,
                GuiMessageSource::class.java,
                GuiMessageTag::class.java
            )
            m.isAccessible = true
            m.invoke(
                chat,
                component,
                null,
                GuiMessageSource.SYSTEM_CLIENT,
                TAG
            )
        } catch (t: Throwable) {
            // fallback без кастомного tag (может сработать дубль плашки — лучше не)
            try {
                chat.addClientSystemMessage(component)
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}