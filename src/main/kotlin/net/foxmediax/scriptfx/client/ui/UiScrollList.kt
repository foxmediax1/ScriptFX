package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.max

class UiScrollList(
    var items: List<String> = emptyList(),
    var onSelect: ((Int, String) -> Unit)? = null
) : UiWidget() {

    var selected = -1
    private var scroll = 0
    private val rowH = 22

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, UiTheme.BG_CARD)

        val visibleRows = max(1, h / rowH)
        val maxScroll = max(0, items.size - visibleRows)
        scroll = scroll.coerceIn(0, maxScroll)

        var row = 0
        for (i in scroll until items.size) {
            if (row >= visibleRows) break
            val ry = y + row * rowH
            val hover = mouseX in x until right && mouseY in ry until (ry + rowH)
            val bg = when {
                i == selected -> UiTheme.BG_ACTIVE
                hover -> UiTheme.BG_HOVER
                else -> 0
            }
            if (bg != 0) g.fill(x, ry, right, ry + rowH, bg)
            g.text(
                font,
                Component.literal(items[i]),
                x + 8,
                ry + (rowH - font.lineHeight) / 2,
                UiTheme.TEXT,
                false
            )
            row++
        }

        if (items.size > visibleRows) {
            val trackH = h
            val thumbH = max(20, trackH * visibleRows / items.size)
            val thumbY = y + if (maxScroll == 0) 0 else (trackH - thumbH) * scroll / maxScroll
            g.fill(right - 4, y, right - 1, bottom, UiTheme.BORDER)
            g.fill(right - 4, thumbY, right - 1, thumbY + thumbH, UiTheme.ACCENT_DIM)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my) || button != 0) return false
        val visibleRows = max(1, h / rowH)
        val idx = scroll + (my - y) / rowH
        if (idx in items.indices && idx < scroll + visibleRows) {
            selected = idx
            onSelect?.invoke(idx, items[idx])
            return true
        }
        return true
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (!visible || !contains(mx.toInt(), my.toInt())) return false
        if (sy > 0) scroll-- else if (sy < 0) scroll++
        return true
    }
}