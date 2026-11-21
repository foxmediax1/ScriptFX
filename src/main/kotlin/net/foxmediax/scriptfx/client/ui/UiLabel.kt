package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class UiLabel(
    var text: String = "",
    var color: Int = UiTheme.TEXT,
    var shadow: Boolean = false
) : UiWidget() {

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible || text.isEmpty()) return
        g.text(font, Component.literal(text), x, y + (h - font.lineHeight).coerceAtLeast(0) / 2, color, shadow)
    }
}