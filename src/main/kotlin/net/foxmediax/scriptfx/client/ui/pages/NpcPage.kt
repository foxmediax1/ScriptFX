package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcModelPreset
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class NpcPage : StudioPage {
    override val title = "NPC"

    private val root = UiPanel(0)

    private var npcList: List<NpcDefinition> = emptyList()
    private var selected: NpcDefinition? = null

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val list = UiScrollList(onSelect = { idx, _ -> selectNpc(idx) })
    private val nameField = UiTextField(hint = "npc_id")

    private val btnCreateFemale = UiButton("Female") { createNpc(NpcModelPreset.FEMALE) }
    private val btnCreateMale = UiButton("Male") { createNpc(NpcModelPreset.MALE) }
    private val btnCreateSlim = UiButton("Slim") { createNpc(NpcModelPreset.MALE_SLIM) }
    private val btnDelete = UiButton("Удалить") { deleteSelected() }
    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnAnim = UiButton("Anim ▶") {
        val name = NpcPreviewHelper.cycleAnim()
        status.text = "Анимация: $name"
        status.color = UiTheme.ACCENT
    }
    private val btnResetCam = UiButton("Сброс камеры") {
        NpcPreviewHelper.resetCamera()
    }

    private var vpX = 0
    private var vpY = 0
    private var vpW = 0
    private var vpH = 0
    private var dragging = false
    private var lastMx = 0
    private var lastMy = 0

    override fun onShow() {
        reloadList()
    }

    override fun onHide() {
        NpcPreviewHelper.clearBounds()
        NpcPreviewHelper.setDefinition(null)
        selected = null
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.28f).toInt().coerceIn(180, 300)

        val header = UiLabel("NPC проекта", UiTheme.ACCENT)
        header.setBounds(x + pad, y + pad, leftW, 16)
        root.add(header)

        list.setBounds(x + pad, y + 36, leftW - pad, h - 170)
        root.add(list)

        nameField.setBounds(x + pad, y + h - 120, leftW - pad, 26)
        root.add(nameField)

        btnCreateFemale.setBounds(x + pad, y + h - 86, 64, 24)
        btnCreateMale.setBounds(x + pad + 70, y + h - 86, 54, 24)
        btnCreateSlim.setBounds(x + pad + 130, y + h - 86, 54, 24)
        root.add(btnCreateFemale)
        root.add(btnCreateMale)
        root.add(btnCreateSlim)

        btnRefresh.setBounds(x + pad, y + h - 54, 80, 24)
        btnDelete.setBounds(x + pad + 88, y + h - 54, 80, 24)
        root.add(btnRefresh)
        root.add(btnDelete)

        vpX = x + leftW + pad
        vpY = y + 36
        vpW = w - leftW - pad * 2
        vpH = h - 90

        val prevTitle = UiLabel("Превью (ЛКМ — вращать)", UiTheme.ACCENT)
        prevTitle.setBounds(vpX, y + pad, vpW, 16)
        root.add(prevTitle)

        val frame = UiPanel(0xFF0A0A10.toInt())
        frame.setBounds(vpX, vpY, vpW, vpH)
        root.add(frame)

        btnAnim.setBounds(vpX, y + h - 46, 90, 24)
        btnResetCam.setBounds(vpX + 100, y + h - 46, 110, 24)
        root.add(btnAnim)
        root.add(btnResetCam)

        status.setBounds(vpX + 220, y + h - 42, (w - leftW - 240).coerceAtLeast(80), 16)
        root.add(status)

        updatePreviewBounds()
        reloadList()
    }

    private fun projectName(): String? = StudioSession.selectedProject?.name

    private fun reloadList() {
        val project = projectName()
        if (project == null) {
            npcList = emptyList()
            list.items = emptyList()
            list.selected = -1
            selected = null
            status.text = "Сначала выберите проект на Главной"
            status.color = UiTheme.WARNING
            NpcPreviewHelper.setDefinition(null)
            NpcPreviewHelper.clearBounds()
            return
        }

        NpcRegistry.reload()
        npcList = NpcRegistry.listNpcs(project).sortedBy { it.id.lowercase() }
        list.items = npcList.map { "${it.displayName}  (${it.id})" }

        val selId = selected?.id
        list.selected = if (selId == null) -1 else npcList.indexOfFirst { it.id == selId }

        if (list.selected >= 0) {
            selectNpc(list.selected)
        } else {
            selected = null
            NpcPreviewHelper.setDefinition(null)
        }

        status.text = "Проект: $project · NPC: ${npcList.size}"
        status.color = UiTheme.TEXT_MUTED
        updatePreviewBounds()
    }

    private fun selectNpc(idx: Int) {
        val project = projectName() ?: return
        if (idx !in npcList.indices) return
        val def = NpcRegistry.get(project, npcList[idx].id) ?: npcList[idx]
        selected = def
        list.selected = idx
        NpcPreviewHelper.setDefinition(def)
        updatePreviewBounds()
        status.text = "NPC: ${def.displayName} · ${def.model}"
        status.color = UiTheme.SUCCESS
    }

    private fun createNpc(preset: NpcModelPreset) {
        val project = projectName() ?: run {
            status.text = "Нет проекта"
            status.color = UiTheme.DANGER
            return
        }
        val raw = nameField.value.trim().ifEmpty { "npc_${npcList.size + 1}" }
        val id = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        if (id.isEmpty()) {
            status.text = "Некорректный id"
            status.color = UiTheme.WARNING
            return
        }
        if (NpcRegistry.get(project, id) != null) {
            status.text = "Уже существует: $id"
            status.color = UiTheme.WARNING
            return
        }

        val def = NpcDefinition.create(id, preset)
        val ok = NpcRegistry.save(project, def)
        if (!ok) {
            status.text = "Не удалось сохранить $id"
            status.color = UiTheme.DANGER
            return
        }
        nameField.value = ""
        reloadList()
        val idx = npcList.indexOfFirst { it.id == id }
        if (idx >= 0) selectNpc(idx)
        status.text = "Создан: $id (${preset.label})"
        status.color = UiTheme.SUCCESS
    }

    private fun deleteSelected() {
        val project = projectName() ?: return
        val def = selected ?: run {
            status.text = "Не выбран NPC"
            status.color = UiTheme.WARNING
            return
        }
        val ok = NpcRegistry.delete(project, def.id)
        selected = null
        NpcPreviewHelper.setDefinition(null)
        reloadList()
        status.text = if (ok) "Удалён: ${def.id}" else "Ошибка удаления"
        status.color = if (ok) UiTheme.WARNING else UiTheme.DANGER
    }

    private fun updatePreviewBounds() {
        if (vpW <= 0 || vpH <= 0 || projectName() == null) {
            NpcPreviewHelper.clearBounds()
            return
        }
        NpcPreviewHelper.setBounds(vpX + 8, vpY + 8, vpX + vpW - 8, vpY + vpH - 8)
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
        // 3D: NpcPreviewHelper.renderInGui через ScreenEvents.afterExtract
        val err = NpcPreviewHelper.loadError
        if (!err.isNullOrBlank()) {
            g.text(
                Minecraft.getInstance().font,
                Component.literal("Ошибка: $err"),
                vpX + 12,
                vpY + 12,
                UiTheme.DANGER,
                false
            )
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (button == 0 && mx in vpX until (vpX + vpW) && my in vpY until (vpY + vpH)) {
            dragging = true
            lastMx = mx
            lastMy = my
            return true
        }
        return root.mouseClicked(mx, my, button)
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean {
        if (dragging && button == 0) {
            NpcPreviewHelper.drag((mx - lastMx).toFloat(), (my - lastMy).toFloat())
            lastMx = mx
            lastMy = my
            return true
        }
        return root.mouseDragged(mx, my, button, dx, dy)
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        if (dragging) {
            dragging = false
            return true
        }
        return root.mouseReleased(mx, my, button)
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean =
        root.mouseScrolled(mx, my, sx, sy)

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean =
        root.keyPressed(key, scan, modifiers)

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean =
        root.charTyped(codePoint, modifiers)
}