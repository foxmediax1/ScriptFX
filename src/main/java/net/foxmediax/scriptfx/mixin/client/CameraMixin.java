package net.foxmediax.scriptfx.mixin.client;

import net.foxmediax.scriptfx.client.CutsceneClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /**
     * После того как ваниль выставила позицию/поворот по сущности —
     * перезаписываем их нашими значениями катсцены.
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void scriptfx$overrideCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        CutsceneClient cutscene = CutsceneClient.INSTANCE;
        if (!cutscene.getActive()) return;

        cutscene.sample();

        setPosition(cutscene.getCamX(), cutscene.getCamY(), cutscene.getCamZ());
        setRotation(cutscene.getCamYaw(), cutscene.getCamPitch());

        Float fov = cutscene.getFovOverride();
        if (fov != null) {
            CameraFovAccessor acc = (CameraFovAccessor) (Object) this;
            acc.setFov(fov);
            acc.setHudFov(fov);
        }
    }
}