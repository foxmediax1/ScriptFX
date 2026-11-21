package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiQuestGraph
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.quest.QuestDefinition
import net.foxmediax.scriptfx.quest.QuestEdge
import net.foxmediax.scriptfx.quest.QuestObjective
import net.foxmediax.scriptfx.quest.QuestRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor

class QuestsPage : StudioPage {
    override val title = "Квесты"

    private val root = UiPanel(0)
    private val list = UiScrollList(onSelect = { idx, _ -> selectIndex(idx) })
    private val questGraph = UiQuestGraph()

    private val status = UiLabel("", UiTheme.TEXT_MUTED)
    private val titleField = UiTextField(hint = "Название")
    private val descField = UiTextField(hint = "Описание")
    private val idField = UiTextField(hint = "id нового квеста")

    private val objIdField = UiTextField(hint = "objective id")
    private val objTextField = UiTextField(hint = "текст цели")
    private val objTargetField = UiTextField(hint = "target")

    private var items: List<QuestDefinition> = emptyList()
    private var selected: QuestDefinition? = null
    private var selectedObjIndex = -1
    private var dirty = false

    private val project: String
        get() = StudioSession.selectedProject?.name ?: "default"

    init {
        questGraph.onSelect = { o ->
            val q = selected
            selectedObjIndex = if (q == null || o == null) -1
            else q.objectives.indexOfFirst { it.id == o.id }
            bindObjectiveFields()
        }
        questGraph.onChanged = {
            dirty = true
            status.text = "Позиции изменены — сохрани квест"
            status.color = UiTheme.WARNING
        }
    }

    override fun onShow() {
        QuestRegistry.reload()
        reloadList()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val pad = UiTheme.PAD

        val leftW = (w * 0.22f).toInt().coerceIn(150, 230)
        val rightW = (w * 0.26f).toInt().coerceIn(190, 280)
        val midX = x + leftW + pad
        val midW = (w - leftW - rightW - pad * 3).coerceAtLeast(240)
        val rightX = x + w - rightW - pad

        root.add(UiLabel("Квесты · $project", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW - pad, 14)
        })
        list.setBounds(x + pad, y + 34, leftW - pad, h - 110)
        root.add(list)

        idField.setBounds(x + pad, y + h - 72, leftW - pad, 24)
        root.add(idField)

        val btnNew = UiButton("+ Создать квест") { createQuest() }
        btnNew.style = UiButton.Style.PRIMARY
        btnNew.setBounds(x + pad, y + h - 42, leftW - pad, 28)
        root.add(btnNew)

        root.add(UiLabel("Граф целей", UiTheme.ACCENT).also {
            it.setBounds(midX, y + pad, midW, 14)
        })
        questGraph.definition = selected
        questGraph.setBounds(midX, y + 34, midW, h - 80)
        root.add(questGraph)

        val btnChain = UiButton("Цепочка") {
            questGraph.layoutChain()
            dirty = true
            bindForm()
        }
        btnChain.setBounds(midX, y + h - 36, 90, 26)
        root.add(btnChain)

        val btnAddObj = UiButton("+ Цель") { addObjective() }
        btnAddObj.setBounds(midX + 96, y + h - 36, 80, 26)
        root.add(btnAddObj)

