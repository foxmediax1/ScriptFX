package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.hud.HudRuntime
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.hud.HudAnchor
import net.foxmediax.scriptfx.hud.HudDefinition
import net.foxmediax.scriptfx.hud.HudRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudPage : StudioPage {
    override val title = "HUD"

    private val root = UiPanel(0)

    private var items: List<HudDefinition> = emptyList()
    private var selected: HudDefinition? = null

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val list = UiScrollList(onSelect = { idx, _ -> selectHud(idx) })

    private val fId = UiTextField(hint = "id")
    private val fTitle = UiTextField(hint = "title")
    private val fW = UiTextField(hint = "width")
    private val fH = UiTextField(hint = "height")
    private val fAnchor = UiTextField(hint = "TOP_LEFT / CENTER / …")
    private val fOx = UiTextField(hint = "offsetX")
    private val fOy = UiTextField(hint = "offsetY")

    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnNew = UiButton("Создать") { createNew() }
    private val btnSave = UiButton("Сохранить") { saveSelected() }
    private val btnDelete = UiButton("Удалить") { deleteSelected() }
    private val btnShow = UiButton("hud_on") { previewOn() }
    private val btnHide = UiButton("hud_off") { previewOff() }
    private val btnHideAll = UiButton("off all") { HudRuntime.hideAll() }
    private val btnCycleAnchor = UiButton("Anchor ▶") { cycleAnchor() }

    override fun onShow() {
        reloadList()
    }

    override fun onHide() {
        // превью можно оставить в мире; если нужно гасить:
        // HudRuntime.hideAll()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.30f).toInt().coerceIn(180, 300)

        val header = UiLabel("HUD проекта", UiTheme.ACCENT)
        header.setBounds(x + pad, y + pad, leftW, 16)
        root.add(header)

        list.setBounds(x + pad, y + 36, leftW - pad, h - 100)
        root.add(list)

        btnRefresh.setBounds(x + pad, y + h - 54, 80, 24)
        btnNew.setBounds(x + pad + 88, y + h - 54, 80, 24)
        root.add(btnRefresh)
        root.add(btnNew)

        val rx = x + leftW + pad
        val rw = w - leftW - pad * 2

        val props = UiLabel("Свойства · скрипт: hud_on / hud_off", UiTheme.ACCENT)
        props.setBounds(rx, y + pad, rw, 16)
        root.add(props)

        var py = y + 36
        fun row(label: String, field: UiTextField) {
            val lab = UiLabel(label, UiTheme.TEXT_MUTED)
            lab.setBounds(rx, py, 90, 14)
            root.add(lab)
            field.setBounds(rx + 96, py - 4, (rw - 100).coerceAtMost(360), 26)
            root.add(field)
            py += 32
        }

        row("id", fId)
        row("title", fTitle)
        row("width", fW)
        row("height", fH)
        row("anchor", fAnchor)
        row("offsetX", fOx)
        row("offsetY", fOy)

        btnCycleAnchor.setBounds(rx, py + 4, 90, 24)
        btnSave.setBounds(rx + 98, py + 4, 90, 24)
        btnDelete.setBounds(rx + 196, py + 4, 80, 24)
        root.add(btnCycleAnchor)
        root.add(btnSave)
        root.add(btnDelete)

        py += 40
        val preview = UiLabel("Превью в игре (HUD overlay)", UiTheme.ACCENT)
        preview.setBounds(rx, py, rw, 14)
        root.add(preview)

        btnShow.setBounds(rx, py + 22, 80, 24)
        btnHide.setBounds(rx + 88, py + 22, 80, 24)
        btnHideAll.setBounds(rx + 176, py + 22, 80, 24)
        root.add(btnShow)
        root.add(btnHide)
        root.add(btnHideAll)

        val hint = UiLabel(
            "В скрипте: hud_on \"id\"  /  hud_off \"id\"",
            UiTheme.TEXT_MUTED
        )
        hint.setBounds(rx, py + 56, rw, 14)
        root.add(hint)

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

        HudRegistry.reload()
        items = HudRegistry.list(project).sortedBy { it.id.lowercase() }
        list.items = items.map { "${it.title.ifBlank { it.id }}  (${it.id})" }

        val selId = selected?.id
        list.selected = if (selId == null) -1 else items.indexOfFirst { it.id == selId }
        if (list.selected >= 0) selectHud(list.selected)
        else {
            selected = null
            clearFields()
        }

        status.text = "Проект: $project · HUD: ${items.size}"
        status.color = UiTheme.TEXT_MUTED
    }

    private fun selectHud(idx: Int) {
        if (idx !in items.indices) return
        selected = items[idx]
        fillFields(selected!!)
        status.text = "Выбран: ${selected!!.id}"
        status.color = UiTheme.SUCCESS
    }

    private fun fillFields(d: HudDefinition) {
        fId.value = d.id
        fTitle.value = d.title
        fW.value = d.width.toString()
        fH.value = d.height.toString()
        fAnchor.value = d.anchor.name
        fOx.value = d.offsetX.toString()
        fOy.value = d.offsetY.toString()
    }

    private fun clearFields() {
        listOf(fId, fTitle, fW, fH, fAnchor, fOx, fOy).forEach { it.value = "" }
    }

    private fun createNew() {
        val project = projectName() ?: run {
            status.text = "Нет проекта"
            status.color = UiTheme.DANGER
            return
        }
        val id = fId.value.trim().ifEmpty { "hud_${items.size + 1}" }
        val title = fTitle.value.trim().ifEmpty { id }
        val def = HudRegistry.createStub(project, id, title)
        selected = def
        reloadList()
        val idx = items.indexOfFirst { it.id == def.id }
        if (idx >= 0) selectHud(idx)
        status.text = "Создан: ${def.id}"
        status.color = UiTheme.SUCCESS
    }

    private fun cycleAnchor() {
        val values = HudAnchor.entries
        val cur = runCatching { HudAnchor.valueOf(fAnchor.value.trim()) }.getOrDefault(HudAnchor.TOP_LEFT)
        val next = values[(values.indexOf(cur) + 1) % values.size]
        fAnchor.value = next.name
    }

    private fun saveSelected() {
        val project = projectName() ?: return
        val id = fId.value.trim().ifEmpty {
            status.text = "Нужен id"
            status.color = UiTheme.WARNING
            return
        }
        val base = selected?.let { HudRegistry.get(it.id) } ?: HudDefinition(id = id, project = project)
        val anchor = runCatching { HudAnchor.valueOf(fAnchor.value.trim()) }
            .getOrDefault(HudAnchor.TOP_LEFT)

        val def = base.copy(
            id = id,
            title = fTitle.value.trim().ifEmpty { id },
            project = project,
            width = fW.value.toFloatOrNull() ?: 220f,
            height = fH.value.toFloatOrNull() ?: 120f,
            anchor = anchor,
            offsetX = fOx.value.toFloatOrNull() ?: 12f,
            offsetY = fOy.value.toFloatOrNull() ?: 12f,
            elements = base.elements.toMutableList()
        )

        val oldId = selected?.id
        if (oldId != null && oldId != def.id) {
            HudRegistry.delete(oldId)
        }

        if (!HudRegistry.save(def)) {
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
            status.text = "Не выбран HUD"
            status.color = UiTheme.WARNING
            return
        }
        HudRuntime.hide(id)
        val ok = HudRegistry.delete(id)
        selected = null
        reloadList()
        status.text = if (ok) "Удалён: $id" else "Ошибка удаления"
        status.color = if (ok) UiTheme.WARNING else UiTheme.DANGER
    }

    private fun previewOn() {
        val id = selected?.id ?: fId.value.trim().ifEmpty { null } ?: run {
            status.text = "Не выбран HUD"
            status.color = UiTheme.WARNING
            return
        }
        // подтянуть с диска
        HudRegistry.reload()
        HudRuntime.show(id)
        status.text = "hud_on \"$id\""
        status.color = UiTheme.SUCCESS
    }

    private fun previewOff() {
        val id = selected?.id ?: fId.value.trim().ifEmpty { null } ?: run {
            HudRuntime.hideAll()
            status.text = "hud_off all"
            status.color = UiTheme.TEXT_MUTED
            return
        }
        HudRuntime.hide(id)
        status.text = "hud_off \"$id\""
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