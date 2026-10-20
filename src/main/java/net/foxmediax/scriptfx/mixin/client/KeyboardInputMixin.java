package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CutsceneClient;
import net.foxmediax.scriptfx.client.NpcDialogClient;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void scriptfx$zeroInput(CallbackInfo ci) {
        boolean lock = (CutsceneClient.INSTANCE.getActive() && CutsceneClient.INSTANCE.getLocked())
                || NpcDialogClient.INSTANCE.getLocksInput();
        if (!lock) return;
        // ClientInput / поля зависят от маппинга 26.1 — обнули forward/left/jump/shift
        ClientInput self = (ClientInput) (Object) this;
        // если есть:
        // self.leftImpulse = 0; self.forwardImpulse = 0;
        // self.keyPresses = new KeyPresses(false,false,false,false,false,false,false);
    }
}