package net.foxmediax.scriptfx.mixin;

import net.foxmediax.scriptfx.scriptengine.TriggerManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportMixin {

    @Inject(
            method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
            at = @At("TAIL")
    )
    private void scriptfx$onTeleport(CallbackInfoReturnable<Boolean> cir) {
        // Срабатываем только если телепортация реально удалась.
        if (!cir.getReturnValueZ()) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        TriggerManager.INSTANCE.onPlayerTeleport(self.level().getServer(), self);
    }
}