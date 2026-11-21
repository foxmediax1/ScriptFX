package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.client.ui.pages.DocsPage
import net.foxmediax.scriptfx.client.ui.pages.HomePage
import net.foxmediax.scriptfx.client.ui.pages.HudPage
import net.foxmediax.scriptfx.client.ui.pages.NpcPage
import net.foxmediax.scriptfx.client.ui.pages.ParticlesPage
import net.foxmediax.scriptfx.client.ui.pages.ScenariosPage
import net.foxmediax.scriptfx.client.ui.pages.TriggersPage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class StudioScreen : Screen(Component.literal("ScriptFX Studio")) {

    private enum class Mode { HOME, STUDIO }

    private var mode = Mode.HOME

    private val root = UiPanel(0)
    private val sidebar = UiPanel(UiTheme.BG_SIDEBAR)
    private val header = UiPanel(UiTheme.BG_HEADER)
    private val navButtons = mutableListOf<UiButton>()

    /** Порядок как в старой панели + расширения. */
    private val studioSections = listOf(
        "Сценарии",
        "NPC",
        "Частицы",
        "Триггеры",
        "HUD",
        "Документация",
        "Настройки"
    )

    private val host = object : StudioHost {
        override fun openPage(name: String) {
            if (name == "Сценарии" || name in studioSections) {
                enterStudio(name)
            }
        }
    }

    private val homePage = HomePage(host)
    private val scenariosPage = ScenariosPage()
    private val npcPage = NpcPage()
    private val particlesPage = ParticlesPage()
    private val triggersPage = TriggersPage()
    private val hudPage = HudPage()
    private val docsPage = DocsPage()

    private val pages: Map<String, StudioPage> = mapOf(
        "Главная" to homePage,
        "Сценарии" to scenariosPage,
        "NPC" to npcPage,
        "Частицы" to particlesPage,
        "Триггеры" to triggersPage,
        "HUD" to hudPage,
        "Документация" to docsPage
        // "Настройки" → SettingsPage later
    )

    private var sectionIndex = 0
    private var currentPage: StudioPage = homePage
    private var breadcrumb = UiLabel("ScriptFX Studio", UiTheme.TEXT)

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
        if (StudioSession.selectedProject == null && section != "Документация") {
            // без проекта — только док / домой
            mode = Mode.HOME
            currentPage = homePage
            layout()
            return
        }
        mode = Mode.STUDIO
        sectionIndex = studioSections.indexOf(section).coerceAtLeast(0)
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
        sidebar.clearChildren()
        header.clearChildren()
        navButtons.clear()

        root.setBounds(0, 0, width, height)

        if (mode == Mode.HOME) {
            currentPage = homePage
            currentPage.layout(0, 0, width, height)
            return
        }

        // --- STUDIO chrome ---
        sidebar.setBounds(0, 0, UiTheme.SIDEBAR_W, height)
        header.setBounds(UiTheme.SIDEBAR_W, 0, width - UiTheme.SIDEBAR_W, UiTheme.TOPBAR_H)

        // logo block
        val logo = UiLabel("ScriptFX", UiTheme.ACCENT)
        logo.setBounds(UiTheme.PAD, 14, UiTheme.SIDEBAR_W - UiTheme.PAD * 2, 16)
        sidebar.add(logo)
        val sub = UiLabel("Студия", UiTheme.TEXT_MUTED)
        sub.setBounds(UiTheme.PAD, 32, UiTheme.SIDEBAR_W - UiTheme.PAD * 2, 12)
        sidebar.add(sub)

        studioSections.forEachIndexed { i, name ->
            val btn = UiButton(name) {
                sectionIndex = i
                currentPage.onHide()
                currentPage = pages[name] ?: PlaceholderPage(name)
                currentPage.onShow()
                layout()
            }
            btn.active = i == sectionIndex
            btn.setBounds(10, 56 + i * 34, UiTheme.SIDEBAR_W - 20, UiTheme.BTN_H)
            navButtons += btn
            sidebar.add(btn)
        }

        val back = UiButton("← Проекты") { goHome() }
        back.setBounds(10, height - 40, UiTheme.SIDEBAR_W - 20, UiTheme.BTN_H)
        sidebar.add(back)

        val title = studioSections.getOrElse(sectionIndex) { "Студия" }
        val proj = StudioSession.selectedProject?.name ?: "—"
        breadcrumb = UiLabel("Панель управления → $title  ·  $proj", UiTheme.TEXT)
        breadcrumb.setBounds(header.x + UiTheme.PAD, header.y + 10, header.w - 120, 16)
        header.add(breadcrumb)

        val btnClose = UiButton("✕") { onClose() }
        btnClose.setBounds(header.right - 36, header.y + 4, 28, 28)
        header.add(btnClose)

        root.add(sidebar)
        root.add(header)

        val cx = UiTheme.SIDEBAR_W
        val cy = UiTheme.TOPBAR_H
        currentPage.layout(cx, cy, width - cx, height - cy)
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, width, height, UiTheme.BG)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (mode == Mode.STUDIO) {
            g.fill(0, 0, UiTheme.SIDEBAR_W, height, UiTheme.BG_SIDEBAR)
            g.fill(UiTheme.SIDEBAR_W, 0, width, UiTheme.TOPBAR_H, UiTheme.BG_HEADER)
            // divider
            g.fill(UiTheme.SIDEBAR_W, 0, UiTheme.SIDEBAR_W + 1, height, UiTheme.BORDER)
            g.fill(UiTheme.SIDEBAR_W, UiTheme.TOPBAR_H, width, UiTheme.TOPBAR_H + 1, UiTheme.BORDER)
            root.render(g, mouseX, mouseY, partialTick)
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
    private val label = UiLabel("Раздел «$title» — в разработке", UiTheme.TEXT_MUTED)

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        label.setBounds(x + UiTheme.PAD, y + UiTheme.PAD, w - UiTheme.PAD * 2, 20)
        root.add(label)

        val card = UiPanel(UiTheme.BG_CARD)
        card.setBounds(x + UiTheme.PAD, y + 40, w - UiTheme.PAD * 2, 72)
        root.add(card)

        val info = UiLabel(
            "Секция будет перенесена с Modern UI. Каркас Hybrid уже активен.",
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