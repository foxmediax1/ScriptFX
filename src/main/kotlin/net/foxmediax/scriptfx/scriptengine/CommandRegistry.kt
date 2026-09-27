package net.foxmediax.scriptfx.scriptengine

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.entity.Relative

typealias CommandHandler = (ScriptContext, List<String>) -> CommandResult

object CommandRegistry {

    private val handlers = mutableMapOf<String, CommandHandler>()

    init { registerDefaults() }

    fun register(name: String, handler: CommandHandler) {
        handlers[name] = handler
    }

    fun isKnown(name: String): Boolean = handlers.containsKey(name)

    fun execute(command: ScriptCommand, context: ScriptContext): CommandResult {
        val handler = handlers[command.name] ?: run {
            ScriptFXLog.warn("Неизвестная команда '${command.name}' (строка ${command.lineNumber})")
            return CommandResult.Continue
        }
        return handler(context, command.args)
    }

    private fun registerDefaults() {

        register("print") { context, args ->
            val text = args.joinToString(" ")
            val message = Component.literal(text)
            context.player?.sendSystemMessage(message) ?: broadcastToAll(context, message)
            ScriptFXLog.info("print: $text")
            CommandResult.Continue
        }

        // printNPC "текст" "имя_или_id_нпс" "цвет" — цвет и имя необязательны
        register("printNPC") { context, args ->
            val text = args.getOrNull(0) ?: ""
            val name = args.getOrNull(1) ?: "NPC"
            val color = args.getOrNull(2)?.let { ChatFormatting.getByName(it) } ?: ChatFormatting.WHITE

            val message = Component.literal("[$name] ").withStyle(color).append(Component.literal(text))
            context.player?.sendSystemMessage(message) ?: broadcastToAll(context, message)
            ScriptFXLog.info("printNPC[$name]: $text")
            CommandResult.Continue
        }

        register("ticktime") { _, args ->
            CommandResult.Wait(parseDurationTicks(args.getOrNull(0) ?: "1.sec"))
        }

        register("stopscript") { _, _ -> CommandResult.Stop }

        register("continue_startscript") { _, args ->
            val name = args.getOrNull(0)?.substringBeforeLast(".") ?: return@register CommandResult.Stop
            CommandResult.ContinueWith(name)
        }

        register("startscript") { context, args ->
            args.getOrNull(0)?.substringBeforeLast(".")?.let { ScriptManager.startScript(it, context) }
            CommandResult.Continue
        }

        register("startCommand") { context, args ->
            val command = (args.getOrNull(0) ?: return@register CommandResult.Continue).removePrefix("/")
            context.server.commands.performPrefixedCommand(context.server.createCommandSourceStack(), command)
            CommandResult.Continue
        }

        register("tp_allplayers") { context, args ->
            val x = args.getOrNull(0)?.toDoubleOrNull() ?: return@register CommandResult.Continue
            val y = args.getOrNull(1)?.toDoubleOrNull() ?: return@register CommandResult.Continue
            val z = args.getOrNull(2)?.toDoubleOrNull() ?: return@register CommandResult.Continue
            val level = resolveLevel(context.server, args.getOrNull(3) ?: "overworld")
                ?: return@register CommandResult.Continue

            context.server.playerList.players.forEach { player ->
                player.teleportTo(level, x, y, z, emptySet<Relative>(), player.yRot, player.xRot, true)
            }
            CommandResult.Continue
        }

        // TODO: cameraINEffect / cameraOUTEffect / cameraBIGText / cameraSMALLText —
        // отдельный шаг: свой S2C-пакет + рендер оверлея на клиенте.
        // TODO: checkpoint / worldstartscript / trigger_globalplay / weather / timecycles —
        // регистрация через Fabric-события (следующий шаг).
    }

    private fun broadcastToAll(context: ScriptContext, message: Component) {
        context.server.playerList.players.forEach { it.sendSystemMessage(message) }
    }

    fun resolveLevel(server: net.minecraft.server.MinecraftServer, worldKey: String) =
        when (worldKey.lowercase()) {
            "overworld" -> server.overworld()
            "nether" -> server.getLevel(Level.NETHER)
            "end" -> server.getLevel(Level.END)
            else -> null
        }

    /** "1.sec" / "20.tick" / "500.ms" -> число серверных тиков (20 тиков = 1 секунда). */
    private fun parseDurationTicks(raw: String): Long {
        val parts = raw.split(".")
        val amount = parts.getOrNull(0)?.toDoubleOrNull() ?: 1.0
        return when (parts.getOrNull(1)?.lowercase()) {
            "tick", "ticks", "t" -> amount.toLong()
            "ms", "millis" -> (amount / 50).toLong().coerceAtLeast(1)
            else -> (amount * 20).toLong()
        }
    }
}