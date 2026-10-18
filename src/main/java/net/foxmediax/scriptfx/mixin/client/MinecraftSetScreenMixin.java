package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.mui.MuiScreens;
import net.foxmediax.scriptfx.config.MessageDisplayMode;
import net.foxmediax.scriptfx.config.ScriptFXConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftSetScreenMixin {

    @Unique
    private static boolean scriptfx$replacingChat = false;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void scriptfx$replaceChatScreen(Screen screen, CallbackInfo ci) {
        if (scriptfx$replacingChat) return;
        if (!(screen instanceof ChatScreen)) return;
        if (ScriptFXConfig.INSTANCE.getMessageMode() != MessageDisplayMode.CENTER) return;

        ci.cancel();
        scriptfx$replacingChat = true;
        try {
            MuiScreens.openCenteredChat();
        } finally {
            scriptfx$replacingChat = false;
        }
    }
}