package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CenterMessageOverlay;
import net.foxmediax.scriptfx.client.ScriptChatHistory;
import net.foxmediax.scriptfx.config.MessageDisplayMode;
import net.foxmediax.scriptfx.config.ScriptFXConfig;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentHudMixin {

    /**
     * Режим CENTER: ванильный чат на HUD отключён полностью.
     * История (allMessages) при этом ведётся как обычно: её показывает окно чата.
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void scriptfx$hideVanillaHudChat(GuiMessage message, CallbackInfo ci) {
        if (ScriptFXConfig.INSTANCE.getMessageMode() == MessageDisplayMode.CENTER) {
            ci.cancel();
        }
    }

    /**
     * Каждое НОВОЕ сообщение чата (вызывается один раз на сообщение, в отличие от
     * addMessageToDisplayQueue, который повторяется при перестройке чата).
     * Системные сообщения дублируем плашкой по центру.
     */
    @Inject(method = "addMessage", at = @At("HEAD"))
    private void scriptfx$mirrorSystemMessage(Component content, MessageSignature signature,
                                              GuiMessageSource source, GuiMessageTag tag,
                                              CallbackInfo ci) {
        if (ScriptFXConfig.INSTANCE.getMessageMode() != MessageDisplayMode.CENTER) return;

        // сообщения игроков показывает событие CHAT (с головой и ником)
        if (source == GuiMessageSource.PLAYER) return;

        // сообщения скриптов уже показаны своим путём
        if (tag != null && ScriptChatHistory.LOG_TAG.equals(tag.logTag())) return;

        CenterMessageOverlay.INSTANCE.receiveSystem(content.getString());
    }
}