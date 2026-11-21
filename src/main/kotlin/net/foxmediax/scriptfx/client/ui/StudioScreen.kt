package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.client.ui.pages.CutscenesPage
import net.foxmediax.scriptfx.client.ui.pages.DocsPage
import net.foxmediax.scriptfx.client.ui.pages.HomePage
import net.foxmediax.scriptfx.client.ui.pages.HudPage
import net.foxmediax.scriptfx.client.ui.pages.LogsPage
import net.foxmediax.scriptfx.client.ui.pages.NpcPage
import net.foxmediax.scriptfx.client.ui.pages.ParticlesPage
import net.foxmediax.scriptfx.client.ui.pages.QuestsPage
import net.foxmediax.scriptfx.client.ui.pages.ScenariosPage
import net.foxmediax.scriptfx.client.ui.pages.SettingsPage
import net.foxmediax.scriptfx.client.ui.pages.TriggersPage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/**
 * Chrome по макету: логотип + вкладки сверху, контент на всю ширину, status bar снизу.
 */
class StudioScreen : Screen(Component.literal("ScriptFX Studio")) {

    private enum class Mode { HOME, STUDIO }

    private var mode = Mode.HOME

    private val root = UiPanel(0)
    private val header = UiPanel(UiTheme.BG_HEADER)

    private val studioSections = listOf(
        "Сценарии",
        "NPC",
        "Квесты",
        "Триггеры",
        "Катсцены",
        "HUD",
        "Частицы",
        "Логи",
        "Документация",
        "Настройки"
    )

    private val host = object : StudioHost {
        override fun openPage(name: String) {
            when {
                name == "Главная" -> goHome()
                name in studioSections || name in pages -> enterStudio(name)
            }
        }
    }

    private val homePage = HomePage(host)
    private val scenariosPage = ScenariosPage()
    private val npcPage = NpcPage()
    private val questsPage = QuestsPage()
    private val triggersPage = TriggersPage()
    private val cutscenesPage = CutscenesPage()
    private val hudPage = HudPage()
    private val particlesPage = ParticlesPage()
    private val logsPage = LogsPage()
    private val docsPage = DocsPage()
    private val settingsPage = SettingsPage()

    private val pages: Map<String, StudioPage> = mapOf(
        "Главная" to homePage,
        "Сценарии" to scenariosPage,
        "NPC" to npcPage,
        "Квесты" to questsPage,
        "Триггеры" to triggersPage,
        "Катсцены" to cutscenesPage,
        "HUD" to hudPage,
        "Частицы" to particlesPage,
        "Логи" to logsPage,
        "Документация" to docsPage,
        "Настройки" to settingsPage
    )

    private var sectionIndex = 0
    private var currentPage: StudioPage = homePage

    private val tabBar = UiTabBar(
        tabs = studioSections,
        selected = 0
    ) { i, name ->
        sectionIndex = i
        currentPage.onHide()
        currentPage = pages[name] ?: PlaceholderPage(name)
        currentPage.onShow()
        layout()
    }

    private val btnSettings = UiButton("⚙") { enterStudio("Настройки") }
    private val btnClose = UiButton("✕") { onClose() }

    private val statusH = 22

    override fun isPauseScreen(): Boolean = true

    override fun init() {
        super.init()
        layout()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layout()
    }

    fun enterStudio(section: String = "Сценарии") {
        val needsProject = section !in setOf("Документация", "Настройки", "Логи")
        if (needsProject && StudioSession.selectedProject == null) {
            mode = Mode.HOME
            currentPage = homePage
            layout()
            return
        }
        mode = Mode.STUDIO
        sectionIndex = studioSections.indexOf(section).let { if (it < 0) 0 else it }
        tabBar.selected = sectionIndex
        currentPage.onHide()
        currentPage = pages[section] ?: pages["Сценарии"] ?: homePage
        currentPage.onShow()
        layout()
    }

    fun goHome() {
        currentPage.onHide()
        mode = Mode.HOME
        currentPage = homePage
        currentPage.onShow()
        layout()
    }

