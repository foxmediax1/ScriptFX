package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.config.MessageDisplayMode;
import net.foxmediax.scriptfx.config.ScriptFXConfig;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentHudMixin {

    /**
     * В режиме CENTER сообщения игроков показываются по центру,
     * поэтому на HUD-чат (trimmedMessages) их не добавляем.
     * В истории (allMessages) они остаются, окно чата их показывает.
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void scriptfx$skipPlayerMessagesOnHud(GuiMessage message, CallbackInfo ci) {
        if (ScriptFXConfig.INSTANCE.getMessageMode() == MessageDisplayMode.CENTER
                && message.source() == GuiMessageSource.PLAYER) {
            ci.cancel();
        }
    }
}