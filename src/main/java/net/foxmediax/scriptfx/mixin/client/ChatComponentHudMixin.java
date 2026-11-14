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

    /** Режим CENTER: ванильный HUD-чат выключен. История (allMessages) остаётся. */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void scriptfx$hideVanillaHudChat(GuiMessage message, CallbackInfo ci) {
        if (ScriptFXConfig.INSTANCE.getMessageMode() == MessageDisplayMode.CENTER) {
            ci.cancel();
        }
    }

    /**
     * Системные сообщения:
     * - centerSystemMessages = false → полностью не показываем (ни overlay, ни история);
     * - centerSystemMessages = true + CENTER → overlay + запись в историю.
     */
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void scriptfx$mirrorSystemMessage(
            Component content,
            MessageSignature signature,
            GuiMessageSource source,
            GuiMessageTag tag,
            CallbackInfo ci
    ) {
        if (ScriptFXConfig.INSTANCE.getMessageMode() != MessageDisplayMode.CENTER) return;

        // сообщения игроков — через CHAT
        if (source == GuiMessageSource.PLAYER) return;

        // сообщения скриптов уже идут своим путём
        if (tag != null && ScriptChatHistory.LOG_TAG.equals(tag.logTag())) return;

        // настройка выключена → не пишем в историю и не показываем
        if (!ScriptFXConfig.INSTANCE.getCenterSystemMessages()) {
            ci.cancel();
            return;
        }

        CenterMessageOverlay.INSTANCE.receiveSystem(content.getString());
        // не cancel — сообщение попадёт в allMessages для окна чата
    }
}