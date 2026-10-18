package net.foxmediax.scriptfx.scriptengine

import net.foxmediax.scriptfx.quest.QuestManager
import net.minecraft.server.level.ServerPlayer

/** Команды квестов для скриптов. Действуют на игрока скрипта (как команды NPC). */
object QuestCommands {

    fun install(register: (String, CommandHandler) -> Unit) {

        // quest_start "квест"
        register("quest_start") { ctx, args ->
            withQuest(ctx, args) { p, id -> QuestManager.start(p, id) }
        }

        // quest_progress "квест" "цель" [число]   (по умолчанию +1, можно отрицательное)
        register("quest_progress") { ctx, args ->
            val objective = clean(args.getOrNull(1))
            val amount = args.getOrNull(2)?.toIntOrNull() ?: 1
            withQuest(ctx, args) { p, id ->
                if (objective.isEmpty()) {
                    ScriptFXLog.warn("quest_progress: не указана цель")
                    false
                } else QuestManager.progress(p, id, objective, amount)
            }
        }

        // quest_complete "квест"
        register("quest_complete") { ctx, args ->
            withQuest(ctx, args) { p, id -> QuestManager.complete(p, id) }
        }

        // quest_fail "квест"
        register("quest_fail") { ctx, args ->
            withQuest(ctx, args) { p, id -> QuestManager.fail(p, id) }
        }

        // quest_status "квест" -> переменная quest_status: none / active / completed / failed
        register("quest_status") { ctx, args ->
            val p = playerOf(ctx)
            val id = clean(args.getOrNull(0))
            if (p != null && id.isNotEmpty()) ctx.vars["quest_status"] = QuestManager.statusOf(p, id)
            CommandResult.Continue
        }
    }

    private fun withQuest(
        ctx: ScriptContext,
        args: List<String>,
        action: (ServerPlayer, String) -> Boolean
    ): CommandResult {
        val player = playerOf(ctx)
        val id = clean(args.getOrNull(0))
        if (player == null || id.isEmpty()) {
            ScriptFXLog.warn("quest_*: нужен игрок и id квеста")
            return CommandResult.Continue
        }
        action(player, id)
        return CommandResult.Continue
    }

    private fun playerOf(ctx: ScriptContext): ServerPlayer? =
        ctx.player ?: ctx.server.playerList.players.firstOrNull()

    private fun clean(s: String?): String = s.orEmpty().removeSurrounding("\"").trim()
}