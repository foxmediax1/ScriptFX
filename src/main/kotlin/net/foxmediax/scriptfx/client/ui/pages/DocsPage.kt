package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiScrollList
import net.foxmediax.scriptfx.client.ui.UiTextField
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.gui.Documentation
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

class DocsPage : StudioPage {
    override val title = "Документация"

    private data class DocNode(
        val title: String,
        val section: String,
        val subsection: String = ""
    )

    private val tree = listOf(
        DocNode("Переменные", "Скрипты", "Переменные"),
        DocNode("Глобальные переменные", "Скрипты", "Глобальные переменные"),
        DocNode("Для сюжета", "Скрипты", "Для сюжета"),
        DocNode("Для камеры", "Скрипты", "Для камеры"),
        DocNode("Катсцены", "Скрипты", "Катсцены"),
        DocNode("NPC", "Скрипты", "NPC"),
        DocNode("Кастомные модели NPC", "Скрипты", "Кастомные модели NPC"),
        DocNode("Частицы", "Скрипты", "Частицы"),
        DocNode("Звук и видео", "Скрипты", "Звук и видео"),
        DocNode("Квесты", "Скрипты", "Квесты"),
        DocNode("HUD", "Скрипты", "HUD"),
        DocNode("Триггеры", "Триггеры"),
        DocNode("Примеры скриптов", "Примеры скриптов"),
        DocNode("Чат и сообщения", "Чат и сообщения"),
        DocNode("Панель управления", "Панель управления")
    )

    private val root = UiPanel(0)
    private val search = UiTextField(hint = "Поиск…") { q -> applyFilter(q) }
    private val nav = UiScrollList(onSelect = { idx, _ -> openIndex(idx) })
    private val articleTitle = UiLabel("Документация", UiTheme.ACCENT)

    private var filtered: List<DocNode> = tree
    private var selected: DocNode? = tree.firstOrNull()
    private var lines: List<String> = emptyList()
    private var scrollLine = 0

    private var artX = 0
    private var artY = 0
    private var artW = 0
    private var artH = 0

    override fun onShow() {
        applyFilter(search.value)
        selected?.let { loadArticle(it) }
    }

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)

        val pad = UiTheme.PAD
        val leftW = (w * 0.28f).toInt().coerceIn(180, 300)

        val header = UiLabel("Оглавление", UiTheme.ACCENT)
        header.setBounds(x + pad, y + pad, leftW, 16)
        root.add(header)

        search.setBounds(x + pad, y + 32, leftW - pad, 26)
        root.add(search)

        nav.setBounds(x + pad, y + 64, leftW - pad, h - 80)
        root.add(nav)

        artX = x + leftW + pad
        artY = y + 36
        artW = w - leftW - pad * 2
        artH = h - 56

        articleTitle.setBounds(artX, y + pad, artW, 16)
        root.add(articleTitle)

        val frame = UiPanel(UiTheme.BG_CARD)
        frame.setBounds(artX, artY, artW, artH)
        root.add(frame)

        applyFilter(search.value)
        selected?.let { loadArticle(it) }
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        filtered = if (q.isEmpty()) tree
        else tree.filter {
            it.title.lowercase().contains(q) ||
                    it.section.lowercase().contains(q) ||
                    it.subsection.lowercase().contains(q)
        }
        nav.items = filtered.map { it.title }
        val sel = selected
        nav.selected = if (sel == null) -1
        else filtered.indexOfFirst { it.title == sel.title && it.section == sel.section }
    }

    private fun openIndex(idx: Int) {
        if (idx !in filtered.indices) return
        selected = filtered[idx]
        loadArticle(selected!!)
    }

    private fun loadArticle(node: DocNode) {
        articleTitle.text = node.title
        lines = Documentation.page(node.section, node.subsection)
            .ifEmpty { listOf("Нет текста для «${node.title}».") }
        scrollLine = 0
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)

        val font = Minecraft.getInstance().font
        val lineH = font.lineHeight + 3
        val visible = max(1, (artH - 16) / lineH)
        scrollLine = scrollLine.coerceIn(0, max(0, lines.size - visible))

        var drawY = artY + 8
        val end = min(lines.size, scrollLine + visible)
        for (i in scrollLine until end) {
            val raw = lines[i]
            when {
                raw.trim() == Documentation.DIVIDER || raw.trim() == "---" -> {
                    g.fill(artX + 10, drawY + lineH / 2, artX + artW - 10, drawY + lineH / 2 + 1, UiTheme.BORDER)
                }
                raw.isBlank() -> { /* skip gap */ }
                else -> {
                    val color = when {
                        raw.startsWith("Раздел:") -> UiTheme.ACCENT
                        else -> UiTheme.TEXT
                    }
                    // простая обрезка длинных строк
                    var text = raw
                    while (font.width(text) > artW - 24 && text.length > 4) {
                        text = text.dropLast(1)
                    }
                    if (text != raw && text.length > 3) text = text.dropLast(1) + "…"
                    g.text(font, Component.literal(text), artX + 12, drawY, color, false)
                }
            }
            drawY += lineH
        }

        if (lines.size > visible) {
            val trackH = artH - 8
            val thumbH = max(20, trackH * visible / lines.size)
            val maxScroll = max(1, lines.size - visible)
            val thumbY = artY + 4 + (trackH - thumbH) * scrollLine / maxScroll
            g.fill(artX + artW - 5, artY + 4, artX + artW - 2, artY + artH - 4, UiTheme.BORDER)
            g.fill(artX + artW - 5, thumbY, artX + artW - 2, thumbY + thumbH, UiTheme.ACCENT_DIM)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean =
        root.mouseClicked(mx, my, button)

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (mx.toInt() in artX until (artX + artW) && my.toInt() in artY until (artY + artH)) {
            if (sy > 0) scrollLine-- else if (sy < 0) scrollLine++
            return true
        }
        return root.mouseScrolled(mx, my, sx, sy)
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)
    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double) =
        root.mouseDragged(mx, my, button, dx, dy)
    override fun keyPressed(key: Int, scan: Int, modifiers: Int) = root.keyPressed(key, scan, modifiers)
    override fun charTyped(codePoint: Int, modifiers: Int) = root.charTyped(codePoint, modifiers)
}