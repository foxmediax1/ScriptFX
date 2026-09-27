package net.foxmediax.scriptfx.mixin;

import net.foxmediax.scriptfx.scriptengine.TriggerManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ловит любую телепортацию игрока (команды /tp, порталы, tp_allplayers и т.д.)
 * для "trigger_globalplay player_tp".
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerTeleportMixin {

    // Если Mixin ругнётся на неоднозначность перегрузок teleportTo(...),
    // укажи точную JVM-сигнатуру, например:
    // method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)V"
    @Inject(method = "teleportTo", at = @At("TAIL"))
    private void scriptfx$onTeleport(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        MinecraftServer server = self.level().getServer();
        TriggerManager.INSTANCE.onPlayerTeleport(server, self);
    }
}