package net.foxmediax.scriptfx.nodes

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.foxmediax.scriptfx.npc.NpcRegistry
import java.io.File

object NodeGraphRegistry {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val byProject = mutableMapOf<String, MutableMap<String, NodeGraph>>()

    fun graphsDir(project: String): File =
        File(NpcRegistry.projectsRoot(), "$project/graphs").also { if (!it.exists()) it.mkdirs() }

    fun reload() {
        byProject.clear()
        val root = NpcRegistry.projectsRoot()
        if (!root.isDirectory) return
        root.listFiles()?.filter { it.isDirectory }?.forEach { projectDir ->
            val folder = File(projectDir, "graphs")
            if (!folder.isDirectory) return@forEach
            val map = mutableMapOf<String, NodeGraph>()
            folder.listFiles()
                ?.filter { it.isFile && it.extension.equals("fxgraph", ignoreCase = true) }
                ?.forEach { file ->
                    runCatching {
                        val g = gson.fromJson(file.readText(), NodeGraph::class.java)
                        if (g.id.isNotBlank()) {
                            g.project = projectDir.name
                            map[g.id] = g
                        }
                    }
                }
            byProject[projectDir.name] = map
        }
    }

    fun totalCount(): Int = byProject.values.sumOf { it.size }

    fun list(project: String): List<NodeGraph> =
        byProject[project]?.values?.sortedBy { it.id }.orEmpty()

    fun get(project: String, id: String): NodeGraph? = byProject[project]?.get(id)

    fun findById(id: String): NodeGraph? {
        for ((_, map) in byProject) map[id]?.let { return it }
        return null
    }

    fun save(graph: NodeGraph): Boolean {
        if (graph.id.isBlank()) return false
        return runCatching {
            File(graphsDir(graph.project), "${graph.id}.fxgraph").writeText(gson.toJson(graph))
            byProject.getOrPut(graph.project) { mutableMapOf() }[graph.id] = graph
            true
        }.getOrDefault(false)
    }

    fun delete(project: String, id: String): Boolean {
        val file = File(graphsDir(project), "$id.fxgraph")
        val ok = !file.exists() || file.delete()
        byProject[project]?.remove(id)
        return ok
    }

    fun createStub(project: String, id: String, title: String = id): NodeGraph {
        val g = NodeGraph(
            id = id,
            project = project,
            title = title.ifBlank { id },
            nodes = mutableListOf(
                GraphNode("start", NodeKind.START, "Start", x = 40f, y = 120f),
                GraphNode(
                    "wait1",
                    NodeKind.WAIT,
                    "Wait",
                    params = mutableMapOf("duration" to "1.sec"),
                    x = 220f,
                    y = 120f
                ),
                GraphNode("end", NodeKind.END, "End", x = 400f, y = 120f)
            ),
            edges = mutableListOf(
                GraphEdge("start", "out", "wait1", "in"),
                GraphEdge("wait1", "out", "end", "in")
            )
        )
        save(g)
        return g
    }
}