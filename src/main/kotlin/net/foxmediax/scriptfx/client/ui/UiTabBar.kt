package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Горизонтальные вкладки верхнего chrome (макет ScriptFX Studio).
 */
class UiTabBar(
    var tabs: List<String> = emptyList(),
    var selected: Int = 0,
    var onSelect: ((Int, String) -> Unit)? = null
) : UiWidget() {

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible || tabs.isEmpty()) return
        val tabW = (w / tabs.size).coerceAtLeast(64)
        tabs.forEachIndexed { i, name ->
            val tx = x + i * tabW
            val active = i == selected
            val hover = mouseX in tx until (tx + tabW) && mouseY in y until bottom

            if (active) {
                g.fill(tx + 2, y, tx + tabW - 2, bottom, UiTheme.BG_ACTIVE)
                g.fill(tx + 4, bottom - 2, tx + tabW - 4, bottom, UiTheme.ACCENT)
            } else if (hover) {
                g.fill(tx + 2, y, tx + tabW - 2, bottom, UiTheme.BG_HOVER)
            }

            val tw = font.width(name)
            val col = when {
                active -> UiTheme.ACCENT_SOFT
                hover -> UiTheme.TEXT
                else -> UiTheme.TEXT_MUTED
            }
            g.text(
                font,
                Component.literal(name),
                tx + (tabW - tw) / 2,
                y + (h - font.lineHeight) / 2,
                col,
                false
            )
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my) || button != 0 || tabs.isEmpty()) return false
        val tabW = (w / tabs.size).coerceAtLeast(64)
        val i = ((mx - x) / tabW).coerceIn(0, tabs.lastIndex)
        selected = i
        onSelect?.invoke(i, tabs[i])
        return true
    }
}