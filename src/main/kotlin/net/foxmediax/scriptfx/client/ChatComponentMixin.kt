package net.foxmediax.scriptfx.mixin.client

import net.foxmediax.scriptfx.client.ScriptFXChat
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ChatComponent::class)
class ChatComponentMixin {

    /**
     * 26.1.2: addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)
     * Скрываем системные сообщения ScriptFX, если showSystemMessages = false.
     */
    @Inject(
        method = ["addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun scriptfxFilter(
        content: Component,
        signature: MessageSignature?,
        source: GuiMessageSource?,
        tag: GuiMessageTag?,
        ci: CallbackInfo
    ) {
        if (!ScriptFXChat.showSystemMessages && ScriptFXChat.isScriptFX(content)) {
            ci.cancel()
        }
    }
}