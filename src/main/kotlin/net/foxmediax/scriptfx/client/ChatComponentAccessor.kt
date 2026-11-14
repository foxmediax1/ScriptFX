package net.foxmediax.scriptfx.client

import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.client.gui.components.ChatComponent
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(ChatComponent::class)
interface ChatComponentAccessor {

    @Accessor("allMessages")
    fun scriptfxGetAllMessages(): MutableList<GuiMessage>

    @Accessor("trimmedMessages")
    fun scriptfxGetTrimmedMessages(): MutableList<GuiMessage.Line>
}