package net.foxmediax.scriptfx.nodes

import net.foxmediax.scriptfx.scriptengine.ScriptCommand
import net.foxmediax.scriptfx.scriptengine.ScriptParser

/**
 * Двусторонняя связь .sfxs ↔ NodeGraph.
 *
 * Метаданные (после режима нодов → текст):
 *   # node connect("A") if ("B")
 *   # node pos("A") 120.0 160.0
 * При разборе текст → ноды connect/pos восстанавливают рёбра и координаты.
 */
object ScriptNodeSync {

    private val CONNECT_RE =
        Regex("""^\s*#\s*node\s+connect\("([^"]+)"\)\s+if\s+\("([^"]+)"\)\s*$""")

    private val POS_RE =
        Regex("""^\s*#\s*node\s+pos\("([^"]+)"\)\s+(\-?\d+(?:\.\d+)?)\s+(\-?\d+(?:\.\d+)?)\s*$""")

    fun defaultTemplateGraph(scriptName: String = "script"): NodeGraph {
        val g = NodeGraph(id = scriptName, title = scriptName)
        val start = GraphNode(
            id = "start",
            kind = NodeKind.START,
            title = "Начало скрипта",
            params = mutableMapOf("cmd" to "startscript", "args" to scriptName),
            x = 80f, y = 160f
        )
        val msg = GraphNode(
            id = "print1",
            kind = NodeKind.COMMAND,
            title = "Сообщение в чат",
            params = mutableMapOf("cmd" to "print", "args" to "Привет, мир!"),
            x = 320f, y = 160f
        )
        val end = GraphNode(
            id = "end",
            kind = NodeKind.END,
            title = "Конец скрипта",
            params = mutableMapOf("cmd" to "stopscript"),
            x = 560f, y = 160f
        )
        g.nodes.addAll(listOf(start, msg, end))
        g.edges.add(GraphEdge("start", "out", "print1", "in"))
        g.edges.add(GraphEdge("print1", "out", "end", "in"))
        return g
    }

