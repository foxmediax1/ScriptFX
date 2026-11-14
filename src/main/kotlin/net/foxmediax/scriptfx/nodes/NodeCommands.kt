package net.foxmediax.scriptfx.nodes

import net.foxmediax.scriptfx.scriptengine.CommandHandler
import net.foxmediax.scriptfx.scriptengine.CommandResult
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.foxmediax.scriptfx.scriptengine.ScriptManager

object NodeCommands {

    fun install(register: (String, CommandHandler) -> Unit) {
        register("graph_run") { ctx, args ->
            val id = args.getOrNull(0)?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("graph_run: укажи id графа")
                return@register CommandResult.Continue
            }
            ScriptManager.startGraph(id, ctx)
            CommandResult.Continue
        }

        register("graph_reload") { _, _ ->
            NodeGraphRegistry.reload()
            ScriptFXLog.info("Node graphs reloaded (${NodeGraphRegistry.totalCount()})")
            CommandResult.Continue
        }

        register("graph_stop") { _, args ->
            val id = args.getOrNull(0)?.trim().orEmpty()
            if (id.isEmpty()) {
                ScriptFXLog.warn("graph_stop: укажи id графа")
                return@register CommandResult.Continue
            }
            val n = ScriptManager.stopScript("graph:$id", "graph_stop")
            ScriptFXLog.info("graph_stop '$id': остановлено $n")
            CommandResult.Continue
        }
    }
}