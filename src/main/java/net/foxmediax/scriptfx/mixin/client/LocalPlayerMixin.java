package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CutsceneClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void scriptfx$lockMovement(CallbackInfo ci) {
        if (!CutsceneClient.INSTANCE.getActive() || !CutsceneClient.INSTANCE.getLocked()) return;

        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setDeltaMovement(Vec3.ZERO);
        self.setJumping(false);
        self.resetFallDistance();   // или self.fallDistance = 0f;
    }
}