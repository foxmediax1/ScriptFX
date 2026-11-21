package net.foxmediax.scriptfx.client.ui.pages

import net.foxmediax.scriptfx.client.ui.StudioPage
import net.foxmediax.scriptfx.client.ui.UiButton
import net.foxmediax.scriptfx.client.ui.UiLabel
import net.foxmediax.scriptfx.client.ui.UiPanel
import net.foxmediax.scriptfx.client.ui.UiTheme
import net.foxmediax.scriptfx.scriptengine.ScriptFXLog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class LogsPage : StudioPage {
    override val title = "Логи"

    private enum class Filter { ALL, INFO, WARN, ERROR }

    private val root = UiPanel(0)
    private var filter = Filter.ALL
    private var lines: List<String> = emptyList()
    private var scroll = 0

    private var listX = 0
    private var listY = 0
    private var listW = 0
    private var listH = 0

    override fun onShow() = refresh()

    override fun layout(x: Int, y: Int, w: Int, h: Int) {
        root.clearChildren()
        root.setBounds(x, y, w, h)
        val pad = UiTheme.PAD

        root.add(UiLabel("Логи ScriptFX", UiTheme.ACCENT).also {
            it.setBounds(x + pad, y + pad, 200, 16)
        })

        var bx = x + pad
        val by = y + 36
        for (f in Filter.entries) {
            val b = UiButton(f.name) {
                filter = f
                scroll = 0
                refresh()
                layout(x, y, w, h)
            }
            b.active = filter == f
            b.setBounds(bx, by, 64, 24)
            root.add(b)
            bx += 70
        }

        val btnRefresh = UiButton("Обновить") {
            refresh()
            layout(x, y, w, h)
        }
        btnRefresh.style = UiButton.Style.PRIMARY
        btnRefresh.setBounds(bx + 8, by, 80, 24)
        root.add(btnRefresh)

        val btnClear = UiButton("Очистить") {
            ScriptFXLog.clearLogs()
            refresh()
            layout(x, y, w, h)
        }
        btnClear.style = UiButton.Style.DANGER
        btnClear.setBounds(bx + 94, by, 80, 24)
        root.add(btnClear)

        listX = x + pad
        listY = by + 36
        listW = w - pad * 2
        listH = h - (listY - y) - pad
    }

    private fun refresh() {
        val all = ScriptFXLog.getRecentLogs().asReversed()
        lines = all.filter { line ->
            when (filter) {
                Filter.ALL -> true
                Filter.INFO -> line.contains("[INFO]")
                Filter.WARN -> line.contains("[WARN]")
                Filter.ERROR -> line.contains("[ERROR]")
            }
        }
        scroll = scroll.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        root.render(g, mouseX, mouseY, pt)
        g.fill(listX, listY, listX + listW, listY + listH, UiTheme.BG_CARD)
        g.fill(listX, listY, listX + listW, listY + 1, UiTheme.BORDER)
        g.fill(listX, listY + listH - 1, listX + listW, listY + listH, UiTheme.BORDER)

        val font = Minecraft.getInstance().font
        val lineH = font.lineHeight + 2
        val visible = ((listH - 8) / lineH).coerceAtLeast(1)
        if (lines.isEmpty()) {
            g.text(font, Component.literal("Нет записей"), listX + 8, listY + 8, UiTheme.TEXT_MUTED, false)
            return
        }
        val from = scroll.coerceIn(0, lines.lastIndex)
        lines.drop(from).take(visible).forEachIndexed { i, line ->
            val col = when {
                line.contains("[ERROR]") -> UiTheme.DANGER
                line.contains("[WARN]") -> UiTheme.WARNING
                line.contains("[INFO]") -> UiTheme.SUCCESS
                else -> UiTheme.TEXT
            }
            var shown = line
            while (shown.length > 4 && font.width(shown) > listW - 16) shown = shown.dropLast(1)
            g.text(font, Component.literal(shown), listX + 8, listY + 6 + i * lineH, col, false)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int) = root.mouseClicked(mx, my, button)
    override fun mouseReleased(mx: Int, my: Int, button: Int) = root.mouseReleased(mx, my, button)

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (mx.toInt() in listX until listX + listW && my.toInt() in listY until listY + listH) {
            scroll = (scroll - sy.toInt()).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
            return true
        }
        return root.mouseScrolled(mx, my, sx, sy)
    }
}