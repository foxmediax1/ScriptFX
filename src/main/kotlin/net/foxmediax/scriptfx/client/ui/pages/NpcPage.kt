package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.NpcPreviewHelper
import net.foxmediax.scriptfx.client.ui.NpcBehGraphCodec
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiNpcBehaviorCanvas
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTabBar
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.npc.NpcDefinition
import net.foxmediax.scriptfx.npc.NpcModelPreset
import net.foxmediax.scriptfx.npc.NpcRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor

class NpcPage : StudioPage {
    override val title = "NPC"

    private val root = UiPanel(0)

    private var npcList: List<NpcDefinition> = emptyList()
    private var selected: NpcDefinition? = null
    private var tabIndex = 0

    private val status = UiLabel("Выберите проект на Главной", UiTheme.TEXT_MUTED)
    private val list = UiScrollList(onSelect = { idx, _ -> selectNpc(idx) })
    private val nameField = UiTextField(hint = "npc_id")

    private val displayField = UiTextField(hint = "Отображаемое имя")
    private val modelField = UiTextField(hint = "model path")
    private val animField = UiTextField(hint = "default anim")
    private val interactField = UiTextField(hint = "onInteractScript")

    private val tabBar = UiTabBar(
        tabs = listOf("Поведение", "Диалоги", "Патруль", "События"),
        selected = 0
    ) { i, _ ->
        tabIndex = i
        bindTabContent()
    }

    private val tabContent = UiLabel("", UiTheme.TEXT_MUTED)

    private val btnCreateFemale = UiButton("Female") { createNpc(NpcModelPreset.FEMALE) }
    private val btnCreateMale = UiButton("Male") { createNpc(NpcModelPreset.MALE) }
    private val btnCreateSlim = UiButton("Slim") { createNpc(NpcModelPreset.MALE_SLIM) }
    private val btnDelete = UiButton("Удалить") { deleteSelected() }
    private val btnRefresh = UiButton("Обновить") { reloadList() }
    private val btnSave = UiButton("Сохранить") { saveSelected() }
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

    private val behCanvas = UiNpcBehaviorCanvas()

    init {
        btnCreateFemale.style = UiButton.Style.PRIMARY
        btnSave.style = UiButton.Style.PRIMARY
        btnDelete.style = UiButton.Style.DANGER
    }

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
        val leftW = (w * 0.22f).toInt().coerceIn(150, 240)
        val rightW = (w * 0.26f).toInt().coerceIn(180, 260)
        val midX = x + leftW + pad
        val midW = (w - leftW - rightW - pad * 3).coerceAtLeast(220)
        val rightX = x + w - rightW - pad

        root.add(UiLabel("NPC", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, leftW - pad, 14)
        })
        list.setBounds(x + pad, y + 34, leftW - pad, h - 160)
        root.add(list)

        nameField.setBounds(x + pad, y + h - 118, leftW - pad, 24)
        root.add(nameField)

        btnCreateFemale.setBounds(x + pad, y + h - 88, 56, 24)
        btnCreateMale.setBounds(x + pad + 60, y + h - 88, 48, 24)
        btnCreateSlim.setBounds(x + pad + 112, y + h - 88, 48, 24)
        root.add(btnCreateFemale)
        root.add(btnCreateMale)
        root.add(btnCreateSlim)

        btnRefresh.setBounds(x + pad, y + h - 56, 72, 24)
        btnDelete.setBounds(x + pad + 78, y + h - 56, 72, 24)
        root.add(btnRefresh)
        root.add(btnDelete)

        behCanvas.setBounds(vpX, vpY, vpW, vpH)
        root.add(behCanvas)

        root.add(UiLabel("Превью", UiTheme.ACCENT).also {
            it.setBounds(midX, y + pad, midW, 14)
        })

        val tabH = 28
        tabBar.selected = tabIndex
        tabBar.setBounds(midX, y + 30, midW, tabH)
        root.add(tabBar)

        vpX = midX
        vpY = y + 30 + tabH + 6
        vpW = midW
        vpH = (h - (vpY - y) - 56).coerceAtLeast(100)

        root.add(UiPanel(0xFF0A0A10.toInt()).also {
            it.setBounds(vpX, vpY, vpW, vpH)
        })

        tabContent.setBounds(vpX + 10, vpY + 10, vpW - 20, 40)
        root.add(tabContent)

        btnAnim.setBounds(midX, y + h - 42, 80, 26)
        btnResetCam.setBounds(midX + 86, y + h - 42, 110, 26)
        root.add(btnAnim)
        root.add(btnResetCam)

        root.add(UiLabel("Свойства NPC", UiTheme.ACCENT).also {
            it.setBounds(rightX, y + pad, rightW, 14)
        })

        var py = y + 34
        fun field(label: String, f: UiTextField) {
            root.add(UiLabel(label, UiTheme.TEXT_MUTED).also {
                it.setBounds(rightX, py, rightW, 12)
            })
            py += 14
            f.setBounds(rightX, py, rightW, 24)
            root.add(f)
            py += 30
        }
        field("Имя", displayField)
        field("Модель", modelField)
        field("Анимация", animField)
        field("onInteract", interactField)

        btnSave.setBounds(rightX, py, rightW, 28)
        root.add(btnSave)

        status.setBounds(rightX, y + h - 28, rightW, 16)
        root.add(status)

        updatePreviewBounds()
        reloadList()
        bindFields()
        bindTabContent()
    }

    private fun bindTabContent() {
        val name = selected?.displayName ?: "—"
        tabContent.text = when (tabIndex) {
            0 -> "Поведение · $name — lookAt / gravity / scripts (граф — позже)"
            1 -> "Диалоги · $name — ветки реплик (позже)"
            2 -> "Патруль · $name — точки пути (позже)"
            else -> "События · $name — onInteract / onDeath (поля справа)"
        }
        tabContent.color = UiTheme.TEXT_MUTED
    }

    private fun bindFields() {
        val d = selected
        if (d == null) {
            displayField.value = ""
            modelField.value = ""
            animField.value = ""
            interactField.value = ""
            return
        }
        displayField.value = d.displayName
        modelField.value = d.model
        animField.value = d.defaultAnim
        interactField.value = d.onInteractScript
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
            bindFields()
            bindTabContent()
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
            bindFields()
            bindTabContent()
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
        bindFields()
        bindTabContent()
        behCanvas.graph = NpcBehGraphCodec.fromJson(def.behaviorJson)
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

    private fun saveSelected() {
        val project = projectName() ?: return
        val d = selected ?: run {
            status.text = "Не выбран NPC"
            status.color = UiTheme.WARNING
            return
        }
        val updated = d.copy(
            displayName = displayField.value.trim().ifEmpty { d.id },
            model = modelField.value.trim().ifEmpty { d.model },
            defaultAnim = animField.value.trim().ifEmpty { d.defaultAnim },
            onInteractScript = interactField.value
            behaviorJson = NpcBehGraphCodec.toJson(behCanvas.graph)
        )
        if (!NpcRegistry.save(project, updated)) {
            status.text = "Ошибка сохранения"
            status.color = UiTheme.DANGER
            return
        }
        selected = updated
        NpcPreviewHelper.setDefinition(updated)
        reloadList()
        status.text = "Сохранено: ${updated.id}"
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

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (mx.toInt() in vpX until (vpX + vpW) && my.toInt() in vpY until (vpY + vpH)) {
            NpcPreviewHelper.zoom(sy.toFloat())
            return true
        }
        return root.mouseScrolled(mx, my, sx, sy)
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean =
        root.keyPressed(key, scan, modifiers)

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean =
        root.charTyped(codePoint, modifiers)
}