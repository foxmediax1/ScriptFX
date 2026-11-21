package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiCheckBox
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.triggers.TriggerDefinition
import net.foxmediax.scriptfx.triggers.TriggerKind
import net.foxmediax.scriptfx.triggers.TriggerRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

class TriggersPage : StudioPage {
    override val title = "Триггеры"

    private val root = UiPanel(0)

    private var items: List<TriggerDefinition> = emptyList()
    private var selected: TriggerDefinition? = null
    private var suppressFields = false

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val list = UiScrollList(onSelect = { idx, _ -> selectTrigger(idx) })

    private val fId = UiTextField(hint = "id")
    private val fTitle = UiTextField(hint = "title")
    private val fKind = UiTextField(hint = "ZONE|INTERACT|WORLD_START")
    private val fX = UiTextField(hint = "x")
    private val fY = UiTextField(hint = "y")
    private val fZ = UiTextField(hint = "z")
    private val fRadius = UiTextField(hint = "radius")
    private val fAction = UiTextField(hint = "actionScript / startscript name")
    private val fItem = UiTextField(hint = "itemId (INTERACT)")

    private val cbActive = UiCheckBox("Активен", true)
    private val cbOnce = UiCheckBox("Один раз", true)

    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnNew = UiButton("+ ZONE") { createNew(TriggerKind.ZONE) }
    private val btnNewInteract = UiButton("+ INTERACT") { createNew(TriggerKind.INTERACT) }
    private val btnNewStart = UiButton("+ WorldStart") { createNew(TriggerKind.WORLD_START) }
    private val btnFromPlayer = UiButton("XYZ игрока") { fillFromPlayer() }
    private val btnSave = UiButton("Сохранить") { saveSelected() }
    private val btnDelete = UiButton("Удалить") { deleteSelected() }
    private val btnCycleKind = UiButton("Kind ▶") { cycleKind() }

    init {
        btnNew.style = UiButton.Style.PRIMARY
        btnSave.style = UiButton.Style.PRIMARY
        btnDelete.style = UiButton.Style.DANGER
    }

    override fun onShow() {
        reloadList()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.30f).toInt().coerceIn(180, 300)

