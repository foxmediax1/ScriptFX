package net.foxmediax.scriptfx.scriptengine

import net.minecraft.ChatFormatting
import net.minecraft.world.level.Level
import net.minecraft.world.entity.Relative

typealias CommandHandler = (ScriptContext, List<String>) -> CommandResult

object CommandRegistry {

    private val handlers = mutableMapOf<String, CommandHandler>()

    private val DURATION_RE = Regex("""^(\d+(?:\.\d+)?)(?:\.([a-z]+))?$""", RegexOption.IGNORE_CASE)

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
            ScriptMessenger.send(context, text)
            ScriptFXLog.info("print: $text")
            CommandResult.Continue
        }

        // printNPC ["аватар.png"] "текст" "цвет имени" "имя" "цвет текста" "ремарка"
        // Всё, кроме текста, необязательно. Аватар определяется по окончанию .png в первом аргументе.
        register("printNPC") { context, args ->
            val hasAvatar = args.getOrNull(0)?.endsWith(".png", ignoreCase = true) == true
            val avatar = if (hasAvatar) args[0] else ""
            val rest = if (hasAvatar) args.drop(1) else args

            val text = rest.getOrNull(0) ?: ""
            val nameColor = rest.getOrNull(1)?.let { ChatFormatting.getByName(it) } ?: ChatFormatting.WHITE
            val name = rest.getOrNull(2) ?: "NPC"
            val textColor = rest.getOrNull(3)?.let { ChatFormatting.getByName(it) } ?: ChatFormatting.WHITE
            val remark = rest.getOrNull(4) ?: ""

            ScriptMessenger.send(context, text, name, nameColor, textColor, avatar, remark)
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

        // Команды-триггеры (checkpoint / worldstartscript / trigger_globalplay) регистрируются
        // при загрузке скриптов (TriggerParser). При обычном запуске скрипта они ничего не делают.
        TriggerParser.TRIGGER_NAMES.forEach { name ->
            register(name) { _, _ -> CommandResult.Continue }
        }

        // weather rain | thunder | clear — меняет погоду (можно ставить на любой строке).
        register("weather") { context, args ->
            val kind = args.getOrNull(0)?.lowercase()
            if (kind != "rain" && kind != "thunder" && kind != "clear") {
                ScriptFXLog.warn("weather: ожидалось rain, thunder или clear, получено '${args.getOrNull(0)}'")
                return@register CommandResult.Continue
            }
            runSilently(context, "weather $kind")
            CommandResult.Continue
        }

        // timecycles day | night | число тиков — меняет время суток (можно ставить на любой строке).
        register("timecycles") { context, args ->
            val raw = args.getOrNull(0)?.lowercase()
            val ticks = when (raw) {
                "day" -> 1000L
                "night" -> 13000L
                else -> raw?.toLongOrNull()?.takeIf { it >= 0 }
            }
            if (ticks == null) {
                ScriptFXLog.warn("timecycles: ожидалось day, night или число тиков, получено '${args.getOrNull(0)}'")
                return@register CommandResult.Continue
            }
            runSilently(context, "time set $ticks")
            CommandResult.Continue
        }

        // Команды камеры: cameraINEffect / cameraOUTEffect / cameraBIGText / cameraSMALLText
        CameraCommands.install { name, handler -> register(name, handler) }

        CutsceneCommands.install { name, handler -> register(name, handler) }
    }

    fun resolveLevel(server: net.minecraft.server.MinecraftServer, worldKey: String) =
        when (worldKey.lowercase()) {
            "overworld" -> server.overworld()
            "nether" -> server.getLevel(Level.NETHER)
            "end" -> server.getLevel(Level.END)
            else -> null
        }

    /** Выполняет ванильную команду от имени сервера, не показывая сообщения операторам в чате. */
    private fun runSilently(context: ScriptContext, command: String) {
        val source = context.server.createCommandSourceStack().withSuppressedOutput()
        context.server.commands.performPrefixedCommand(source, command)
    }

    /** "1.sec" / "20.tick" / "500.ms" -> число серверных тиков (20 тиков = 1 секунда). */
    fun parseDurationTicks(raw: String): Long =
        Durations.parseTicks(raw) ?: run {
            ScriptFXLog.warn("Некорректная длительность '$raw', использую 1.sec")
            20L
        }
}