        root.add(UiLabel("Свойства квеста", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + pad, rightW, 14)
        })
        status.setBounds(rightX, y + 34, rightW, 14)
        root.add(status)

        titleField.setBounds(rightX, y + 54, rightW, 26)
        root.add(titleField)
        descField.setBounds(rightX, y + 86, rightW, 26)
        root.add(descField)

        val btnSave = UiButton("Сохранить") { saveSelected() }
        btnSave.style = UiButton.Style.PRIMARY
        btnSave.setBounds(rightX, y + 120, 110, 28)
        root.add(btnSave)

        val btnDel = UiButton("Удалить") { deleteSelected() }
        btnDel.style = UiButton.Style.DANGER
        btnDel.setBounds(rightX + 116, y + 120, 90, 28)
        root.add(btnDel)

        root.add(UiLabel("Цель (выбранная)", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + 164, rightW, 14)
        })
        objIdField.setBounds(rightX, y + 184, rightW, 24)
        root.add(objIdField)
        objTextField.setBounds(rightX, y + 214, rightW, 24)
        root.add(objTextField)
        objTargetField.setBounds(rightX, y + 244, rightW, 24)
        root.add(objTargetField)

        val btnApplyObj = UiButton("Применить цель") { applyObjective() }
        btnApplyObj.style = UiButton.Style.PRIMARY
        btnApplyObj.setBounds(rightX, y + 276, rightW, 26)
        root.add(btnApplyObj)

        val btnDelObj = UiButton("Удалить цель") { deleteObjective() }
        btnDelObj.style = UiButton.Style.DANGER
        btnDelObj.setBounds(rightX, y + 308, rightW, 26)
        root.add(btnDelObj)

        root.add(UiLabel(edgesHint(), UiTheme.TEXT_DIM).also {
            it.setBounds(rightX, y + 348, rightW, 40)
        })
        root.add(UiLabel(
            "quest_start / quest_progress / quest_complete",
            UiTheme.TEXT_MUTED
        ).also { it.setBounds(rightX, y + h - 28, rightW, 14) })
    }

    private fun edgesHint(): String {
        val q = selected ?: return "Рёбра: —"
        if (q.edges.isEmpty()) return "Рёбра: нет (кнопка «Цепочка»)"
        return "Рёбра: " + q.edges.take(6).joinToString(" · ") { "${it.from}→${it.to}" } +
                if (q.edges.size > 6) " …" else ""
    }

    private fun reloadList() {
        items = QuestRegistry.list(project)
        list.items = items.map { q ->
            val mark = if (selected?.id == q.id) "▶ " else ""
            "$mark${q.title.ifBlank { q.id }}"
        }
        selected = selected?.id?.let { id -> items.find { it.id == id } } ?: items.firstOrNull()
        bindForm()
        questGraph.definition = selected
        bindObjectiveFields()
    }

    private fun selectIndex(idx: Int) {
        selected = items.getOrNull(idx)
        selectedObjIndex = -1
        dirty = false
        bindForm()
        questGraph.definition = selected
        bindObjectiveFields()
        list.items = items.map { q ->
            val mark = if (selected?.id == q.id) "▶ " else ""
            "$mark${q.title.ifBlank { q.id }}"
        }
    }

    private fun bindForm() {
        val q = selected
        if (q == null) {
            status.text = "Нет выбранного квеста"
            status.color = UiTheme.TEXT_MUTED
            titleField.value = ""
            descField.value = ""
            return
        }
        status.text = "ID: ${q.id} · целей: ${q.objectives.size} · рёбер: ${q.edges.size}"
        status.color = if (dirty) UiTheme.WARNING else UiTheme.SUCCESS
        titleField.value = q.title
        descField.value = q.description
    }

    private fun bindObjectiveFields() {
        val o = selected?.objectives?.getOrNull(selectedObjIndex)
        if (o == null) {
            objIdField.value = ""
            objTextField.value = ""
            objTargetField.value = ""
            return
        }
        objIdField.value = o.id
        objTextField.value = o.text
        objTargetField.value = o.target.toString()
    }

    private fun createQuest() {
        val id = idField.value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifEmpty { "quest_${items.size + 1}" }
        if (QuestRegistry.get(project, id) != null) {
            status.text = "Уже есть: $id"
            status.color = UiTheme.WARNING
            return
        }
        val def = QuestDefinition(
            id = id,
            title = id.replace('_', ' '),
            description = "",
            objectives = mutableListOf(
                QuestObjective("obj_1", "Первая цель", 1, 40f, 60f),
                QuestObjective("obj_2", "Вторая цель", 1, 220f, 60f)
            ),
            edges = mutableListOf(QuestEdge("obj_1", "obj_2"))
        )
        QuestRegistry.save(project, def)
        idField.value = ""
        selected = def
        dirty = false
        reloadList()
        status.text = "Создан $id"
        status.color = UiTheme.SUCCESS
    }

    private fun addObjective() {
        val q = selected ?: return
        val n = q.objectives.size + 1
        q.objectives.add(
            QuestObjective("obj_$n", "Цель $n", 1, 40f + n * 40f, 60f + (n % 3) * 30f)
        )
        selectedObjIndex = q.objectives.lastIndex
        dirty = true
        questGraph.definition = q
        bindForm()
        bindObjectiveFields()
    }

    private fun applyObjective() {
        val q = selected ?: return
        val o = q.objectives.getOrNull(selectedObjIndex) ?: return
        o.id = objIdField.value.trim().ifBlank { o.id }
        o.text = objTextField.value
        o.target = objTargetField.value.toIntOrNull() ?: 1
        dirty = true
        questGraph.definition = q
        bindForm()
        bindObjectiveFields()
    }

    private fun deleteObjective() {
        val q = selected ?: return
        if (selectedObjIndex !in q.objectives.indices) return
        val id = q.objectives[selectedObjIndex].id
        q.objectives.removeAt(selectedObjIndex)
        q.edges.removeAll { it.from == id || it.to == id }
        selectedObjIndex = -1
        dirty = true
        questGraph.definition = q
        bindForm()
        bindObjectiveFields()
    }

    private fun saveSelected() {
        val q = selected ?: return
        applyObjective()
        q.title = titleField.value.ifBlank { q.id }
        q.description = descField.value
        QuestRegistry.save(project, q)
        QuestRegistry.reload()
        selected = QuestRegistry.get(project, q.id)
        dirty = false
        reloadList()
        status.text = "Сохранено ${q.id}.fxquest"
        status.color = UiTheme.SUCCESS
    }

    private fun deleteSelected() {
        val q = selected ?: return
        QuestRegistry.delete(project, q.id)
        selected = null
        selectedObjIndex = -1
        dirty = false
        reloadList()
        status.text = "Удалён ${q.id}"
        status.color = UiTheme.TEXT_MUTED
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) =
        root.render(g, mouseX, mouseY, pt)

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double) =
        root.mouseDragged(mx, my, button, dx, dy)
    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double) =
        root.mouseScrolled(mx, my, sx, sy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}