package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioHost
import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.StudioSession
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.io.File

class HomePage(
    private val host: StudioHost
) : StudioPage {
    override val title = "Главная"

    private val root = UiPanel(0)
    private var projectFiles: List<File> = emptyList()

    private val list = UiScrollList(onSelect = { idx, _ ->
        if (idx in projectFiles.indices) {
            StudioSession.selectProject(projectFiles[idx])
            refreshStatus()
        }
    })

    private val status = UiLabel("Проект не выбран", UiTheme.TEXT_MUTED)
    private val nameField = UiTextField(hint = "Имя нового проекта…")
    private val btnCreate = UiButton("Создать проект") { createProject() }
    private val btnContinue = UiButton("Продолжить") {
        if (StudioSession.selectedProject == null) {
            status.text = "Сначала выберите проект"
            status.color = UiTheme.WARNING
            return@UiButton
        }
        host.openPage("Сценарии")
    }
    private val btnDelete = UiButton("Удалить") { deleteSelected() }

    override fun onShow() {
        reloadProjects()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.42f).toInt().coerceAtLeast(220)
        val rightX = x + leftW + pad

        val header = UiLabel("Проекты", UiTheme.ACCENT)
        header.setBounds(x + pad, y + pad, leftW - pad, 16)
        root.add(header)

        list.setBounds(x + pad, y + 36, leftW - pad, h - 48)
        root.add(list)

        val infoTitle = UiLabel("Действия", UiTheme.ACCENT)
        infoTitle.setBounds(rightX, y + pad, w - leftW - pad * 2, 16)
        root.add(infoTitle)

        status.setBounds(rightX, y + 36, w - leftW - pad * 2, 16)
        root.add(status)

        nameField.setBounds(rightX, y + 64, (w - leftW - pad * 2).coerceAtMost(280), 28)
        root.add(nameField)

        btnCreate.setBounds(rightX, y + 100, 140, 26)
        root.add(btnCreate)

        btnContinue.setBounds(rightX, y + 134, 140, 26)
        root.add(btnContinue)

        btnDelete.setBounds(rightX + 150, y + 134, 100, 26)
        root.add(btnDelete)

        val pathHint = UiLabel("Папка: config/scriptfx/projects", UiTheme.TEXT_MUTED)
        pathHint.setBounds(rightX, y + 180, w - leftW - pad * 2, 14)
        root.add(pathHint)

        reloadProjects()
        refreshStatus()
    }

    private fun reloadProjects() {
        projectFiles = StudioSession.listProjects()
        list.items = projectFiles.map { it.name }
        val sel = StudioSession.selectedProject
        list.selected = if (sel == null) -1
        else projectFiles.indexOfFirst { it.canonicalFile == sel.canonicalFile }
        btnContinue.enabled = sel != null
        btnDelete.enabled = sel != null
    }

    private fun refreshStatus() {
        val sel = StudioSession.selectedProject
        status.text = if (sel != null) "Выбран: ${sel.name}" else "Проект не выбран"
        status.color = if (sel != null) UiTheme.SUCCESS else UiTheme.TEXT_MUTED
        btnContinue.enabled = sel != null
        btnDelete.enabled = sel != null
    }

    private fun createProject() {
        val name = nameField.value.trim()
        if (name.isEmpty()) {
            status.text = "Введите имя проекта"
            status.color = UiTheme.WARNING
            return
        }
        val dir = StudioSession.createProject(name)
        if (dir != null) {
            nameField.value = ""
            reloadProjects()
            refreshStatus()
            status.text = "Создан: ${dir.name}"
            status.color = UiTheme.SUCCESS
        } else {
            status.text = "Не удалось создать проект"
            status.color = UiTheme.DANGER
        }
    }

    private fun deleteSelected() {
        val sel = StudioSession.selectedProject ?: return
        val ok = StudioSession.deleteProject(sel)
        reloadProjects()
        refreshStatus()
        status.text = if (ok) "Удалён: ${sel.name}" else "Ошибка удаления"
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