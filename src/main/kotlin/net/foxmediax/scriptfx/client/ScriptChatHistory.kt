package net.foxmediax.scriptfx.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import java.util.ArrayDeque

object ScriptChatHistory {

    const val LOG_TAG = "scriptfx"
    private val TAG = GuiMessageTag(0, null, null, LOG_TAG)

    data class AvatarEntry(val contentKey: String, val png: ByteArray)

    private val recent = ArrayDeque<AvatarEntry>()
    private const val MAX = 80

    fun add(component: Component) = add(component, ByteArray(0))

    fun add(component: Component, avatarPng: ByteArray) {
        val chat = Minecraft.getInstance().gui.chat
        try {
            val m = ChatComponent::class.java.getDeclaredMethod(
                "addMessage",
                Component::class.java,
                MessageSignature::class.java,
                GuiMessageSource::class.java,
                GuiMessageTag::class.java
            )
            m.isAccessible = true
            m.invoke(chat, component, null, GuiMessageSource.SYSTEM_CLIENT, TAG)
        } catch (t: Throwable) {
            try {
                chat.addClientSystemMessage(component)
            } catch (_: Throwable) {
            }
        }

        if (avatarPng.isNotEmpty()) {
            val key = normalize(component.string)
            synchronized(recent) {
                recent.addLast(AvatarEntry(key, avatarPng.copyOf()))
                while (recent.size > MAX) recent.removeFirst()
            }
            // временно для отладки — потом можно убрать
            net.foxmediax.scriptfx.scriptengine.ScriptFXLog.info(
                "ScriptChatHistory: avatar saved key='$key' bytes=${avatarPng.size}"
            )
        }
    }

    fun avatarFor(contentString: String): ByteArray? {
        val key = normalize(contentString)
        synchronized(recent) {
            // точное совпадение
            recent.lastOrNull { it.contentKey == key }?.let { return it.png }
            // частичное: ключ содержится в строке сообщения или наоборот
            recent.lastOrNull {
                key.contains(it.contentKey) || it.contentKey.contains(key)
            }?.let { return it.png }
        }
        return null
    }

    fun clearAvatars() {
        synchronized(recent) { recent.clear() }
    }

    private fun normalize(s: String): String =
        s.replace('\n', ' ').trim().replace(Regex("\\s+"), " ")
}