package net.foxmediax.scriptfx.mixin.client;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Window.class)
public interface WindowAccessor {

    @Accessor("handle")
    long getHandle();   // ← без $ и без scriptfx
}