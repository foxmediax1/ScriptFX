package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class UiTextField(
    var value: String = "",
    var hint: String = "",
    var onChange: ((String) -> Unit)? = null
) : UiWidget() {

    var focused = false
    private var cursor = value.length
    private var blink = 0

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        val bg = if (focused) UiTheme.BG_ACTIVE else UiTheme.BG_CARD
        g.fill(x, y, right, bottom, bg)
        val border = if (focused) UiTheme.ACCENT else UiTheme.BORDER
        g.fill(x, y, right, y + 1, border)
        g.fill(x, bottom - 1, right, bottom, border)
        g.fill(x, y, x + 1, bottom, border)
        g.fill(right - 1, y, right, bottom, border)

        val textY = y + (h - font.lineHeight) / 2
        if (value.isEmpty() && !focused) {
            g.text(font, Component.literal(hint), x + 6, textY, UiTheme.TEXT_MUTED, false)
        } else {
            g.text(font, Component.literal(value), x + 6, textY, UiTheme.TEXT, false)
            if (focused && (blink / 10) % 2 == 0) {
                val cx = x + 6 + font.width(value.take(cursor.coerceIn(0, value.length)))
                g.fill(cx, textY, cx + 1, textY + font.lineHeight, UiTheme.ACCENT)
            }
        }
        blink++
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        focused = visible && enabled && contains(mx, my)
        if (focused) cursor = value.length
        return focused
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        if (!focused || !enabled) return false
        when (key) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0 && value.isNotEmpty()) {
                    value = value.removeRange(cursor - 1, cursor)
                    cursor--
                    onChange?.invoke(value)
                }
                return true
            }
            GLFW.GLFW_KEY_DELETE -> {
                if (cursor < value.length) {
                    value = value.removeRange(cursor, cursor + 1)
                    onChange?.invoke(value)
                }
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                cursor = (cursor - 1).coerceAtLeast(0)
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                cursor = (cursor + 1).coerceAtMost(value.length)
                return true
            }
            GLFW.GLFW_KEY_HOME -> {
                cursor = 0
                return true
            }
            GLFW.GLFW_KEY_END -> {
                cursor = value.length
                return true
            }
        }
        return false
    }

    override fun charTyped(codePoint: Int, modifiers: Int): Boolean {
        if (!focused || !enabled) return false
        if (codePoint < 32) return false
        val ch = codePoint.toChar()
        value = value.substring(0, cursor) + ch + value.substring(cursor)
        cursor++
        onChange?.invoke(value)
        return true
    }
}