    /** Граф → текст (connect + pos + команды). */
    fun graphToText(graph: NodeGraph, includeConnectMeta: Boolean = true): String {
        val sb = StringBuilder()
        sb.appendLine("# ScriptFX node graph: ${graph.id}")
        if (includeConnectMeta) {
            for (e in graph.edges) {
                val from = graph.node(e.from)?.title?.ifBlank { e.from } ?: e.from
                val to = graph.node(e.to)?.title?.ifBlank { e.to } ?: e.to
                sb.appendLine("# node connect(\"$from\") if (\"$to\")")
            }
            for (n in graph.nodes) {
                val title = n.title.ifBlank { n.id }
                sb.appendLine("# node pos(\"$title\") ${n.x} ${n.y}")
            }
            sb.appendLine()
        }
        val cmds = NodeCompiler.compile(graph)
        for (c in cmds) {
            if (c.name == "startscript") continue
            sb.append(c.name)
            if (c.args.isNotEmpty()) {
                sb.append(' ')
                sb.append(c.args.joinToString(" ") { arg ->
                    if (arg.any { it.isWhitespace() }) "\"$arg\"" else arg
                })
            }
            sb.appendLine()
        }
        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Текст → граф.
     * 1) # node connect / # node pos
     * 2) иначе линейная цепочка из ScriptParser
     */
    fun textToGraph(text: String, scriptName: String): NodeGraph {
        val graph = NodeGraph(id = scriptName, title = scriptName)
        val connects = mutableListOf<Pair<String, String>>()
        val positions = mutableMapOf<String, Pair<Float, Float>>()

        text.lineSequence().forEach { line ->
            val t = line.trim()
            CONNECT_RE.matchEntire(t)?.let { m ->
                connects.add(m.groupValues[1] to m.groupValues[2])
            }
            POS_RE.matchEntire(t)?.let { m ->
                positions[m.groupValues[1]] =
                    m.groupValues[2].toFloat() to m.groupValues[3].toFloat()
            }
        }

        val commands = ScriptParser.parse(text)
            .filter { it.name != "startscript" }

        val start = GraphNode(
            id = "start",
            kind = NodeKind.START,
            title = "Начало скрипта",
            params = mutableMapOf("cmd" to "startscript", "args" to scriptName),
            x = 80f, y = 160f
        )
        positions[start.title]?.let { (x, y) ->
            start.x = x
            start.y = y
        }
        graph.nodes.add(start)

        val bodyNodes = mutableListOf<GraphNode>()
        commands.forEachIndexed { i, cmd ->
            if (cmd.name == "stopscript") return@forEachIndexed
            val n = commandToNode(cmd, i)
            n.x = 80f + (i + 1) * 240f
            n.y = 160f
            positions[n.title]?.let { (x, y) ->
                n.x = x
                n.y = y
            }
            bodyNodes.add(n)
            graph.nodes.add(n)
        }

        val end = GraphNode(
            id = "end",
            kind = NodeKind.END,
            title = "Конец скрипта",
            params = mutableMapOf("cmd" to "stopscript"),
            x = 80f + (bodyNodes.size + 1) * 240f,
            y = 160f
        )
        positions[end.title]?.let { (x, y) ->
            end.x = x
            end.y = y
        }
        graph.nodes.add(end)

        if (connects.isNotEmpty()) {
            val byTitle = graph.nodes.associateBy { it.title }
            for ((fromT, toT) in connects) {
                val a = byTitle[fromT] ?: continue
                val b = byTitle[toT] ?: continue
                graph.edges.add(GraphEdge(a.id, "out", b.id, "in"))
            }
        } else {
            var prev = start.id
            for (n in bodyNodes) {
                graph.edges.add(GraphEdge(prev, "out", n.id, "in"))
                prev = n.id
            }
            graph.edges.add(GraphEdge(prev, "out", end.id, "in"))
        }
        return graph
    }

    private fun commandToNode(cmd: ScriptCommand, index: Int): GraphNode {
        val id = "n$index"
        return when (cmd.name) {
            "ticktime" -> GraphNode(
                id, NodeKind.WAIT, "Ожидание",
                mutableMapOf("duration" to (cmd.args.firstOrNull() ?: "1.sec"))
            )
            "quest_start" -> GraphNode(
                id, NodeKind.QUEST_START, "Квест: старт",
                mutableMapOf("quest" to (cmd.args.firstOrNull() ?: ""))
            )
            "quest_complete" -> GraphNode(
                id, NodeKind.QUEST_COMPLETE, "Квест: завершить",
                mutableMapOf("quest" to (cmd.args.firstOrNull() ?: ""))
            )
            "quest_fail" -> GraphNode(
                id, NodeKind.QUEST_FAIL, "Квест: провал",
                mutableMapOf("quest" to (cmd.args.firstOrNull() ?: ""))
            )
            "cutscene_start" -> GraphNode(
                id, NodeKind.CUTSCENE_START, "Катсцена: старт",
                mutableMapOf("mode" to (cmd.args.firstOrNull() ?: "player"))
            )
            "cutscene_end" -> GraphNode(id, NodeKind.CUTSCENE_END, "Катсцена: конец")
            "camera_set" -> GraphNode(
                id, NodeKind.CUTSCENE_SET, "Камера: set",
                mutableMapOf(
                    "x" to (cmd.args.getOrNull(0) ?: "0"),
                    "y" to (cmd.args.getOrNull(1) ?: "0"),
                    "z" to (cmd.args.getOrNull(2) ?: "0"),
                    "yaw" to (cmd.args.getOrNull(3) ?: "0"),
                    "pitch" to (cmd.args.getOrNull(4) ?: "0")
                )
            )
            "camera_move" -> GraphNode(
                id, NodeKind.CUTSCENE_MOVE, "Камера: move",
                mutableMapOf(
                    "x" to (cmd.args.getOrNull(0) ?: "0"),
                    "y" to (cmd.args.getOrNull(1) ?: "0"),
                    "z" to (cmd.args.getOrNull(2) ?: "0"),
                    "yaw" to (cmd.args.getOrNull(3) ?: "0"),
                    "pitch" to (cmd.args.getOrNull(4) ?: "0"),
                    "duration" to (cmd.args.getOrNull(5) ?: "1.sec")
                )
            )
            else -> GraphNode(
                id, NodeKind.COMMAND, titleForCmd(cmd),
                mutableMapOf(
                    "cmd" to cmd.name,
                    "args" to cmd.args.joinToString(" ") { a ->
                        if (a.any { it.isWhitespace() }) "\"$a\"" else a
                    }
                )
            )
        }
    }

    private fun titleForCmd(cmd: ScriptCommand): String = when (cmd.name) {
        "print" -> "Сообщение в чат"
        "printNPC" -> "Сообщение NPC"
        else -> cmd.name
    }
}