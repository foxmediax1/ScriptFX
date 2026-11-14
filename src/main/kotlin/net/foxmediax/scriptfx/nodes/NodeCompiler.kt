package net.foxmediax.scriptfx.nodes

import net.foxmediax.scriptfx.scriptengine.ScriptCommand

/**
 * Обход графа → список ScriptCommand под CommandRegistry.
 * Пауза: ticktime (1.sec / 20.tick / 500.ms)
 * Камера: camera_set / camera_move
 * Катсцена: cutscene_start / cutscene_end
 * Квесты: quest_start / quest_complete / quest_fail
 */
object NodeCompiler {

    fun compile(graph: NodeGraph): List<ScriptCommand> {
        val out = mutableListOf<ScriptCommand>()
        val visited = mutableSetOf<String>()
        val start = graph.startNode() ?: return out
        walk(graph, start.id, out, visited, 1)
        return out
    }

    private fun walk(
        graph: NodeGraph,
        nodeId: String,
        out: MutableList<ScriptCommand>,
        visited: MutableSet<String>,
        line: Int
    ): Int {
        if (nodeId in visited) return line
        visited.add(nodeId)
        val node = graph.node(nodeId) ?: return line
        var ln = line

        when (node.kind) {
            NodeKind.START -> { /* no-op */ }

            NodeKind.END -> {
                out.add(ScriptCommand("stopscript", emptyList(), ln++))
                return ln
            }

            NodeKind.WAIT, NodeKind.DELAY -> {
                out.add(ScriptCommand("ticktime", listOf(durationOf(node)), ln++))
            }

            NodeKind.COMMAND -> {
                val name = node.params["cmd"]?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val args = splitArgs(node.params["args"].orEmpty())
                    out.add(ScriptCommand(name, args, ln++))
                }
            }

            NodeKind.SCRIPT -> {
                val script = node.params["script"]?.trim().orEmpty()
                if (script.isNotEmpty()) {
                    val cont = node.params["continue"]?.equals("true", ignoreCase = true) == true
                    out.add(
                        ScriptCommand(
                            if (cont) "continue_startscript" else "startscript",
                            listOf(script),
                            ln++
                        )
                    )
                }
            }

            NodeKind.QUEST_START -> {
                val q = questId(node)
                if (q.isNotEmpty()) out.add(ScriptCommand("quest_start", listOf(q), ln++))
            }

            NodeKind.QUEST_COMPLETE -> {
                val q = questId(node)
                if (q.isNotEmpty()) out.add(ScriptCommand("quest_complete", listOf(q), ln++))
            }

            NodeKind.QUEST_FAIL -> {
                val q = questId(node)
                if (q.isNotEmpty()) out.add(ScriptCommand("quest_fail", listOf(q), ln++))
            }

            NodeKind.CUTSCENE_START -> {
                val mode = node.params["mode"]?.lowercase()?.takeIf { it == "all" || it == "player" }
                    ?: "player"
                out.add(ScriptCommand("cutscene_start", listOf(mode), ln++))
            }

            NodeKind.CUTSCENE_END -> {
                out.add(ScriptCommand("cutscene_end", emptyList(), ln++))
            }

            NodeKind.CUTSCENE_SET -> {
                // camera_set x y z yaw pitch [world]
                val args = mutableListOf(
                    node.params["x"] ?: "0",
                    node.params["y"] ?: "0",
                    node.params["z"] ?: "0",
                    node.params["yaw"] ?: "0",
                    node.params["pitch"] ?: "0"
                )
                node.params["world"]?.takeIf { it.isNotBlank() }?.let { args.add(it) }
                out.add(ScriptCommand("camera_set", args, ln++))
            }

            NodeKind.CUTSCENE_MOVE -> {
                // camera_move x y z yaw pitch duration
                out.add(
                    ScriptCommand(
                        "camera_move",
                        listOf(
                            node.params["x"] ?: "0",
                            node.params["y"] ?: "0",
                            node.params["z"] ?: "0",
                            node.params["yaw"] ?: "0",
                            node.params["pitch"] ?: "0",
                            durationOf(node)
                        ),
                        ln++
                    )
                )
            }

            NodeKind.BRANCH -> {
                // if var op value  …  }
                val v = node.params["var"] ?: "flag"
                val op = node.params["op"] ?: "=="
                val value = node.params["value"] ?: "1"
                out.add(ScriptCommand("if", listOf(v, op, value), ln++))
                graph.outgoing(nodeId, "true").forEach { e ->
                    ln = walk(graph, e.to, out, visited, ln)
                }
                out.add(ScriptCommand("}", emptyList(), ln++))
                graph.outgoing(nodeId, "false").forEach { e ->
                    ln = walk(graph, e.to, out, visited, ln)
                }
                return ln
            }
        }

        graph.outgoing(nodeId, "out").forEach { e ->
            ln = walk(graph, e.to, out, visited, ln)
        }
        graph.outgoing(nodeId, "next").forEach { e ->
            ln = walk(graph, e.to, out, visited, ln)
        }
        return ln
    }

    /** "40" → "40.tick", иначе как есть ("1.sec", "20.tick", "500.ms"). */
    private fun durationOf(node: GraphNode): String {
        val raw = node.params["duration"]
            ?: node.params["ticks"]
            ?: "1.sec"
        val t = raw.trim()
        if (t.toLongOrNull() != null) return "$t.tick"
        if (t.toDoubleOrNull() != null && !t.contains('.')) return "$t.tick"
        // "1.5" без единицы — секунды по умолчанию Durations (пустой суффикс = sec)
        return t
    }

    private fun questId(node: GraphNode): String =
        (node.params["quest"] ?: node.params["questId"] ?: "").trim().removeSurrounding("\"")

    private fun splitArgs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                (c == ' ' || c == '\t') && !inQuotes -> {
                    if (cur.isNotEmpty()) {
                        tokens.add(cur.toString())
                        cur.clear()
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotEmpty()) tokens.add(cur.toString())
        return tokens
    }
}