package net.foxmediax.scriptfx.server

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.TriggerManager
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult

object ScriptFXServer : ModInitializer {

    private var tickCounter = 0L
    private var cachedServer: MinecraftServer? = null

    override fun onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            cachedServer = server
            ScriptManager.reload()
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            cachedServer = server
            tickCounter++
            ScriptManager.tickAll(tickCounter)
            TriggerManager.onServerTick(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            TriggerManager.onPlayerJoin(server, handler.player)
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            val server = cachedServer ?: return@register
            if (entity is ServerPlayer) {
                TriggerManager.onPlayerDeath(server, entity)
            }
        }

        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            val server = cachedServer ?: return@register
            TriggerManager.onPlayerRespawn(server, newPlayer)
        }

        // trigger_globalplay player_click "id_предмета" — срабатывает при использовании предмета (ПКМ).
        UseItemCallback.EVENT.register { player, world, hand ->
            if (!world.isClientSide && player is ServerPlayer) {
                val server = player.level().server
                val stack = player.getItemInHand(hand)
                val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
                TriggerManager.onPlayerClick(server, player, itemId)
            }
            InteractionResult.PASS
        }
    }
}