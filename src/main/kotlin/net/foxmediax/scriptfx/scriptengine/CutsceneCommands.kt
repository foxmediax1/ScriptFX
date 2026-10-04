package net.foxmediax.scriptfx.scriptengine

object CutsceneCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        // cutscene_start [player|all]
        register("cutscene_start") { context, args ->
            val mode = args.getOrNull(0)?.lowercase() ?: "player"
            if (mode != "player" && mode != "all") {
                ScriptFXLog.warn("cutscene_start: ожидалось player или all, получено '$mode'")
                return@register CommandResult.Continue
            }
            val targets = CutsceneManager.resolveTargets(context, mode)
            CutsceneManager.start(context.server, mode, targets)
            ScriptFXLog.info("Катсцена запущена (режим=$mode, игроков=${targets.size})")
            CommandResult.Continue
        }

        register("cutscene_end") { context, _ ->
            // Завершаем у всех, кто сейчас в катсцене (или только у контекста)
            val targets = context.player?.let { listOf(it) } ?: context.server.playerList.players
            CutsceneManager.end(context.server, targets)
            CommandResult.Continue
        }

        // camera_set x y z yaw pitch [world]
        register("camera_set") { context, args ->
            val x = args.getOrNull(0)?.toDoubleOrNull()
            val y = args.getOrNull(1)?.toDoubleOrNull()
            val z = args.getOrNull(2)?.toDoubleOrNull()
            val yaw = args.getOrNull(3)?.toFloatOrNull()
            val pitch = args.getOrNull(4)?.toFloatOrNull()
            if (x == null || y == null || z == null || yaw == null || pitch == null) {
                ScriptFXLog.warn("camera_set: нужны x y z yaw pitch")
                return@register CommandResult.Continue
            }
            val world = args.getOrNull(5) ?: ""
            val targets = resolveActive(context)
            for (p in targets) {
                CutsceneManager.setCamera(p, x, y, z, yaw, pitch, world)
            }
            CommandResult.Continue
        }

        // camera_move x y z yaw pitch duration
        register("camera_move") { context, args ->
            val x = args.getOrNull(0)?.toDoubleOrNull()
            val y = args.getOrNull(1)?.toDoubleOrNull()
            val z = args.getOrNull(2)?.toDoubleOrNull()
            val yaw = args.getOrNull(3)?.toFloatOrNull()
            val pitch = args.getOrNull(4)?.toFloatOrNull()
            val durationRaw = args.getOrNull(5) ?: "1.sec"
            if (x == null || y == null || z == null || yaw == null || pitch == null) {
                ScriptFXLog.warn("camera_move: нужны x y z yaw pitch duration")
                return@register CommandResult.Continue
            }
            val ticks = CommandRegistry.parseDurationTicks(durationRaw).toInt().coerceAtLeast(1)
            val targets = resolveActive(context)
            for (p in targets) {
                CutsceneManager.moveCamera(p, x, y, z, yaw, pitch, ticks)
            }
            // Ждём окончания движения, чтобы следующая команда шла после
            CommandResult.Wait(ticks.toLong())
        }

        // player_lock true|false
        register("player_lock") { context, args ->
            val value = args.getOrNull(0)?.equals("true", ignoreCase = true) == true
            val targets = resolveActive(context)
            for (p in targets) CutsceneManager.setLock(p, value)
            CommandResult.Continue
        }

        // hud_hide true|false
        register("hud_hide") { context, args ->
            val value = args.getOrNull(0)?.equals("true", ignoreCase = true) == true
            val targets = resolveActive(context)
            for (p in targets) CutsceneManager.setHud(p, value)
            CommandResult.Continue
        }

        // letterbox true|false [height]
        register("letterbox") { context, args ->
            val enabled = args.getOrNull(0)?.equals("true", ignoreCase = true) == true
            val height = args.getOrNull(1)?.toFloatOrNull() ?: 0.15f
            val targets = resolveActive(context)
            for (p in targets) CutsceneManager.setLetterbox(p, enabled, height)
            CommandResult.Continue
        }

        register("camera_path_start") { context, _ ->
            resolveActive(context).forEach { CutsceneManager.pathStart(it) }
            CommandResult.Continue
        }

        register("camera_path_point") { context, args ->
            val x = args.getOrNull(0)?.toDoubleOrNull()
            val y = args.getOrNull(1)?.toDoubleOrNull()
            val z = args.getOrNull(2)?.toDoubleOrNull()
            val yaw = args.getOrNull(3)?.toFloatOrNull()
            val pitch = args.getOrNull(4)?.toFloatOrNull()
            val dur = CommandRegistry.parseDurationTicks(args.getOrNull(5) ?: "1.sec").toInt()
            if (x == null || y == null || z == null || yaw == null || pitch == null) {
                ScriptFXLog.warn("camera_path_point: нужны x y z yaw pitch duration")
                return@register CommandResult.Continue
            }
            resolveActive(context).forEach {
                CutsceneManager.pathPoint(it, x, y, z, yaw, pitch, dur)
            }
            CommandResult.Continue
        }

        // camera_path_end [wait]
        register("camera_path_end") { context, args ->
            val total = resolveActive(context).maxOfOrNull { CutsceneManager.pathEnd(it) } ?: 0
            if (args.getOrNull(0).equals("wait", ignoreCase = true) && total > 0) {
                CommandResult.Wait(total.toLong())
            } else {
                CommandResult.Continue
            }
        }

        register("camera_fov") { context, args ->
            val fov = args.getOrNull(0)?.toFloatOrNull()
            if (fov == null) {
                ScriptFXLog.warn("camera_fov: нужно число (например 40)")
                return@register CommandResult.Continue
            }
            val dur = if (args.size > 1)
                CommandRegistry.parseDurationTicks(args[1]).toInt()
            else 0
            resolveActive(context).forEach { CutsceneManager.setFov(it, fov, dur) }
            if (dur > 0) CommandResult.Wait(dur.toLong()) else CommandResult.Continue
        }

        register("camera_lookat") { context, args ->
            val x = args.getOrNull(0)?.toDoubleOrNull()
            val y = args.getOrNull(1)?.toDoubleOrNull()
            val z = args.getOrNull(2)?.toDoubleOrNull()
            if (x == null || y == null || z == null) {
                ScriptFXLog.warn("camera_lookat: нужны x y z [duration]")
                return@register CommandResult.Continue
            }
            val dur = if (args.size > 3)
                CommandRegistry.parseDurationTicks(args[3]).toInt()
            else 0
            resolveActive(context).forEach { CutsceneManager.lookAt(it, x, y, z, dur) }
            if (dur > 0) CommandResult.Wait(dur.toLong()) else CommandResult.Continue
        }

        register("camera_lookat_entity") { context, args ->
            val name = args.getOrNull(0) ?: return@register CommandResult.Continue
            val dur = if (args.size > 1)
                CommandRegistry.parseDurationTicks(args[1]).toInt()
            else 0

            val target = if (name == "@s") context.player
            else context.server.playerList.getPlayerByName(name)

            if (target == null) {
                ScriptFXLog.warn("camera_lookat_entity: игрок '$name' не найден")
                return@register CommandResult.Continue
            }
            resolveActive(context).forEach {
                CutsceneManager.lookAt(it, target.x, target.eyeY, target.z, dur)
            }
            if (dur > 0) CommandResult.Wait(dur.toLong()) else CommandResult.Continue
        }
    }

    /** Кому применять команды камеры: только тем, кто сейчас в катсцене */
    private fun resolveActive(context: ScriptContext): List<net.minecraft.server.level.ServerPlayer> {
        val inCutscene = context.server.playerList.players
            .filter { CutsceneManager.isInCutscene(it) }
        val bound = context.player
        return if (bound != null) inCutscene.filter { it == bound } else inCutscene
    }
}