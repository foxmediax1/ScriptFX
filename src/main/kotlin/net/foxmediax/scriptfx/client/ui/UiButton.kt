package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class UiButton(
    var text: String = "",
    var onClick: (() -> Unit)? = null
) : UiWidget() {

    var active = false

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        val hover = contains(mouseX, mouseY) && enabled
        val bg = when {
            !enabled -> 0xFF15151C.toInt()
            active -> UiTheme.BG_ACTIVE
            hover -> UiTheme.BG_HOVER
            else -> UiTheme.BG_CARD
        }
        g.fill(x, y, right, bottom, bg)
        val border = when {
            !enabled -> UiTheme.BORDER
            active || hover -> UiTheme.ACCENT_DIM
            else -> UiTheme.BORDER
        }
        g.fill(x, y, right, y + 1, border)
        g.fill(x, bottom - 1, right, bottom, border)
        g.fill(x, y, x + 1, bottom, border)
        g.fill(right - 1, y, right, bottom, border)

        val tw = font.width(text)
        val tx = x + (w - tw) / 2
        val ty = y + (h - font.lineHeight) / 2
        val col = if (enabled) UiTheme.TEXT else UiTheme.TEXT_MUTED
        g.text(font, Component.literal(text), tx, ty, col, false)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false
        onClick?.invoke()
        return true
    }
}