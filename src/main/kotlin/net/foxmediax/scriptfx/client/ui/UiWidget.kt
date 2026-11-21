package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

abstract class UiWidget {
    var x = 0
    var y = 0
    var w = 0
    var h = 0
    var visible = true
    var enabled = true

    val right get() = x + w
    val bottom get() = y + h

    fun setBounds(x: Int, y: Int, w: Int, h: Int) {
        this.x = x
        this.y = y
        this.w = w
        this.h = h
    }

    fun contains(mx: Int, my: Int): Boolean =
        visible && mx >= x && mx < right && my >= y && my < bottom

    abstract fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float)

    open fun mouseClicked(mx: Int, my: Int, button: Int): Boolean = false
    open fun mouseReleased(mx: Int, my: Int, button: Int): Boolean = false
    open fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean = false
    open fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean = false
    open fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean = false
    open fun charTyped(codePoint: Int, modifiers: Int): Boolean = false

    protected val font get() = Minecraft.getInstance().font
}