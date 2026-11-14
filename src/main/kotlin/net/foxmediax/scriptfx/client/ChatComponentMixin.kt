package net.foxmediax.scriptfx.client

import net.minecraft.client.multiplayer.chat.GuiMessageTag
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ChatComponent::class)
class ChatComponentMixin {

    @Inject(method = ["addMessage(Lnet/minecraft/network/chat/Component;)V"],
        at = [At("HEAD")], cancellable = true)
    private fun scriptfxFilterSimple(msg: Component, ci: CallbackInfo) {
        if (!ScriptFXChat.showSystemMessages && ScriptFXChat.isScriptFX(msg)) ci.cancel()
    }

    @Inject(
        method = ["addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"],
        at = [At("HEAD")], cancellable = true
    )
    private fun scriptfxFilterSigned(
        msg: Component, sig: MessageSignature?, tag: GuiMessageTag?, ci: CallbackInfo
    ) {
        if (!ScriptFXChat.showSystemMessages && ScriptFXChat.isScriptFX(msg)) ci.cancel()
    }

    // Если в 26.1 есть перегрузка с ChatType.Bound — добавьте её, но в текущем логе
    // достаточно двух выше. Если сообщения всё равно проходят, раскомментируйте:
    /*
    @Inject(
        method = ["addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType$Bound;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;Lnet/minecraft/network/chat/MessageSignature;)V"],
        at = [At("HEAD")], cancellable = true
    )
    private fun scriptfxFilterBound(
        msg: Component, type: ChatType.Bound?, tag: GuiMessageTag?,
        sig: MessageSignature?, ci: CallbackInfo
    ) {
        if (!ScriptFXChat.showSystemMessages && ScriptFXChat.isScriptFX(msg)) ci.cancel()
    }
    */
}