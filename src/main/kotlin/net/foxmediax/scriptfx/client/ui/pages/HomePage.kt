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

/**
 * Главный экран студии (как в Modern UI home):
 * — без sidebar секций / topbar
 * — слева логотип и действия
 * — справа список проектов
 * — «Продолжить» активна только при выбранном проекте
 */
class HomePage(
    private val host: StudioHost
) : StudioPage {
    override val title = "Главная"

    private val root = UiPanel(0)
    private var projectFiles: List<File> = emptyList()

    private val list = UiScrollList(onSelect = { idx, _ ->
        if (idx in projectFiles.indices) {
            StudioSession.selectProject(projectFiles[idx])
            refreshActions()
        }
    })

    private val status = UiLabel("", UiTheme.TEXT_MUTED)
    private val nameField = UiTextField(hint = "Имя нового проекта…")

    private val btnContinue = UiButton("Продолжить") {
        if (StudioSession.selectedProject == null) {
            status.text = "Сначала выберите проект в списке"
            status.color = UiTheme.WARNING
            return@UiButton
        }
        host.openPage("Сценарии")
    }

    private val btnCreate = UiButton("Создать проект") { createProject() }
    private val btnDelete = UiButton("🗑") { deleteSelected() }
    private val btnRefresh = UiButton("Обновить") { reloadProjects() }

    override fun onShow() {
        reloadProjects()
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        // левая колонка ~42%, правая — список
        val leftW = (w * 0.42f).toInt().coerceIn(260, 420)
        val rightX = x + leftW + pad
        val rightW = w - leftW - pad * 2

        // --- LEFT: brand + actions ---
        val brand = UiLabel("ScriptFX", UiTheme.ACCENT)
        brand.setBounds(x + pad * 2, y + 48, leftW - pad * 3, 22)
        root.add(brand)

        val tagline = UiLabel("Студия сценариев", UiTheme.TEXT_MUTED)
        tagline.setBounds(x + pad * 2, y + 74, leftW - pad * 3, 14)
        root.add(tagline)

        val card = UiPanel(UiTheme.BG_CARD)
        card.setBounds(x + pad * 2, y + 110, leftW - pad * 4, 200)
        root.add(card)

        val actionsTitle = UiLabel("Проект", UiTheme.ACCENT)
        actionsTitle.setBounds(card.x + 14, card.y + 14, card.w - 28, 14)
        root.add(actionsTitle)

        status.setBounds(card.x + 14, card.y + 36, card.w - 28, 14)
        root.add(status)

        btnContinue.setBounds(card.x + 14, card.y + 60, card.w - 28, 30)
        root.add(btnContinue)

        nameField.setBounds(card.x + 14, card.y + 102, card.w - 28, 26)
        root.add(nameField)

        btnCreate.setBounds(card.x + 14, card.y + 138, (card.w - 36) / 2, 28)
        root.add(btnCreate)

        btnRefresh.setBounds(card.x + 14 + (card.w - 36) / 2 + 8, card.y + 138, (card.w - 36) / 2, 28)
        root.add(btnRefresh)

        val pathHint = UiLabel("config/scriptfx/projects", UiTheme.TEXT_DIM)
        pathHint.setBounds(x + pad * 2, y + h - 28, leftW - pad * 3, 12)
        root.add(pathHint)

        // --- RIGHT: project list ---
        val listTitle = UiLabel("Ваши проекты", UiTheme.ACCENT)
        listTitle.setBounds(rightX, y + 48, rightW - 40, 16)
        root.add(listTitle)

        btnDelete.setBounds(rightX + rightW - 36, y + 42, 32, 28)
        root.add(btnDelete)

        val listFrame = UiPanel(UiTheme.BG_CARD)
        listFrame.setBounds(rightX, y + 78, rightW, h - 110)
        root.add(listFrame)

        list.setBounds(rightX + 4, y + 82, rightW - 8, h - 118)
        root.add(list)

        val listHint = UiLabel("Выберите проект курсором, затем «Продолжить»", UiTheme.TEXT_DIM)
        listHint.setBounds(rightX, y + h - 28, rightW, 12)
        root.add(listHint)

        reloadProjects()
    }

    private fun reloadProjects() {
        projectFiles = StudioSession.listProjects()
        list.items = projectFiles.map { it.name }

        val sel = StudioSession.selectedProject
        list.selected = if (sel == null) -1
        else projectFiles.indexOfFirst { it.canonicalFile == sel.canonicalFile }

        refreshActions()
    }

    private fun refreshActions() {
        val sel = StudioSession.selectedProject
        btnContinue.enabled = sel != null
        btnDelete.enabled = sel != null
        status.text = if (sel != null) "Выбран: ${sel.name}" else "Проект не выбран"
        status.color = if (sel != null) UiTheme.SUCCESS else UiTheme.TEXT_MUTED
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
        status.text = if (ok) "Удалён: ${sel.name}" else "Ошибка удаления"
        status.color = if (ok) UiTheme.WARNING else UiTheme.DANGER
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        // лёгкий вертикальный акцент слева (как разделитель колонок)
        val mid = root.x + (root.w * 0.42f).toInt()
        g.fill(mid, root.y + 40, mid + 1, root.bottom - 24, UiTheme.BORDER)
        root.render(g, mouseX, mouseY, pt)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double) =
        root.mouseDragged(mx, my, button, dx, dy)
    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double) =
        root.mouseScrolled(mx, my, sx, sy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}