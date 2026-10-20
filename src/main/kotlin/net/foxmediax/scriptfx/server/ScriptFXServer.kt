package net.foxmediax.scriptfx.server

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.foxmediax.scriptfx.network.CutsceneInterruptPayload
import net.foxmediax.scriptfx.network.MediaEndPayload
import net.foxmediax.scriptfx.network.NpcDialogChoicePayload
import net.foxmediax.scriptfx.network.NpcInteractKeyPayload
import net.foxmediax.scriptfx.npc.NpcDialogWait
import net.foxmediax.scriptfx.npc.NpcInteractWait
import net.foxmediax.scriptfx.npc.NpcManager
import net.foxmediax.scriptfx.npc.NpcRuntime
import net.foxmediax.scriptfx.npc.ScriptNpcEntity
import net.foxmediax.scriptfx.quest.QuestManager
import net.foxmediax.scriptfx.scriptengine.CameraCommands
import net.foxmediax.scriptfx.scriptengine.CutsceneManager
import net.foxmediax.scriptfx.scriptengine.MediaCommands
import net.foxmediax.scriptfx.scriptengine.ScriptContext
import net.foxmediax.scriptfx.scriptengine.ScriptManager
import net.foxmediax.scriptfx.scriptengine.TriggerManager
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.InteractionResult

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
            QuestManager.onJoin(handler.player)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            CutsceneManager.endFor(handler.player)
            TriggerManager.onPlayerLeave(handler.player.uuid)
            QuestManager.onLeave(handler.player.uuid)
            MediaCommands.onPlayerLeave(handler.player)
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
            CutsceneManager.onInterrupt(context.player())
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            CutsceneManager.endAll(server)
            MediaCommands.onServerStopping()
        }

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            !(entity is ServerPlayer && CutsceneManager.isInCutscene(entity))
        }

        ServerPlayNetworking.registerGlobalReceiver(NpcInteractKeyPayload.TYPE) { payload, ctx ->
            NpcInteractWait.notifyKey(ctx.player().uuid, payload.key)
        }
        ServerPlayNetworking.registerGlobalReceiver(NpcDialogChoicePayload.TYPE) { payload, ctx ->
            NpcDialogWait.notifyChoice(ctx.player().uuid, payload.button)
        }
        ServerPlayNetworking.registerGlobalReceiver(MediaEndPayload.TYPE) { payload, context ->
            MediaCommands.onVideoEnded(context.player(), payload.videoId)
        }
    }

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
                    .then(
                        Commands.literal("stop_kill_npcs")
                            .executes { ctx: CommandContext<CommandSourceStack> ->
                                killAllNpcs(ctx.source)
                            }
                    )
                    .then(
                        Commands.literal("stop_killnpc")
                            .then(
                                Commands.argument("id", StringArgumentType.string())
                                    .executes { ctx: CommandContext<CommandSourceStack> ->
                                        killOneNpc(ctx)
                                    }
                            )
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
                source.sendSuccess(
                    { Component.literal("Скрипт '$name' аварийно завершён (экземпляров: $stopped)") },
                    true
                )
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
        val context = ScriptContext(source.server, source.player)
        return if (ScriptManager.startScript(name, context)) {
            source.sendSuccess({ Component.literal("Скрипт '$name' запущен") }, true)
            1
        } else {
            source.sendFailure(
                Component.literal("Скрипт '$name' не запущен: достигнут лимит одновременных скриптов")
            )
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
    }

    private fun killAllNpcs(source: CommandSourceStack): Int {
        val n = NpcManager.despawnAll(source.server)
        return if (n > 0) {
            source.sendSuccess({ Component.literal("Удалено NPC: $n") }, true)
            n
        } else {
            source.sendFailure(Component.literal("В мире нет ScriptFX NPC"))
            0
        }
    }

    private fun killOneNpc(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
            .trim()
            .removeSurrounding("\"")
        if (id.isEmpty()) {
            source.sendFailure(Component.literal("Укажи id NPC"))
            return 0
        }

        val server = source.server
        var removed = false

        if (NpcRuntime.isSpawned(id)) {
            for (level in server.allLevels) {
                if (level is ServerLevel && NpcManager.despawn(level, id)) {
                    removed = true
                    break
                }
            }
            NpcRuntime.remove(id)
        }

        for (level in server.allLevels) {
            if (level !is ServerLevel) continue
            val list = level.getAllEntities()
                .filterIsInstance<ScriptNpcEntity>()
                .filter { it.npcId == id }
            list.forEach { it.discard() }
            if (list.isNotEmpty()) removed = true
        }
        NpcRuntime.remove(id)

        return if (removed) {
            source.sendSuccess({ Component.literal("NPC '$id' удалён") }, true)
            1
        } else {
            source.sendFailure(Component.literal("NPC '$id' не найден в мире"))
            0
        }
    }
}