    private fun layout() {
        root.clearChildren()
        header.clearChildren()
        root.setBounds(0, 0, width, height)

        if (mode == Mode.HOME) {
            currentPage = homePage
            currentPage.layout(0, 0, width, height)
            return
        }

        header.setBounds(0, 0, width, UiTheme.TOPBAR_H)
        header.background = UiTheme.BG_HEADER

        val logo = UiLabel("ScriptFX", UiTheme.ACCENT)
        logo.setBounds(14, (UiTheme.TOPBAR_H - 14) / 2, 88, 14)
        header.add(logo)

        val rightBtns = 36 + 8 + 36 + 12
        val tabsX = 110
        val tabsW = (width - tabsX - rightBtns).coerceAtLeast(200)
        tabBar.tabs = studioSections
        tabBar.selected = sectionIndex
        tabBar.setBounds(tabsX, 4, tabsW, UiTheme.TOPBAR_H - 8)
        header.add(tabBar)

        btnSettings.style = UiButton.Style.GHOST
        btnSettings.setBounds(width - 36 - 8 - 36, 6, 28, 28)
        header.add(btnSettings)

        btnClose.style = UiButton.Style.GHOST
        btnClose.setBounds(width - 36, 6, 28, 28)
        header.add(btnClose)

        root.add(header)

        val contentY = UiTheme.TOPBAR_H
        val contentH = (height - contentY - statusH).coerceAtLeast(100)
        currentPage.layout(0, contentY, width, contentH)
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, width, height, UiTheme.BG)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (mode == Mode.STUDIO) {
            g.fill(0, 0, width, UiTheme.TOPBAR_H, UiTheme.BG_HEADER)
            g.fill(0, UiTheme.TOPBAR_H - 1, width, UiTheme.TOPBAR_H, UiTheme.BORDER)

            root.render(g, mouseX, mouseY, partialTick)

            val sy = height - statusH
            g.fill(0, sy, width, height, UiTheme.BG_HEADER)
            g.fill(0, sy, width, sy + 1, UiTheme.BORDER)
            val font = Minecraft.getInstance().font
            val proj = StudioSession.selectedProject?.name ?: "—"
            val sec = studioSections.getOrElse(sectionIndex) { "Студия" }
            g.text(
                font,
                Component.literal("● Готово"),
                12,
                sy + (statusH - font.lineHeight) / 2,
                UiTheme.SUCCESS,
                false
            )
            g.text(
                font,
                Component.literal("$sec  ·  $proj"),
                90,
                sy + (statusH - font.lineHeight) / 2,
                UiTheme.TEXT_MUTED,
                false
            )
        }
        currentPage.render(g, mouseX, mouseY, partialTick)
        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (mode == Mode.STUDIO && root.mouseClicked(mx, my, btn)) return true
        if (currentPage.mouseClicked(mx, my, btn)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (mode == Mode.STUDIO && root.mouseReleased(mx, my, btn)) return true
        if (currentPage.mouseReleased(mx, my, btn)) return true
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (mode == Mode.STUDIO && root.mouseDragged(mx, my, btn, dx, dy)) return true
        if (currentPage.mouseDragged(mx, my, btn, dx, dy)) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mode == Mode.STUDIO && root.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (currentPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (mode == Mode.STUDIO) {
                goHome()
                return true
            }
            onClose()
            return true
        }
        if (mode == Mode.STUDIO && root.keyPressed(event.key(), 0, 0)) return true
        if (currentPage.keyPressed(event.key(), 0, 0)) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val cp = event.codepoint()
        if (currentPage.charTyped(cp, 0)) return true
        if (mode == Mode.STUDIO && root.charTyped(cp, 0)) return true
        return super.charTyped(event)
    }

    override fun onClose() {
        currentPage.onHide()
        super.onClose()
    }
}

class PlaceholderPage(override val title: String) : StudioPage {
    private val root = UiPanel(0)

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val label = UiLabel("Раздел «$title» — в разработке", UiTheme.TEXT_MUTED)
        label.setBounds(x + UiTheme.PAD, y + UiTheme.PAD, w - UiTheme.PAD * 2, 20)
        root.add(label)

        val card = UiPanel(UiTheme.BG_CARD)
        card.setBounds(x + UiTheme.PAD, y + 40, (w - UiTheme.PAD * 2).coerceAtMost(520), 72)
        root.add(card)

        val info = UiLabel(
            "Каркас top-tabs активен. Контент раздела будет доведён до макета.",
            UiTheme.TEXT
        )
        info.setBounds(card.x + 12, card.y + 28, card.w - 24, 16)
        root.add(info)
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean =
        root.mouseClicked(mx, my, button)
}