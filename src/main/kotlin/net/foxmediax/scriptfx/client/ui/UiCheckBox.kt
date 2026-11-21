package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class UiCheckBox(
    var label: String = "",
    var checked: Boolean = false,
    var onChange: ((Boolean) -> Unit)? = null
) : UiWidget() {

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        val box = 14
        val by = y + (h - box) / 2
        val hover = contains(mouseX, mouseY) && enabled

        g.fill(x, by, x + box, by + box, if (checked) UiTheme.BG_ACTIVE else UiTheme.BG_CARD)
        val border = when {
            !enabled -> UiTheme.BORDER
            checked || hover -> UiTheme.ACCENT
            else -> UiTheme.BORDER
        }
        g.fill(x, by, x + box, by + 1, border)
        g.fill(x, by + box - 1, x + box, by + box, border)
        g.fill(x, by, x + 1, by + box, border)
        g.fill(x + box - 1, by, x + box, by + box, border)

        if (checked) {
            g.fill(x + 3, by + 3, x + box - 3, by + box - 3, UiTheme.ACCENT)
        }

        if (label.isNotEmpty()) {
            val col = if (enabled) UiTheme.TEXT else UiTheme.TEXT_DIM
            g.text(font, Component.literal(label), x + box + 8, y + (h - font.lineHeight) / 2, col, false)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !enabled || button != 0 || !contains(mx, my)) return false
        checked = !checked
        onChange?.invoke(checked)
        return true
    }
}