        root.add(UiLabel("Триггеры проекта", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW, 16)
        })

        list.setBounds(x + pad, y + 36, leftW - pad, h - 130)
        root.add(list)

        btnRefresh.setBounds(x + pad, y + h - 88, 80, 24)
        btnNew.setBounds(x + pad + 86, y + h - 88, 78, 24)
        btnNewInteract.setBounds(x + pad, y + h - 58, 100, 24)
        btnNewStart.setBounds(x + pad + 106, y + h - 58, 100, 24)
        root.add(btnRefresh)
        root.add(btnNew)
        root.add(btnNewInteract)
        root.add(btnNewStart)

        val rx = x + leftW + pad
        val rw = w - leftW - pad * 2

        root.add(UiLabel("Свойства", UiTheme.ACCENT).also {
            it.setBounds(rx, y + pad, rw, 16)
        })

        var py = y + 36
        fun row(label: String, field: UiTextField, rowH: Int = 26) {
            val lab = UiLabel(label, UiTheme.TEXT_MUTED)
            lab.setBounds(rx, py, 90, 14)
            root.add(lab)
            field.setBounds(rx + 96, py - 4, (rw - 100).coerceAtMost(360), rowH)
            root.add(field)
            py += rowH + 6
        }

        row("id", fId)
        row("title", fTitle)
        row("kind", fKind)
        row("x", fX)
        row("y", fY)
        row("z", fZ)
        row("radius", fRadius)
        row("action", fAction)
        row("itemId", fItem)

        cbActive.setBounds(rx, py, 140, 22)
        root.add(cbActive)
        cbOnce.setBounds(rx + 150, py, 140, 22)
        root.add(cbOnce)
        py += 30

        btnCycleKind.setBounds(rx, py + 4, 80, 24)
        btnFromPlayer.setBounds(rx + 88, py + 4, 100, 24)
        btnSave.setBounds(rx + 196, py + 4, 90, 24)
        btnDelete.setBounds(rx + 294, py + 4, 80, 24)
        root.add(btnCycleKind)
        root.add(btnFromPlayer)
        root.add(btnSave)
        root.add(btnDelete)

        status.setBounds(rx, y + h - 24, rw, 14)
        root.add(status)

        reloadList()
    }

    private fun projectName(): String? = StudioSession.selectedProject?.name

    private fun reloadList() {
        val project = projectName()
        if (project == null) {
            items = emptyList()
            list.items = emptyList()
            selected = null
            clearFields()
            status.text = "Сначала выберите проект на Главной"
            status.color = UiTheme.WARNING
            return
        }

        TriggerRegistry.reload()
        items = TriggerRegistry.list(project).sortedBy { it.id.lowercase() }
        list.items = items.map { t ->
            val mark = if (t.active) "●" else "○"
            "$mark ${t.title.ifBlank { t.id }}  [${t.kind}]"
        }

        val selId = selected?.id
        list.selected = if (selId == null) -1 else items.indexOfFirst { it.id == selId }
        if (list.selected >= 0) selectTrigger(list.selected)
        else {
            selected = null
            clearFields()
        }

        status.text = "Проект: $project · триггеров: ${items.size}"
        status.color = UiTheme.TEXT_MUTED
    }

    private fun selectTrigger(idx: Int) {
        if (idx !in items.indices) return
        selected = items[idx]
        fillFields(selected!!)
        status.text = "Выбран: ${selected!!.id}"
        status.color = UiTheme.SUCCESS
    }

    private fun fillFields(t: TriggerDefinition) {
        suppressFields = true
        fId.value = t.id
        fTitle.value = t.title
        fKind.value = t.kind.name
        fX.value = t.x.toString()
        fY.value = t.y.toString()
        fZ.value = t.z.toString()
        fRadius.value = t.radius.toString()
        fAction.value = t.actionScript
        fItem.value = t.itemId.orEmpty()
        cbActive.checked = t.active
        cbOnce.checked = t.once
        suppressFields = false
    }

    private fun clearFields() {
        suppressFields = true
        listOf(fId, fTitle, fKind, fX, fY, fZ, fRadius, fAction, fItem)
            .forEach { it.value = "" }
        cbActive.checked = true
        cbOnce.checked = true
        suppressFields = false
    }

    private fun createNew(kind: TriggerKind) {
        val project = projectName() ?: run {
            status.text = "Нет проекта"
            status.color = UiTheme.DANGER
            return
        }
        val id = "trigger_${items.size + 1}"
        val def = TriggerDefinition(
            id = id,
            title = id,
            kind = kind,
            project = project,
            actionScript = "print \"trigger $id\"",
            active = true,
            once = true
        )
        if (kind == TriggerKind.ZONE) {
            val p = Minecraft.getInstance().player
            if (p != null) {
                def.x = p.x
                def.y = p.y
                def.z = p.z
            }
        }
        if (!TriggerRegistry.save(def)) {
            status.text = "Не удалось создать"
            status.color = UiTheme.DANGER
            return
        }
        reloadList()
        val idx = items.indexOfFirst { it.id == def.id }
        if (idx >= 0) selectTrigger(idx)
        status.text = "Создан: ${def.id}"
        status.color = UiTheme.SUCCESS
    }

    private fun fillFromPlayer() {
        val p = Minecraft.getInstance().player ?: run {
            status.text = "Нет игрока (зайди в мир)"
            status.color = UiTheme.WARNING
            return
        }
        fX.value = "%.3f".format(p.x)
        fY.value = "%.3f".format(p.y)
        fZ.value = "%.3f".format(p.z)
        status.text = "XYZ с игрока"
        status.color = UiTheme.ACCENT
    }

    private fun cycleKind() {
        val order = listOf(TriggerKind.ZONE, TriggerKind.INTERACT, TriggerKind.WORLD_START)
        val cur = runCatching { TriggerKind.valueOf(fKind.value.trim()) }.getOrDefault(TriggerKind.ZONE)
        val next = order[(order.indexOf(cur) + 1) % order.size]
        fKind.value = next.name
    }

    private fun saveSelected() {
        val project = projectName() ?: run {
            status.text = "Нет проекта"
            status.color = UiTheme.DANGER
            return
        }
        val id = fId.value.trim().ifEmpty {
            status.text = "id обязателен"
            status.color = UiTheme.WARNING
            return
        }
        val kind = runCatching { TriggerKind.valueOf(fKind.value.trim()) }
            .getOrDefault(TriggerKind.ZONE)

        val def = TriggerDefinition(
            id = id,
            title = fTitle.value.trim().ifEmpty { id },
            kind = kind,
            project = project,
            x = fX.value.toDoubleOrNull() ?: 0.0,
            y = fY.value.toDoubleOrNull() ?: 64.0,
            z = fZ.value.toDoubleOrNull() ?: 0.0,
            radius = fRadius.value.toDoubleOrNull() ?: 10.0,
            actionScript = fAction.value.trim(),
            itemId = fItem.value.trim().ifEmpty { null },
            active = cbActive.checked,
            once = cbOnce.checked
        )

        val oldId = selected?.id
        if (oldId != null && oldId != def.id) {
            TriggerRegistry.delete(oldId)
        }

        if (!TriggerRegistry.save(def)) {
            status.text = "Ошибка сохранения"
            status.color = UiTheme.DANGER
            return
        }
        selected = def
        reloadList()
        status.text = "Сохранено: ${def.id}"
        status.color = UiTheme.SUCCESS
    }

    private fun deleteSelected() {
        val id = selected?.id ?: fId.value.trim().ifEmpty { null } ?: run {
            status.text = "Не выбран триггер"
            status.color = UiTheme.WARNING
            return
        }
        val ok = TriggerRegistry.delete(id)
        selected = null
        reloadList()
        status.text = if (ok) "Удалён: $id" else "Ошибка удаления"
        status.color = if (ok) UiTheme.WARNING else UiTheme.DANGER
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