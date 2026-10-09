package net.foxmediax.scriptfx.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> scriptfx$getAllMessages();

    /** Приватный ChatComponent.addMessage: позволяет добавить сообщение со своей меткой. */
    @Invoker("addMessage")
    void scriptfx$addMessage(Component content, MessageSignature signature,
                             GuiMessageSource source, GuiMessageTag tag);
}