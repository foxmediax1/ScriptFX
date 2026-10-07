package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CutsceneClient;
import net.foxmediax.scriptfx.client.NpcDialogClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    private static boolean scriptfx$shouldLock() {
        boolean cutscene = CutsceneClient.INSTANCE.getActive()
                && CutsceneClient.INSTANCE.getLocked();
        boolean dialog = NpcDialogClient.INSTANCE.isActive();
        return cutscene || dialog;
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void scriptfx$lockMovementHead(CallbackInfo ci) {
        if (!scriptfx$shouldLock()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setDeltaMovement(Vec3.ZERO);
        self.setJumping(false);
    }

    /** После aiStep — иначе WASD снова задаёт скорость. */
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void scriptfx$lockMovementTail(CallbackInfo ci) {
        if (!scriptfx$shouldLock()) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setDeltaMovement(Vec3.ZERO);
        self.setJumping(false);
        self.resetFallDistance();
        self.xxa = 0f;
        self.zza = 0f;
        self.yya = 0f;
    }
}