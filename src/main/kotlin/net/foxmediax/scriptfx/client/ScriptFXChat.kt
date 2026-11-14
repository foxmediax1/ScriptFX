package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.client.chat.ChatCleaner
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

object ScriptFXChat {

    const val MARKER = "scriptfx:system"

    @Volatile
    var showSystemMessages: Boolean = true
        set(value) {
            field = value
            if (!value) ChatCleaner.clearFromChat()
        }

    fun wrap(msg: Component): Component =
        Component.empty()
            .setStyle(Style.EMPTY.withInsertion(MARKER))
            .append(msg)

    fun isScriptFX(msg: Component): Boolean =
        msg.style.insertion == MARKER
}