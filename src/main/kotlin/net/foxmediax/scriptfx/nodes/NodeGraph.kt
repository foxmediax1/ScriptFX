package net.foxmediax.scriptfx.nodes

enum class NodeKind {
    START,
    END,
    WAIT,
    DELAY,
    COMMAND,
    SCRIPT,
    QUEST_START,
    QUEST_COMPLETE,
    QUEST_FAIL,
    CUTSCENE_START,
    CUTSCENE_END,
    CUTSCENE_MOVE,
    CUTSCENE_SET,
    BRANCH
}

data class GraphNode(
    var id: String = "",
    var kind: NodeKind = NodeKind.COMMAND,
    var title: String = "",
    var params: MutableMap<String, String> = mutableMapOf(),
    var x: Float = 0f,
    var y: Float = 0f
)

data class GraphEdge(
    var from: String = "",
    var fromPort: String = "out",
    var to: String = "",
    var toPort: String = "in"
)

data class NodeGraph(
    var id: String = "",
    var project: String = "default",
    var title: String = "",
    var nodes: MutableList<GraphNode> = mutableListOf(),
    var edges: MutableList<GraphEdge> = mutableListOf()
) {
    fun node(id: String): GraphNode? = nodes.find { it.id == id }

    fun outgoing(nodeId: String, port: String = "out"): List<GraphEdge> =
        edges.filter { it.from == nodeId && it.fromPort == port }

    fun startNode(): GraphNode? =
        nodes.find { it.kind == NodeKind.START } ?: nodes.firstOrNull()
}