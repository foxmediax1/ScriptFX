package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.hud.HudRuntime
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiHudCanvas
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.hud.HudAnchor
import net.foxmediax.scriptfx.hud.HudDefinition
import net.foxmediax.scriptfx.hud.HudElementType
import net.foxmediax.scriptfx.hud.HudRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor

class HudPage : StudioPage {
    override val title = "HUD"

    private val root = UiPanel(0)

    private var items: List<HudDefinition> = emptyList()
    private var selected: HudDefinition? = null

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val list = UiScrollList(onSelect = { idx, _ -> selectHud(idx) })
    private val hudCanvas = UiHudCanvas()

    private val fId = UiTextField(hint = "id")
    private val fTitle = UiTextField(hint = "title")
    private val fW = UiTextField(hint = "width")
    private val fH = UiTextField(hint = "height")
    private val fAnchor = UiTextField(hint = "TOP_LEFT / CENTER / …")
    private val fOx = UiTextField(hint = "offsetX")
    private val fOy = UiTextField(hint = "offsetY")

    private val fElText = UiTextField(hint = "text / label элемента")
    private val fElW = UiTextField(hint = "el width")
    private val fElH = UiTextField(hint = "el height")

    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnNew = UiButton("+ Создать") { createNew() }
    private val btnSave = UiButton("Сохранить") { saveSelected() }
    private val btnDelete = UiButton("Удалить HUD") { deleteSelected() }
    private val btnShow = UiButton("hud_on") { previewOn() }
    private val btnHide = UiButton("hud_off") { previewOff() }
    private val btnHideAll = UiButton("off all") { HudRuntime.hideAll() }
    private val btnCycleAnchor = UiButton("Anchor ▶") { cycleAnchor() }

    private val btnAddText = UiButton("+ TEXT") { hudCanvas.addElement(HudElementType.TEXT) }
    private val btnAddRect = UiButton("+ RECT") { hudCanvas.addElement(HudElementType.RECT) }
    private val btnAddImg = UiButton("+ IMAGE") { hudCanvas.addElement(HudElementType.IMAGE) }
    private val btnDelEl = UiButton("Del el") { hudCanvas.removeSelected() }
    private val btnApplyEl = UiButton("Применить el") { applyElementFields() }

    init {
        btnNew.style = UiButton.Style.PRIMARY
        btnSave.style = UiButton.Style.PRIMARY
        btnShow.style = UiButton.Style.PRIMARY
        btnDelete.style = UiButton.Style.DANGER
        btnDelEl.style = UiButton.Style.DANGER
        btnAddText.style = UiButton.Style.PRIMARY

        hudCanvas.onSelect = { el ->
            if (el == null) {
                fElText.value = ""
                fElW.value = ""
                fElH.value = ""
            } else {
                fElText.value = el.text
                fElW.value = el.w.toString()
                fElH.value = el.h.toString()
            }
        }
        hudCanvas.onChanged = {
            status.text = "Элементы изменены — нажми Сохранить"
            status.color = UiTheme.WARNING
        }
    }

    override fun onShow() {
        reloadList()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.22f).toInt().coerceIn(150, 240)
        val rightW = (w * 0.28f).toInt().coerceIn(200, 300)
        val midX = x + leftW + pad
        val midW = (w - leftW - rightW - pad * 3).coerceAtLeast(220)
        val rightX = x + w - rightW - pad

