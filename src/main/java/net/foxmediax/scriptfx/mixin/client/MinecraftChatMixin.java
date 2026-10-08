package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CenteredChatScreen;
import net.foxmediax.scriptfx.config.MessageDisplayMode;
import net.foxmediax.scriptfx.config.ScriptFXConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftChatMixin {

    /** Флаг, чтобы не зациклиться при setScreen внутри setScreen. */
    private static boolean scriptfx$replacingChat = false;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void scriptfx$replaceChatScreen(Screen screen, CallbackInfo ci) {
        if (scriptfx$replacingChat) return;
        if (screen == null) return;

        // Подменяем только обычный ChatScreen, не наш CenteredChatScreen
        if (screen instanceof ChatScreen
                && !(screen instanceof CenteredChatScreen)
                && ScriptFXConfig.INSTANCE.getMessageMode() == MessageDisplayMode.CENTER) {

            scriptfx$replacingChat = true;
            try {
                Minecraft self = (Minecraft) (Object) this;
                // initial-текст из ванильного ChatScreen достать сложно без accessor —
                // для начала открываем пустой.
                self.setScreen(new CenteredChatScreen(""));
            } finally {
                scriptfx$replacingChat = false;
            }
            ci.cancel();
        }
    }
}