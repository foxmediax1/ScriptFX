package net.foxmediax.scriptfx.nodes

data class NodeTemplate(
    val kind: NodeKind,
    val title: String,
    val params: Map<String, String> = emptyMap(),
    val category: String = "Общие"
)

object NodePalette {

    val all: List<NodeTemplate> = listOf(
        NodeTemplate(NodeKind.START, "Начало скрипта", mapOf("cmd" to "startscript"), "Поток"),
        NodeTemplate(NodeKind.END, "Конец скрипта", mapOf("cmd" to "stopscript"), "Поток"),
        NodeTemplate(NodeKind.WAIT, "Ожидание", mapOf("duration" to "1.sec"), "Поток"),
        NodeTemplate(NodeKind.COMMAND, "Сообщение в чат", mapOf("cmd" to "print", "args" to "Привет, мир!"), "Чат"),
        NodeTemplate(NodeKind.COMMAND, "Сообщение NPC", mapOf("cmd" to "printNPC", "args" to "Текст WHITE Имя WHITE"), "Чат"),
        NodeTemplate(NodeKind.SCRIPT, "Запустить скрипт", mapOf("script" to "other"), "Поток"),
        NodeTemplate(NodeKind.QUEST_START, "Квест: старт", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.QUEST_COMPLETE, "Квест: завершить", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.QUEST_FAIL, "Квест: провал", mapOf("quest" to "id"), "Квесты"),
        NodeTemplate(NodeKind.CUTSCENE_START, "Катсцена: старт", mapOf("mode" to "player"), "Катсцена"),
        NodeTemplate(NodeKind.CUTSCENE_END, "Катсцена: конец", category = "Катсцена"),
        NodeTemplate(
            NodeKind.CUTSCENE_SET, "Камера: set",
            mapOf("x" to "0", "y" to "64", "z" to "0", "yaw" to "0", "pitch" to "0"), "Катсцена"
        ),
        NodeTemplate(
            NodeKind.CUTSCENE_MOVE, "Камера: move",
            mapOf("x" to "0", "y" to "64", "z" to "0", "yaw" to "0", "pitch" to "0", "duration" to "1.sec"),
            "Катсцена"
        ),
        NodeTemplate(NodeKind.BRANCH, "Ветка if", mapOf("var" to "flag", "op" to "==", "value" to "1"), "Поток")
    )

    fun search(query: String): List<NodeTemplate> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter {
            it.title.lowercase().contains(q) ||
                    it.kind.name.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
        }
    }

    fun spawn(template: NodeTemplate, id: String, x: Float, y: Float): GraphNode =
        GraphNode(
            id = id,
            kind = template.kind,
            title = template.title,
            params = template.params.toMutableMap(),
            x = x,
            y = y
        )
}