package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CutsceneClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void scriptfx$lockMouse(CallbackInfo ci) {
        if (CutsceneClient.INSTANCE.getActive() && CutsceneClient.INSTANCE.getLocked()) {
            ci.cancel();
        }
    }
}