package net.foxmediax.scriptfx.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraFovAccessor {

    @Accessor("fov")
    void setFov(float fov);

    @Accessor("hudFov")
    void setHudFov(float hudFov);
}