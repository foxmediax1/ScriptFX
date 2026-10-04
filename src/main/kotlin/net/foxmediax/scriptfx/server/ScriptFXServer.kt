package net.foxmediax.scriptfx.server

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.TriggerManager
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.InteractionResult
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.CameraCommands
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.CutsceneInterruptPayload
import net.foxmediax.scriptfx.scriptengine.CutsceneManager

object ScriptFXServer : ModInitializer {

    private var tickCounter = 0L
    private var cachedServer: MinecraftServer? = null

    override fun onInitialize() {
        registerCommands()

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            cachedServer = server
            ScriptManager.reload()
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            cachedServer = server
            tickCounter++
            ScriptManager.tickAll(tickCounter)
            TriggerManager.onServerTick(server)
            CutsceneManager.tick(server)
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            TriggerManager.onPlayerJoin(server, handler.player)
        }

        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            val server = cachedServer ?: return@register
            if (entity is ServerPlayer) {
                CutsceneManager.abort(entity)
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

        ServerPlayNetworking.registerGlobalReceiver(CutsceneInterruptPayload.TYPE) { _, context ->
            val player = context.player()
            CutsceneManager.onInterrupt(player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            CutsceneManager.endFor(handler.player)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            CutsceneManager.endAll(server)   // иначе игрок сохранится в точке камеры
        }

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            !(entity is ServerPlayer && CutsceneManager.isInCutscene(entity))
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            CutsceneManager.endFor(handler.player)
            TriggerManager.onPlayerLeave(handler.player.uuid)
        }
    }

    /** /scriptfx stop_script "имя скрипта.sfxs" */
    /** /scriptfx start_script, stop_script, stop_all_scripts */
    private fun registerCommands() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("scriptfx")
                    .requires { it.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                    .then(
                        Commands.literal("start_script")
                            .then(
                                Commands.argument("name", StringArgumentType.string())
                                    .executes { ctx ->
                                        startScript(ctx.source, StringArgumentType.getString(ctx, "name"))
                                    }
                            )
                    )
                    .then(
                        Commands.literal("stop_script")
                            .then(
                                Commands.argument("name", StringArgumentType.string())
                                    .executes { ctx ->
                                        stopScript(ctx.source, StringArgumentType.getString(ctx, "name"))
                                    }
                            )
                    )
                    .then(
                        Commands.literal("stop_all_scripts")
                            .executes { ctx -> stopAllScripts(ctx.source) }
                    )
                    .then(
                        Commands.literal("camera_reset")
                            .executes { ctx -> cameraReset(ctx.source) }
                        
                    )
                    .then(
                        Commands.literal("reload")
                            .executes { ctx ->
                                val server = ctx.source.server
                                ScriptManager.stopAllScripts()
                                CameraCommands.reset(server)
                                CutsceneManager.endAll(server)
                                ScriptManager.reload()
                                ctx.source.sendSuccess(
                                    { Component.literal("Скрипты ScriptFX перезагружены") },
                                    true
                                )
                                1
                            }
                    )
            )
        }
    }

    private fun cameraReset(source: CommandSourceStack): Int {
        CameraCommands.reset(source.server)
        source.sendSuccess({ Component.literal("Эффекты камеры сброшены") }, true)
        return 1
    }

    private fun stopScript(source: CommandSourceStack, rawName: String): Int {
        val name = rawName.trim().removeSuffix(".sfxs")
        val stopped = ScriptManager.stopScript(name)
        when {
            stopped > 0 ->
                source.sendSuccess({ Component.literal("Скрипт '$name' аварийно завершён (экземпляров: $stopped)") }, true)
            ScriptManager.loadedScript(name) != null ->
                source.sendFailure(Component.literal("Скрипт '$name' сейчас не запущен"))
            else ->
                source.sendFailure(Component.literal("Скрипт '$name' не найден"))
        }
        return stopped
    }

    private fun startScript(source: CommandSourceStack, rawName: String): Int {
        val name = rawName.trim().removeSuffix(".sfxs")
        if (ScriptManager.loadedScript(name) == null) {
            source.sendFailure(Component.literal("Скрипт '$name' не найден"))
            return 0
        }
        return if (ScriptManager.startScript(name, ScriptContext(source.server))) {
            source.sendSuccess({ Component.literal("Скрипт '$name' запущен") }, true)
            1
        } else {
            source.sendFailure(Component.literal("Скрипт '$name' не запущен: достигнут лимит одновременных скриптов"))
            0
        }
    }

    private fun stopAllScripts(source: CommandSourceStack): Int {
        CameraCommands.reset(source.server)
        CutsceneManager.endAll(source.server)
        val stopped = ScriptManager.stopAllScripts()
        if (stopped > 0) {
            source.sendSuccess({ Component.literal("Аварийно завершено скриптов: $stopped") }, true)
        } else {
            source.sendFailure(Component.literal("Сейчас нет запущенных скриптов"))
        }
        return stopped
        ScriptManager.stopAllScripts("reload")
    }
}