        root.add(UiLabel("HUD проекта", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW - pad, 14)
        })
        list.setBounds(x + pad, y + 34, leftW - pad, h - 100)
        root.add(list)

        btnRefresh.setBounds(x + pad, y + h - 56, 80, 26)
        btnNew.setBounds(x + pad + 86, y + h - 56, 90, 26)
        root.add(btnRefresh)
        root.add(btnNew)

        root.add(UiLabel("HUD Maker", UiTheme.ACCENT).also {
            it.setBounds(midX, y + pad, midW, 14)
        })

        hudCanvas.definition = selected
        val canvasH = (h - 34 - 40 - pad).coerceAtLeast(120)
        hudCanvas.setBounds(midX, y + 34, midW, canvasH)
        root.add(hudCanvas)

        val barY = y + 34 + canvasH + 6
        btnAddText.setBounds(midX, barY, 70, 26)
        btnAddRect.setBounds(midX + 74, barY, 70, 26)
        btnAddImg.setBounds(midX + 148, barY, 78, 26)
        btnDelEl.setBounds(midX + 230, barY, 70, 26)
        root.add(btnAddText)
        root.add(btnAddRect)
        root.add(btnAddImg)
        root.add(btnDelEl)

        root.add(UiLabel("Свойства HUD", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + pad, rightW, 14)
        })

        var py = y + 34
        fun row(label: String, field: UiTextField, rowH: Int = 24) {
            root.add(UiLabel(label, UiTheme.TEXT_MUTED).also {
                it.setBounds(rightX, py, 64, 12)
            })
            field.setBounds(rightX + 68, py - 2, rightW - 68, rowH)
            root.add(field)
            py += rowH + 6
        }

        row("id", fId)
        row("title", fTitle)
        row("width", fW)
        row("height", fH)
        row("anchor", fAnchor)
        row("offsetX", fOx)
        row("offsetY", fOy)

        btnCycleAnchor.setBounds(rightX, py, 90, 24)
        btnSave.setBounds(rightX + 96, py, 90, 24)
        btnDelete.setBounds(rightX + 192, py, 90, 24)
        root.add(btnCycleAnchor)
        root.add(btnSave)
        root.add(btnDelete)
        py += 32

        btnShow.setBounds(rightX, py, 80, 24)
        btnHide.setBounds(rightX + 86, py, 80, 24)
        btnHideAll.setBounds(rightX + 172, py, 80, 24)
        root.add(btnShow)
        root.add(btnHide)
        root.add(btnHideAll)
        py += 36

        root.add(UiLabel("Элемент", UiTheme.ACCENT).also {
            it.setBounds(rightX, py, rightW, 14)
        })
        py += 18
        row("text", fElText)
        row("el W", fElW)
        row("el H", fElH)

        btnApplyEl.setBounds(rightX, py, rightW, 26)
        root.add(btnApplyEl)

        status.setBounds(rightX, y + h - 28, rightW, 14)
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
            hudCanvas.definition = null
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
            hudCanvas.definition = null
            clearFields()
        }

        status.text = "Проект: $project · HUD: ${items.size}"
        status.color = UiTheme.TEXT_MUTED
    }

    private fun selectHud(idx: Int) {
        if (idx !in items.indices) return
        selected = items[idx]
        fillFields(selected!!)
        hudCanvas.definition = selected
        status.text = "Выбран: ${selected!!.id} · el=${selected!!.elements.size}"
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
        listOf(fId, fTitle, fW, fH, fAnchor, fOx, fOy, fElText, fElW, fElH)
            .forEach { it.value = "" }
    }

    private fun applyElementFields() {
        val el = hudCanvas.selected() ?: run {
            status.text = "Кликни элемент на канве"
            status.color = UiTheme.WARNING
            return
        }
        el.text = fElText.value
        el.w = fElW.value.toFloatOrNull() ?: el.w
        el.h = fElH.value.toFloatOrNull() ?: el.h
        status.text = "Элемент ${el.id} обновлён"
        status.color = UiTheme.ACCENT
    }

    private fun applyElementFieldsQuiet() {
        val el = hudCanvas.selected() ?: return
        el.text = fElText.value
        el.w = fElW.value.toFloatOrNull() ?: el.w
        el.h = fElH.value.toFloatOrNull() ?: el.h
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
        applyElementFieldsQuiet()

        val base = selected?.let { HudRegistry.get(it.id) } ?: selected
        ?: HudDefinition(id = id, project = project)
        val anchor = runCatching { HudAnchor.valueOf(fAnchor.value.trim()) }
            .getOrDefault(HudAnchor.TOP_LEFT)

        val elements = (hudCanvas.definition ?: base).elements.toMutableList()

        val def = base.copy(
            id = id,
            title = fTitle.value.trim().ifEmpty { id },
            project = project,
            width = fW.value.toFloatOrNull() ?: 220f,
            height = fH.value.toFloatOrNull() ?: 120f,
            anchor = anchor,
            offsetX = fOx.value.toFloatOrNull() ?: 12f,
            offsetY = fOy.value.toFloatOrNull() ?: 12f,
            elements = elements
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
        hudCanvas.definition = def
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
        hudCanvas.definition = null
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