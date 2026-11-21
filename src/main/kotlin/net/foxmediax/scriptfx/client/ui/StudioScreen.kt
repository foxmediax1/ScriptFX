package net.foxmediax.scriptfx.client.ui

import net.foxmediax.scriptfx.client.ui.pages.HomePage
import net.foxmediax.scriptfx.client.ui.pages.ScenariosPage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import net.foxmediax.scriptfx.client.ui.pages.NpcPage
import net.foxmediax.scriptfx.client.ui.pages.ParticlesPage
import net.foxmediax.scriptfx.client.ui.pages.TriggersPage
import net.foxmediax.scriptfx.client.ui.pages.HudPage
import net.foxmediax.scriptfx.client.ui.pages.DocsPage

class StudioScreen : Screen(Component.literal("ScriptFX Studio")) {

    private val root = UiPanel(UiTheme.BG)
    private val sidebar = UiPanel(UiTheme.BG_SIDEBAR)
    private val topBar = UiPanel(UiTheme.BG_PANEL)
    private val navButtons = mutableListOf<UiButton>()

    private val pageNames = listOf(
        "Главная",
        "Сценарии",
        "NPC",
        "Частицы",
        "Триггеры",
        "HUD",
        "Документация"
    )

    private val host = object : StudioHost {
        override fun openPage(name: String) {
            val idx = pageNames.indexOf(name)
            if (idx >= 0) selectPage(idx)
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
        "Документация" to particlesPage,
    )

    private var currentPageIndex = 0
    private var currentPage: StudioPage = homePage
    private var titleLabel = UiLabel(currentPage.title, UiTheme.TEXT)

    override fun isPauseScreen(): Boolean = true

    override fun init() {
        super.init()
        layout()
    }

    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        layout()
    }

    private fun layout() {
        root.clearChildren()
        sidebar.clearChildren()
        topBar.clearChildren()
        navButtons.clear()

        root.setBounds(0, 0, width, height)
        sidebar.setBounds(0, 0, UiTheme.SIDEBAR_W, height)
        topBar.setBounds(UiTheme.SIDEBAR_W, 0, width - UiTheme.SIDEBAR_W, UiTheme.TOPBAR_H)

        val contentX = UiTheme.SIDEBAR_W
        val contentY = UiTheme.TOPBAR_H
        val contentW = width - UiTheme.SIDEBAR_W
        val contentH = height - UiTheme.TOPBAR_H

        val logo = UiLabel("ScriptFX", UiTheme.ACCENT)
        logo.setBounds(UiTheme.PAD, 12, UiTheme.SIDEBAR_W - UiTheme.PAD * 2, 16)
        sidebar.add(logo)

        val sub = UiLabel("Студия 2.0", UiTheme.TEXT_MUTED)
        sub.setBounds(UiTheme.PAD, 30, UiTheme.SIDEBAR_W - UiTheme.PAD * 2, 12)
        sidebar.add(sub)

        pageNames.forEachIndexed { i, name ->
            val btn = UiButton(name) { selectPage(i) }
            btn.active = i == currentPageIndex
            btn.setBounds(8, 56 + i * 28, UiTheme.SIDEBAR_W - 16, 24)
            navButtons += btn
            sidebar.add(btn)
        }

        titleLabel = UiLabel(currentPage.title, UiTheme.TEXT)
        titleLabel.setBounds(topBar.x + UiTheme.PAD, topBar.y + 10, 280, 16)
        topBar.add(titleLabel)

        val modeHint = UiLabel("Hybrid UI", UiTheme.TEXT_MUTED)
        modeHint.setBounds(topBar.right - 90, topBar.y + 10, 80, 16)
        topBar.add(modeHint)

        root.add(sidebar)
        root.add(topBar)

        currentPage.layout(contentX, contentY, contentW, contentH)
    }

    private fun selectPage(index: Int) {
        if (index !in pageNames.indices) return
        currentPage.onHide()
        currentPageIndex = index
        val name = pageNames[index]
        currentPage = pages[name] ?: PlaceholderPage(name)
        currentPage.onShow()
        layout()
    }

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(0, 0, width, height, UiTheme.BG)
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        g.fill(UiTheme.SIDEBAR_W, UiTheme.TOPBAR_H, width, height, UiTheme.BG)
        root.render(g, mouseX, mouseY, partialTick)
        currentPage.render(g, mouseX, mouseY, partialTick)
        super.extractRenderState(g, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (root.mouseClicked(mx, my, btn)) return true
        if (currentPage.mouseClicked(mx, my, btn)) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (root.mouseReleased(mx, my, btn)) return true
        if (currentPage.mouseReleased(mx, my, btn)) return true
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val btn = event.button()
        if (root.mouseDragged(mx, my, btn, dx, dy)) return true
        if (currentPage.mouseDragged(mx, my, btn, dx, dy)) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(
        mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double
    ): Boolean {
        if (root.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        if (currentPage.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        // Ctrl+S
        if (event.key() == GLFW.GLFW_KEY_S) {
            scenariosPage.save()
            // даже если не на сценариях — безопасно
        }
        if (root.keyPressed(event.key(), 0, 0)) return true
        if (currentPage.keyPressed(event.key(), 0, 0)) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        val cp = event.codepoint()
        if (currentPage.charTyped(cp, 0)) return true
        if (root.charTyped(cp, 0)) return true
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
            "Сюда переедем UI с ModernUI. Сейчас работает каркас Hybrid UI.",
            UiTheme.TEXT
        )
        info.setBounds(card.x + 12, card.y + 28, card.w - 24, 16)
        root.add(info)
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) =
        root.render(g, mouseX, mouseY, pt)

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
}