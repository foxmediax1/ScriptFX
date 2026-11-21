package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor

interface StudioPage {
    val title: String
    fun layout(x: Int, y: Int, w: Int, h: Int)
    fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float)
    fun mouseClicked(mx: Int, my: Int, button: Int): Boolean = false
    fun mouseReleased(mx: Int, my: Int, button: Int): Boolean = false
    fun mouseDragged(mx: Int, my: Int, button: Int, dx: Double, dy: Double): Boolean = false
    fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean = false
    fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean = false
    fun charTyped(codePoint: Int, modifiers: Int): Boolean = false
    fun onShow() {}
    fun onHide() {}
}