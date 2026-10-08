package net.foxmediax.scriptfx.client

import net.foxmediax.scriptfx.config.ScriptFXConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.roundToInt

class NpcDialogScreen(
    private val npcName: String,
    fullText: String,
    private val buttons: List<String>,
    private val onChoose: (Int) -> Unit
) : Screen(Component.empty()) {

    private val shownAt = System.currentTimeMillis()
    private var picked = false

    // "Страж: кто идёт?" -> "кто идёт?" (имя уже показано в плашке)
    private val bodyText: String = fullText.trim().let { t ->
        val prefix = "$npcName:"
        if (npcName.isNotBlank() && t.startsWith(prefix, ignoreCase = true)) t.substring(prefix.length).trim() else t
    }

    // --- прокрутка текстового окна ---
    private var scrollLine = 0
    private var userScrolled = false
    private var draggingBar = false

    private var cachedWidth = -1
    private var cachedLines: List<String> = emptyList()

    override fun shouldCloseOnEsc(): Boolean = false
    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {}

    private class BtnRect(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val lines: List<String>)
    private class Layout(
        val headerY: Int, val headerH: Int,
        val boxX: Int, val boxY: Int, val boxW: Int, val boxH: Int,
        val textW: Int, val btn: List<BtnRect>
    )

    private companion object {
        const val SCROLLBAR_W = 3
        const val SCROLLBAR_RESERVE = 6
        const val PAD = 8

        const val COL_HEADER = 0x1E2A44
        const val COL_ACCENT = 0x5A7FC0
        const val COL_BODY = 0x101018
        const val COL_BORDER = 0x3A3F5C
        const val COL_NAME = 0xFFD866
    }

    private fun layout(): Layout {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val w = mc.window.guiScaledWidth
        val h = mc.window.guiScaledHeight

        val boxW = (w * 0.26).toInt().coerceIn(130, 200)
        val boxH = (h * 0.28).toInt().coerceIn(80, 160)
        val textW = boxW - PAD * 2 - SCROLLBAR_RESERVE

        // плашка с именем + окно с текстом, вместе по центру экрана по вертикали
        val headerH = font.lineHeight + 9
        val top = h / 2 - (headerH + boxH) / 2

        val btnW = (w * 0.20).toInt().coerceIn(100, 160)
        val padX = 6
        val padY = 5
        val gap = 6
        val lh = font.lineHeight
        val labelW = btnW - padX * 2

        val prepared = buttons.mapIndexed { i, label ->
            val lines = wrap(font, "${i + 1}. $label", labelW)
            lines to max(18, lines.size * (lh + 1) - 1 + padY * 2)
        }
        val total = prepared.sumOf { it.second } + gap * (prepared.size - 1).coerceAtLeast(0)
        val bx = w - btnW - 24
        var by = (h / 2 - total / 2).coerceAtLeast(8)

        val rects = prepared.map { (lines, hgt) ->
            val r = BtnRect(bx, by, bx + btnW, by + hgt, lines)
            by += hgt + gap
            r
        }
        return Layout(top, headerH, 24, top + headerH, boxW, boxH, textW, rects)
    }

    private fun fade(): Float {
        val ms = ScriptFXConfig.dialogFadeMs.coerceAtLeast(1).toFloat()
        val t = ((System.currentTimeMillis() - shownAt) / ms).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    // ---------------- текст и скролл ----------------

    private fun wrappedFull(font: Font, width: Int): List<String> {
        if (width != cachedWidth) {
            cachedLines = wrap(font, bodyText, width)
            cachedWidth = width
        }
        return cachedLines
    }

    private fun revealed(lines: List<String>): List<String> {
        val perChar = ScriptFXConfig.dialogTypewriterMs.coerceAtLeast(1)
        val total = lines.sumOf { it.length }
        var left = ((System.currentTimeMillis() - shownAt) / perChar).toInt().coerceIn(0, total)
        val out = ArrayList<String>()
        for (line in lines) {
            if (left <= 0) break
            if (left >= line.length) {
                out.add(line); left -= line.length
            } else {
                out.add(line.take(left)); left = 0
            }
        }
        return out
    }

    private fun visibleLines(l: Layout): Int =
        ((l.boxH - PAD * 2) / (Minecraft.getInstance().font.lineHeight + 2)).coerceAtLeast(1)

    private fun maxScroll(l: Layout): Int {
        val lines = wrappedFull(Minecraft.getInstance().font, l.textW)
        return (lines.size - visibleLines(l)).coerceAtLeast(0)
    }

    private fun scrollBy(l: Layout, delta: Int) {
        scrollLine = (scrollLine + delta).coerceIn(0, maxScroll(l))
        userScrolled = true
    }

    private fun onScrollbar(l: Layout, mx: Double, my: Double): Boolean {
        if (maxScroll(l) <= 0) return false
        val x = l.boxX + l.boxW - PAD / 2 - SCROLLBAR_W
        return mx >= x - 2 && mx <= x + SCROLLBAR_W + 2 && my >= l.boxY && my <= l.boxY + l.boxH
    }

    private fun dragTo(l: Layout, mouseY: Double) {
        val ms = maxScroll(l)
        if (ms <= 0) return
        val total = wrappedFull(Minecraft.getInstance().font, l.textW).size
        val trackY = l.boxY + 4
        val trackH = l.boxH - 8
        val thumbH = max(12, trackH * visibleLines(l) / total)
        val ratio = ((mouseY - trackY - thumbH / 2.0) / (trackH - thumbH)).coerceIn(0.0, 1.0)
        scrollLine = (ratio * ms).roundToInt()
        userScrolled = true
    }

    // ---------------- рендер ----------------

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = Minecraft.getInstance()
        val font = mc.font
        val a = (fade() * 230).toInt().coerceIn(0, 255)
        if (a < 8) return

        val l = layout()
        val ca = a shl 24

        // --- плашка с именем NPC ---
        g.fill(l.boxX, l.headerY, l.boxX + l.boxW, l.headerY + l.headerH, ca or COL_HEADER)
        g.fill(l.boxX, l.headerY + l.headerH - 1, l.boxX + l.boxW, l.headerY + l.headerH, ca or COL_ACCENT)
        g.fill(l.boxX, l.headerY, l.boxX + 2, l.headerY + l.headerH, ca or COL_ACCENT)
        g.text(font, fit(font, npcName, l.boxW - PAD * 2 - 2), l.boxX + PAD, l.headerY + 5, ca or COL_NAME, true)

        // --- окно текста: фон + тонкая рамка ---
        g.fill(l.boxX, l.boxY, l.boxX + l.boxW, l.boxY + l.boxH, ca or COL_BODY)
        g.fill(l.boxX, l.boxY + l.boxH - 1, l.boxX + l.boxW, l.boxY + l.boxH, ca or COL_BORDER)
        g.fill(l.boxX, l.boxY, l.boxX + 1, l.boxY + l.boxH, ca or COL_BORDER)
        g.fill(l.boxX + l.boxW - 1, l.boxY, l.boxX + l.boxW, l.boxY + l.boxH, ca or COL_BORDER)

        val lines = wrappedFull(font, l.textW)
        val shown = revealed(lines)
        val visible = visibleLines(l)
        val ms = (lines.size - visible).coerceAtLeast(0)

        if (!userScrolled) scrollLine = (shown.size - visible).coerceIn(0, ms)
        scrollLine = scrollLine.coerceIn(0, ms)

        for (i in 0 until visible) {
            val idx = scrollLine + i
            if (idx >= shown.size) break
            g.text(font, shown[idx], l.boxX + PAD, l.boxY + PAD + i * (font.lineHeight + 2), ca or 0xFFFFFF, false)
        }

        if (ms > 0) {
            val trackX = l.boxX + l.boxW - PAD / 2 - SCROLLBAR_W
            val trackY = l.boxY + 4
            val trackH = l.boxH - 8
            val thumbH = max(12, trackH * visible / lines.size)
            val thumbY = trackY + ((trackH - thumbH) * (scrollLine.toFloat() / ms)).toInt()
            g.fill(trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, ca or 0x30303C)
            g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH,
                ca or (if (draggingBar) 0xFFFFFF else 0xA0A0B0))
        }

        // --- кнопки ---
        l.btn.forEach { r ->
            val hovered = mouseX in r.x1 until r.x2 && mouseY in r.y1 until r.y2
            g.fill(r.x1, r.y1, r.x2, r.y2, ca or (if (hovered) 0x304060 else 0x181828))
            r.lines.forEachIndexed { li, line ->
                g.text(font, line, r.x1 + 6, r.y1 + 5 + li * (font.lineHeight + 1), ca or 0xFFFFFF, false)
            }
        }
    }

    // ---------------- ввод ----------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val l = layout()
            if (onScrollbar(l, event.x(), event.y())) {
                draggingBar = true
                dragTo(l, event.y())
                return true
            }
            if (fade() > 0.8f) {
                l.btn.forEachIndexed { i, r ->
                    if (event.x() >= r.x1 && event.x() < r.x2 && event.y() >= r.y1 && event.y() < r.y2) {
                        pick(i + 1)
                        return true
                    }
                }
            }
        }
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingBar) {
            dragTo(layout(), event.y())
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        draggingBar = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val l = layout()
        val inBox = mouseX >= l.boxX && mouseX <= l.boxX + l.boxW && mouseY >= l.boxY && mouseY <= l.boxY + l.boxH
        if (inBox && scrollY != 0.0) {
            scrollBy(l, if (scrollY > 0) -2 else 2)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val n = event.key() - GLFW.GLFW_KEY_1
        if (n in buttons.indices && fade() > 0.8f) {
            pick(n + 1)
            return true
        }
        return super.keyPressed(event)
    }

    private fun pick(button: Int) {
        if (picked) return
        picked = true
        onChoose(button)
    }

    // ---------------- вспомогательное ----------------

    /** Обрезает строку с "…", если она шире maxWidth. */
    private fun fit(font: Font, text: String, maxWidth: Int): String {
        if (font.width(text) <= maxWidth) return text
        var s = text
        while (s.isNotEmpty() && font.width("$s…") > maxWidth) s = s.dropLast(1)
        return "$s…"
    }

    private fun wrap(font: Font, text: String, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            var cur = ""
            for (word in paragraph.split(" ")) {
                var rest = word
                while (font.width(rest) > maxWidth) {
                    if (cur.isNotEmpty()) { result.add(cur); cur = "" }
                    var cut = rest.length
                    while (cut > 1 && font.width(rest.substring(0, cut)) > maxWidth) cut--
                    result.add(rest.substring(0, cut))
                    rest = rest.substring(cut)
                }
                val cand = if (cur.isEmpty()) rest else "$cur $rest"
                if (font.width(cand) > maxWidth && cur.isNotEmpty()) {
                    result.add(cur); cur = rest
                } else cur = cand
            }
            result.add(cur)
        }
        return result
    }
}