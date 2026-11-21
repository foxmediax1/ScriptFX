package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor

open class UiPanel(
    var background: Int = UiTheme.BG_PANEL
) : UiWidget() {

    protected val children = mutableListOf<UiWidget>()

    fun add(child: UiWidget) {
        children += child
    }

    fun clearChildren() {
        children.clear()
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        if (background != 0) g.fill(x, y, right, bottom, background)
        for (c in children) {
            if (c.visible) c.render(g, mouseX, mouseY, pt)
        }
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        if (!visible || !contains(mx, my)) return false
        for (c in children.asReversed()) {
            if (c.mouseClicked(mx, my, button)) return true
        }
        return true
    }

    override fun mouseReleased(mx: Int, my: Int, button: Int): Boolean {
        for (c in children.asReversed()) {
            if (c.mouseReleased(mx, my, button)) return true
        }
        return false
    }

    override fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean {
        for (c in children.asReversed()) {
            if (c.mouseDragged(mx, my, button, dx, dy)) return true
        }
        return false
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        for (c in children.asReversed()) {
            if (c.mouseScrolled(mx, my, sx, sy)) return true
        }
        return false
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        for (c in children) {
            if (c.keyPressed(key, scan, modifiers)) return true
        }
        return false
    }

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean {
        for (c in children) {
            if (c.charTyped(codePoint, modifiers)) return true
        }
        return false
    }
}