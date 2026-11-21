package net.foxmediax.scriptfx.client.ui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class UiTextArea(
    var value: String = "",
    var onChange: ((String) -> Unit)? = null
) : UiWidget() {

    var focused = false
    private var cursor = 0
    private var scrollLine = 0
    private var blink = 0

    private val lineH get() = font.lineHeight + 2

    fun setText(text: String) {
        value = text
        cursor = text.length
        scrollLine = 0
        onChange?.invoke(value)
    }

    override fun render(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, pt: Float) {
        if (!visible) return
        g.fill(x, y, right, bottom, UiTheme.BG_CARD)
        val border = if (focused) UiTheme.ACCENT_DIM else UiTheme.BORDER
        g.fill(x, y, right, y + 1, border)
        g.fill(x, bottom - 1, right, bottom, border)
        g.fill(x, y, x + 1, bottom, border)
        g.fill(right - 1, y, right, bottom, border)

        val lines = if (value.isEmpty()) listOf("") else value.split('\n')
        val visibleLines = max(1, (h - 8) / lineH)
        scrollLine = scrollLine.coerceIn(0, max(0, lines.size - visibleLines))

        var drawY = y + 4
        for (i in scrollLine until min(lines.size, scrollLine + visibleLines)) {
            g.text(font, Component.literal(lines[i]), x + 6, drawY, UiTheme.TEXT, false)
            drawY += lineH
        }

        if (focused && (blink / 10) % 2 == 0) {
            val (cx, cy) = cursorToXY(lines)
            if (cy in 0 until visibleLines) {
                val px = x + 6 + cx
                val py = y + 4 + cy * lineH
                g.fill(px, py, px + 1, py + font.lineHeight, UiTheme.ACCENT)
            }
        }
        blink++
    }

    private fun cursorToXY(lines: List<String>): Pair<Int, Int> {
        var left = cursor
        for (i in lines.indices) {
            val len = lines[i].length
            if (left <= len) {
                return font.width(lines[i].take(left)) to (i - scrollLine)
            }
            left -= len + 1
        }
        val last = lines.lastOrNull().orEmpty()
        return font.width(last) to (lines.lastIndex - scrollLine).coerceAtLeast(0)
    }

    override fun mouseClicked(mx: Int, my: Int, button: Int): Boolean {
        focused = visible && enabled && contains(mx, my)
        return focused
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        if (!visible || !contains(mx.toInt(), my.toInt())) return false
        if (sy > 0) scrollLine-- else if (sy < 0) scrollLine++
        return true
    }

    override fun keyPressed(key: Int, scan: Int, modifiers: Int): Boolean {
        if (!focused || !enabled) return false
        when (key) {
            GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
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
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                value = value.substring(0, cursor) + "\n" + value.substring(cursor)
                cursor++
                onChange?.invoke(value)
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
                cursor = lineStart(cursor)
                return true
            }
            GLFW.GLFW_KEY_END -> {
                cursor = lineEnd(cursor)
                return true
            }
            GLFW.GLFW_KEY_UP -> {
                moveVert(-1)
                return true
            }
            GLFW.GLFW_KEY_DOWN -> {
                moveVert(1)
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

    private fun lineStart(pos: Int): Int {
        if (pos <= 0) return 0
        val i = value.lastIndexOf('\n', pos - 1)
        return if (i < 0) 0 else i + 1
    }

    private fun lineEnd(pos: Int): Int {
        val i = value.indexOf('\n', pos)
        return if (i < 0) value.length else i
    }

    private fun moveVert(dir: Int) {
        val start = lineStart(cursor)
        val col = cursor - start
        if (dir < 0) {
            if (start == 0) return
            val prevEnd = start - 1
            val prevStart = lineStart(prevEnd)
            cursor = (prevStart + col).coerceAtMost(prevEnd)
        } else {
            val end = lineEnd(cursor)
            if (end >= value.length) return
            val nextStart = end + 1
            val nextEnd = lineEnd(nextStart)
            cursor = (nextStart + col).coerceAtMost(nextEnd)
        }
    }
}